package com.buyology.ecommerce.infrastructure.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
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
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final LettuceConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    // Lazily initialized; retries every 30 s if Redis is unavailable
    private volatile LettuceBasedProxyManager<String> proxyManager;
    private volatile long lastAttemptMs = 0;
    private static final long RETRY_INTERVAL_MS = 30_000;

    public RateLimitingFilter(LettuceConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        LettuceBasedProxyManager<String> pm = acquireProxyManager();
        if (pm == null) {
            // Redis unavailable — fail open, let the request through
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        RateLimitTier tier = determineTier(path);
        String bucketKey = "rl:" + tier.name() + ":" + clientIp;

        try {
            var bucket = pm.builder().build(bucketKey, tier::buildConfiguration);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

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
        } catch (Exception e) {
            log.warn("Rate limiting error on {}, allowing request: {}", path, e.getMessage());
            filterChain.doFilter(request, response);
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
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }

    private RateLimitTier determineTier(String path) {
        if (path.equals("/auth/signup")
                || path.equals("/auth/signin")
                || path.equals("/auth/verify-otp")) {
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
            @Override
            BucketConfiguration buildConfiguration() {
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofMinutes(1)).build())
                        .build();
            }
        },

        // 10 req/min — Google OAuth and other auth flows
        AUTH_GENERAL {
            @Override
            BucketConfiguration buildConfiguration() {
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder().capacity(10).refillGreedy(10, Duration.ofMinutes(1)).build())
                        .build();
            }
        },

        // 30 req/min — admin operations
        ADMIN {
            @Override
            BucketConfiguration buildConfiguration() {
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder().capacity(30).refillGreedy(30, Duration.ofMinutes(1)).build())
                        .build();
            }
        },

        // 100 req/min — public product/category/story browsing
        PUBLIC {
            @Override
            BucketConfiguration buildConfiguration() {
                return BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder().capacity(100).refillGreedy(100, Duration.ofMinutes(1)).build())
                        .build();
            }
        };

        abstract BucketConfiguration buildConfiguration();
    }
}
