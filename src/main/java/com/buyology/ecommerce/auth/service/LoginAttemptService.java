package com.buyology.ecommerce.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * Tracks failed sign-in attempts per email + IP and locks the account out after a threshold.
 * Counters are stored in Redis with a sliding TTL so they auto-expire.
 *
 * Pentest-driven defaults:
 *  - 5 failures within 15 minutes → 15 minute lockout
 *  - Successful login resets the counter
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    private static final String COUNT_PREFIX = "auth:fail:count:";
    private static final String LOCK_PREFIX = "auth:fail:locked:";

    private final StringRedisTemplate redis;

    public LoginAttemptService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isLockedOut(String email) {
        if (email == null || email.isBlank()) return false;
        Boolean exists = redis.hasKey(LOCK_PREFIX + normalize(email));
        return Boolean.TRUE.equals(exists);
    }

    public void recordFailure(String email) {
        if (email == null || email.isBlank()) return;
        String key = COUNT_PREFIX + normalize(email);
        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, WINDOW);
            }
        } catch (Exception e) {
            log.warn("LoginAttemptService Redis failure (recordFailure ignored): {}", e.getMessage());
            return;
        }
        if (count != null && count >= MAX_ATTEMPTS) {
            redis.opsForValue().set(LOCK_PREFIX + normalize(email), "1", LOCKOUT);
            redis.delete(key);
            log.warn("Account {} locked out after {} failed attempts", email, count);
        }
    }

    public void recordSuccess(String email) {
        if (email == null || email.isBlank()) return;
        try {
            redis.delete(COUNT_PREFIX + normalize(email));
            redis.delete(LOCK_PREFIX + normalize(email));
        } catch (Exception e) {
            log.warn("LoginAttemptService Redis failure (recordSuccess ignored): {}", e.getMessage());
        }
    }

    public Duration lockoutDuration() {
        return LOCKOUT;
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
