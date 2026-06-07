package com.buyology.ecommerce.infrastructure.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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

    // Lazily initialized; retries every 30 s if Redis is unavailable
    private volatile LettuceBasedProxyManager<String> proxyManager;
    private volatile long lastAttemptMs = 0;
    private static final long RETRY_INTERVAL_MS = 30_000;

    // Local in-memory fallback used for AUTH tiers when Redis is unavailable, so
    // brute-force protection survives a Redis outage (fail-closed-ish, not fail-open).
    // Per-instance only — acceptable degraded mode until Redis recovers.
    private final ConcurrentHashMap<String, Bucket> localBuckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(LettuceConnectionFactory connectionFactory,
                              ObjectMapper objectMapper,
                              @Value("${app.trust-forwarded-headers:false}") boolean trustForwardedHeaders) {
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
        this.trustForwardedHeaders = trustForwardedHeaders;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        RateLimitTier tier = determineTier(path);
        String bucketKey = "rl:" + tier.name() + ":" + clientIp;

        LettuceBasedProxyManager<String> pm = acquireProxyManager();
        if (pm == null) {
            // Redis is unavailable.
            // For auth-sensitive endpoints we must NOT fail open (that would disable
            // brute-force protection exactly when it matters most). Fall back to a
            // per-instance in-memory bucket so the limiter keeps working.
            if (tier.isAuthSensitive()) {
                applyLocalFallback(bucketKey, tier, request, response, filterChain, path);
            } else {
                // Non-sensitive tiers: keep failing open to preserve availability.
                filterChain.doFilter(request, response);
            }
            return;
        }

        try {
            var bucket = pm.builder().build(bucketKey, tier::buildConfiguration);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            handleProbe(probe, request, response, filterChain, path);
        } catch (Exception e) {
            log.warn("Rate limiting error on {} (Redis path): {}", path, e.getMessage());
            // Redis errored mid-request. Protect auth-sensitive endpoints with the
            // local fallback instead of waving the request through.
            if (tier.isAuthSensitive()) {
                applyLocalFallback(bucketKey, tier, request, response, filterChain, path);
            } else {
                filterChain.doFilter(request, response);
            }
        }
    }

    /**
     * Throttles using a per-instance in-memory bucket. Used for auth tiers when Redis
     * is unavailable so brute-force protection does not silently disappear.
     */
    private void applyLocalFallback(String bucketKey, RateLimitTier tier, HttpServletRequest request,
                                    HttpServletResponse response, FilterChain filterChain, String path)
            throws ServletException, IOException {
        Bucket localBucket = localBuckets.computeIfAbsent(bucketKey,
                k -> Bucket.builder().addLimit(tier.buildConfiguration().getBandwidths()[0]).build());
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
                proxyManager = Bucket4jLettuce.<String>casBasedBuilder(connection).build();
                log.info("Rate limiter connected to Redis");
            } catch (Exception e) {
                log.warn("Redis unavailable — rate limiting disabled, retrying in {}s: {}",
                        RETRY_INTERVAL_MS / 1000, e.getMessage());
            }
        }
        return proxyManager;
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

        // 30 req/min — admin operations
        ADMIN {
            
            BucketConfiguration buildConfiguration() {
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder().capacity(30).refillGreedy(30, Duration.ofMinutes(1)).build())
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
    }
}
