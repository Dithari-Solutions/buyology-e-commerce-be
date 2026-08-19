package com.buyology.ecommerce.infrastructure.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final LettuceConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    // Whether X-Forwarded-For can be trusted (only when behind a trusted reverse proxy).
    // Defaults to false so a direct client cannot spoof its IP to mint fresh buckets.
    private final boolean trustForwardedHeaders;

    // Shared secret that lets trusted server-to-server clients (e.g. the n8n
    // product-integration workflow) bypass throttling. Blank = bypass disabled.
    private final String rateLimitBypassKey;

    // Lazily initialized; retries every 30 s if Redis is unavailable
    private volatile LettuceBasedProxyManager<String> proxyManager;
    private volatile long lastAttemptMs = 0;
    private static final long RETRY_INTERVAL_MS = 30_000;

    // Local in-memory fallback used for AUTH tiers when Redis is unavailable, so
    // brute-force protection survives a Redis outage (fail-closed-ish, not fail-open).
    // Per-instance only — acceptable degraded mode until Redis recovers.
    private final ConcurrentHashMap<String, Bucket> localBuckets = new ConcurrentHashMap<>();

    /**
     * Per-minute ceiling for the ADMIN tier, overridable without a code deploy. The dashboard's real
     * appetite is only knowable in production — this is the lever to turn when admins report 429s,
     * rather than waiting on a release. 0 or less means "use the tier's built-in default".
     */
    private final int adminPerMinute;

    public RateLimitingFilter(LettuceConnectionFactory connectionFactory,
                              ObjectMapper objectMapper,
                              @Value("${app.trust-forwarded-headers:false}") boolean trustForwardedHeaders,
                              @Value("${app.rate-limit.bypass-key:}") String rateLimitBypassKey,
                              @Value("${app.rate-limit.admin-per-minute:0}") int adminPerMinute) {
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.rateLimitBypassKey = rateLimitBypassKey;
        this.adminPerMinute = adminPerMinute;
    }

    /**
     * The bucket shape for a tier, applying the ADMIN override when one is configured. Everything
     * else uses the tier's own constant, so a bad value here can only affect admin traffic.
     */
    private BucketConfiguration configurationFor(RateLimitTier tier) {
        if (tier == RateLimitTier.ADMIN && adminPerMinute > 0) {
            return BucketConfiguration.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(adminPerMinute)
                            .refillGreedy(adminPerMinute, Duration.ofMinutes(1))
                            .build())
                    .build();
        }
        return tier.buildConfiguration();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isExcluded(path) || isTrustedService(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        RateLimitTier tier = determineTier(path);
        BucketConfiguration configuration = configurationFor(tier);
        // The capacity is part of the key on purpose. Bucket4j stores a bucket's configuration
        // alongside its state and only calls the supplier when the key does not exist yet, so an
        // already-created bucket would keep its old capacity forever — and these keys have a TTL
        // measured in minutes, not the lifetime of the deployment. Without this, changing
        // app.rate-limit.admin-per-minute would look applied and do nothing.
        String bucketKey = "rl:" + tier.name() + ":" + capacityOf(configuration) + ":"
                + resolveBucketSubject(tier, clientIp);

        LettuceBasedProxyManager<String> pm = acquireProxyManager();
        if (pm == null) {
            // Redis is unavailable.
            // For auth-sensitive endpoints we must NOT fail open (that would disable
            // brute-force protection exactly when it matters most). Fall back to a
            // per-instance in-memory bucket so the limiter keeps working.
            if (tier.needsLocalFallback()) {
                applyLocalFallback(bucketKey, configuration, request, response, filterChain, path);
            } else {
                // Non-sensitive tiers: keep failing open to preserve availability.
                filterChain.doFilter(request, response);
            }
            return;
        }

        try {
            var bucket = pm.builder().build(bucketKey, () -> configuration);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            handleProbe(probe, request, response, filterChain, path);
        } catch (Exception e) {
            log.warn("Rate limiting error on {} (Redis path): {}", path, e.getMessage());
            // Redis errored mid-request. Protect auth-sensitive endpoints with the
            // local fallback instead of waving the request through.
            if (tier.needsLocalFallback()) {
                applyLocalFallback(bucketKey, configuration, request, response, filterChain, path);
            } else {
                filterChain.doFilter(request, response);
            }
        }
    }

    /**
     * Throttles using a per-instance in-memory bucket. Used for auth tiers when Redis
     * is unavailable so brute-force protection does not silently disappear.
     */
    private void applyLocalFallback(String bucketKey, BucketConfiguration configuration,
                                    HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain, String path)
            throws ServletException, IOException {
        Bucket localBucket = localBuckets.computeIfAbsent(bucketKey,
                k -> Bucket.builder().addLimit(configuration.getBandwidths()[0]).build());
        ConsumptionProbe probe = localBucket.tryConsumeAndReturnRemaining(1);
        handleProbe(probe, request, response, filterChain, path);
    }

    /**
     * Applies the outcome of a bucket consumption probe: forwards the request when a
     * token was consumed, otherwise writes a 429 with Retry-After.
     */
    private void handleProbe(ConsumptionProbe probe, HttpServletRequest request,
                             HttpServletResponse response, FilterChain filterChain, String path)
            throws ServletException, IOException {
        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setHeader("X-Rate-Limit-Remaining", "0");
            objectMapper.writeValue(response.getOutputStream(), Map.of(
                    "success", false,
                    "message", "Too many requests. Please slow down.",
                    "retryAfterSeconds", retryAfterSeconds
            ));
        }
    }

    /**
     * Lazily creates the Bucket4j proxy manager backed by Redis.
     * Returns null if Redis is unreachable (fail-open behaviour).
     * Retries every 30 s so the limiter activates once Redis comes up.
     */
    private LettuceBasedProxyManager<String> acquireProxyManager() {
        if (proxyManager != null) return proxyManager;

        long now = System.currentTimeMillis();
        if (now - lastAttemptMs < RETRY_INTERVAL_MS) return null;

        synchronized (this) {
            if (proxyManager != null) return proxyManager;
            if (System.currentTimeMillis() - lastAttemptMs < RETRY_INTERVAL_MS) return null;
            lastAttemptMs = System.currentTimeMillis();
            try {
                RedisClient redisClient = (RedisClient) connectionFactory.getNativeClient();
                var connection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                // Without an expiration strategy every bucket key is written with no TTL, so Redis
                // accumulated one permanent key per client IP for as long as the deployment lived.
                // basedOnTimeForRefillingBucketUpToMax only expires a bucket once it would have
                // refilled to full, at which point its state is indistinguishable from a fresh one —
                // so nothing is forgotten that would have still been throttling.
                proxyManager = Bucket4jLettuce.<String>casBasedBuilder(connection)
                        .expirationAfterWrite(ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(5)))
                        .build();
                log.info("Rate limiter connected to Redis");
            } catch (Exception e) {
                log.warn("Redis unavailable — rate limiting disabled, retrying in {}s: {}",
                        RETRY_INTERVAL_MS / 1000, e.getMessage());
            }
        }
        return proxyManager;
    }

    /**
     * Trusted server-to-server clients (e.g. the n8n product-integration workflow)
     * bypass throttling by presenting the shared service key in the X-Service-Key
     * header. Compared in constant time; a blank/unset key disables the bypass.
     * NOTE: this only skips THROTTLING — the endpoint's JWT auth still applies, so
     * the caller must still be an authenticated admin/supplier.
     */
    private boolean isTrustedService(HttpServletRequest request) {
        if (rateLimitBypassKey == null || rateLimitBypassKey.isBlank()) return false;
        String provided = request.getHeader("X-Service-Key");
        if (provided == null) return false;
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                rateLimitBypassKey.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isExcluded(String path) {
        return path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/webjars")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/actuator/");
    }

    /**
     * Who the bucket belongs to.
     *
     * <p>Admin traffic is keyed on the authenticated admin rather than on the client IP. Every admin
     * reaches the API through the same nginx load balancer, and with
     * {@code app.trust-forwarded-headers=false} (the default) {@link HttpServletRequest#getRemoteAddr()}
     * is the proxy's address — so a single IP-keyed bucket was shared by every admin, every browser
     * tab and every background badge poll on the dashboard. One person's page navigation could
     * therefore 429 someone else's.
     *
     * <p>This filter is an unordered {@code @Component}, so it runs after Spring Security's chain and
     * the principal is populated by then. If it is ever absent — an unauthenticated request, or the
     * filter order changing — this falls back to the IP and behaves exactly as it did before.
     *
     * <p>Every other tier stays IP-keyed. {@code AUTH_SENSITIVE} especially: brute force is a
     * property of where the requests come from, not of whoever the caller claims to be.
     *
     * <p>A private bucket is given only to callers who hold an admin-side authority. {@code
     * /api/admin/**} is merely {@code .authenticated()} in the filter chain — the role check is a
     * method-level {@code @PreAuthorize} that runs in the DispatcherServlet, downstream of every
     * servlet filter — so an ordinary customer's token reaches this limiter and would otherwise mint
     * its own private budget on a path it can only ever be 403'd from. Those callers stay in the
     * shared IP bucket, which is where they were before, so the per-account ceiling is raised only
     * for people who can actually use the dashboard.
     */
    // Package-private so RateLimitingFilterSubjectTest can pin the tier-by-tier behaviour.
    String resolveBucketSubject(RateLimitTier tier, String clientIp) {
        if (tier != RateLimitTier.ADMIN) {
            return clientIp;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return clientIp;
        }
        String name = auth.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return clientIp;
        }
        if (!isAdminSide(auth)) {
            return clientIp;
        }
        return "u:" + name;
    }

    /**
     * Whether the caller is a dashboard-side account rather than a shopper.
     *
     * <p>Keyed on {@code ROLE_ADMIN} / {@code ROLE_SUPERADMIN} rather than on the specific job roles
     * (MARKETING, PROCUREMENT, REPAIR, STORE_ADMIN, …) because {@code JwtAuthenticationFilter} derives
     * {@code ROLE_<USER_TYPE>} for every account, so every dashboard user carries {@code ROLE_ADMIN}
     * whatever their role is, and a shopper carries {@code ROLE_CUSTOMER}. Enumerating job roles here
     * would silently drop any role added later back into the shared bucket.
     */
    private static boolean isAdminSide(Authentication auth) {
        for (var authority : auth.getAuthorities()) {
            String value = authority.getAuthority();
            if ("ROLE_ADMIN".equals(value) || "ROLE_SUPERADMIN".equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static long capacityOf(BucketConfiguration configuration) {
        return configuration.getBandwidths()[0].getCapacity();
    }

    private String resolveClientIp(HttpServletRequest request) {
        // Only trust X-Forwarded-For when explicitly configured (i.e. behind a trusted
        // reverse proxy). Otherwise a client could spoof the header to get a fresh
        // rate-limit bucket on every request and bypass throttling entirely.
        if (trustForwardedHeaders) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].strip();
            }
        }
        return request.getRemoteAddr();
    }

    private RateLimitTier determineTier(String path) {
        // Credential / OTP / password endpoints — strongest throttle (brute force,
        // credential stuffing, OTP guessing, token-setup abuse). Per-account limits are
        // additionally enforced in the service layer (LoginAttemptService / OTP attempts).
        if (path.equals("/auth/signup")
                || path.equals("/auth/signin")
                || path.equals("/auth/verify-otp")
                || path.startsWith("/auth/admin/")            // admin signin/signup/verify-otp
                || path.contains("forgot-password")
                || path.contains("reset-password")
                || path.contains("resend-otp")
                || path.contains("verify-otp")
                || path.startsWith("/api/supplier/auth/")     // supplier login / password setup
                || path.startsWith("/api/membership/auth/")) { // B2B token-gated password setup
            return RateLimitTier.AUTH_SENSITIVE;
        }
        if (path.startsWith("/auth/")) {
            return RateLimitTier.AUTH_GENERAL;
        }
        if (path.startsWith("/api/admin/")) {
            return RateLimitTier.ADMIN;
        }
        // The visitor beacon fires once per page view, so leaving it in the PUBLIC tier would make
        // every page cost two tokens and roughly halve the browsing budget of a shared/NAT'd IP.
        // Its own bucket keeps shoppers browsing freely while still capping beacon abuse.
        if (path.startsWith("/api/analytics/")) {
            return RateLimitTier.ANALYTICS_BEACON;
        }
        // Every accepted assistant message is a paid model call, so this endpoint is the only one
        // where throttling protects the bill and not just the servers. Its own tier keeps that
        // ceiling well below PUBLIC without making the storefront feel slow around it.
        //
        // Only /chat — not the whole /api/assistant/ prefix. The widget probes /status on every page
        // load to decide whether to render, so putting that in the same bucket would spend the
        // customer's chat budget on browsing and 429 them before they had asked anything.
        if (path.equals("/api/assistant/chat")) {
            return RateLimitTier.ASSISTANT;
        }
        return RateLimitTier.PUBLIC;
    }

    enum RateLimitTier {

        // 5 req/min — brute-force protection on signup/signin/OTP
        AUTH_SENSITIVE {
            
            BucketConfiguration buildConfiguration() {
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build())
                        .build();
            }
        },

        // 10 req/min — Google OAuth and other auth flows
        AUTH_GENERAL {
            
            BucketConfiguration buildConfiguration() {
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build())
                        .build();
            }
        },

        // 300 req/min per ADMIN — dashboard operations.
        //
        // Was 30, which the dashboard could not live inside. Its shell idles at ~10 req/min per open
        // tab before anyone clicks anything (three Procurement badge counts, the Repair badge and the
        // notification bell, each on a 30s timer), every page load fires several more in parallel
        // (the home page alone makes five), and until this tier became per-admin every admin and
        // every tab drew from the same bucket. Admins were being 429'd for using the dashboard
        // normally.
        //
        // 300/min is ~5 req/s sustained per admin: comfortable for parallel page loads and badge
        // polling, still low enough to stop a runaway client loop.
        ADMIN {

            BucketConfiguration buildConfiguration() {
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder().capacity(300).refillGreedy(300, Duration.ofMinutes(1)).build())
                        .build();
            }
        },

        // 240 req/min — storefront page-view beacon. Higher than PUBLIC because it rides along with
        // browsing rather than replacing it, and cheap to serve (one insert, no reads).
        ANALYTICS_BEACON {

            BucketConfiguration buildConfiguration() {
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder().capacity(240).refillGreedy(240, Duration.ofMinutes(1)).build())
                        .build();
            }
        },

        // 12 req/min — the storefront AI assistant.
        //
        // Sized on how fast a person actually types, not on what the servers could take: a customer
        // in a real conversation sends a message every few seconds at most, so 12/min is generous
        // for them and cuts off a script immediately. It is deliberately far below PUBLIC because
        // every request here costs money at the model provider — this is the one endpoint where the
        // limiter is a spend control, and where failing open would be writing a blank cheque.
        ASSISTANT {

            BucketConfiguration buildConfiguration() {
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder().capacity(12).refillGreedy(12, Duration.ofMinutes(1)).build())
                        .build();
            }
        },

        // 100 req/min — public product/category/story browsing
        PUBLIC {
            
            BucketConfiguration buildConfiguration() {
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder().capacity(100).refillGreedy(100, Duration.ofMinutes(1)).build())
                        .build();
            }
        };

        abstract BucketConfiguration buildConfiguration();

        /** Auth-sensitive tiers must not fail open when Redis is unavailable. */
        boolean isAuthSensitive() {
            return this == AUTH_SENSITIVE || this == AUTH_GENERAL;
        }

        /**
         * Tiers that fall back to a per-instance in-memory bucket instead of failing open while
         * Redis is down. Auth tiers, because brute-force protection matters most exactly then — the
         * visitor beacon, because it is unauthenticated and every accepted request is an INSERT, so
         * failing open there means an anonymous caller can grow the table without limit — and the
         * assistant, because failing open on it means an anonymous caller can spend money at the
         * model provider for as long as Redis stays down. Reads (PUBLIC, ADMIN) keep failing open:
         * throttling a page of products is not worth an outage.
         */
        boolean needsLocalFallback() {
            return isAuthSensitive() || this == ANALYTICS_BEACON || this == ASSISTANT;
        }
    }
}
