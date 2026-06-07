package com.buyology.ecommerce.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Issues and validates short-lived, single-use MFA tickets in Redis.
 *
 * A ticket represents a half-authenticated state: the password was verified but
 * the second factor has not been. Keeping this out of the real JWT means a ticket
 * carries no roles/permissions and can never be replayed as an access token.
 *
 * Two purposes:
 *  - {@link Purpose#ENROLL} — issued at login when a mandatory account has not yet
 *    set up 2FA; consumed by enroll/confirm.
 *  - {@link Purpose#LOGIN}  — issued at login when 2FA is already enabled; consumed
 *    by the verify step.
 *
 * Each ticket allows a bounded number of code attempts before it self-destructs.
 */
@Service
public class MfaTicketService {

    public enum Purpose { ENROLL, LOGIN }

    public record Ticket(Purpose purpose, UUID authCredentialId) {}

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;
    private static final String TICKET_PREFIX = "mfa:ticket:";
    private static final String ATTEMPT_PREFIX = "mfa:ticket:attempts:";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public MfaTicketService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Mint a new opaque ticket bound to the given credential and purpose. */
    public String issue(Purpose purpose, UUID authCredentialId) {
        String token = newToken();
        redis.opsForValue().set(TICKET_PREFIX + token, purpose.name() + "|" + authCredentialId, TTL);
        return token;
    }

    /** Resolve a ticket without consuming it. Returns null if absent or purpose mismatch. */
    public Ticket resolve(String token, Purpose expected) {
        if (token == null || token.isBlank()) return null;
        String value = redis.opsForValue().get(TICKET_PREFIX + token);
        if (value == null) return null;
        int sep = value.indexOf('|');
        if (sep < 0) return null;
        Purpose purpose;
        try {
            purpose = Purpose.valueOf(value.substring(0, sep));
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (purpose != expected) return null;
        try {
            return new Ticket(purpose, UUID.fromString(value.substring(sep + 1)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Delete a ticket and its attempt counter (single-use consumption / invalidation). */
    public void invalidate(String token) {
        if (token == null) return;
        redis.delete(TICKET_PREFIX + token);
        redis.delete(ATTEMPT_PREFIX + token);
    }

    /**
     * Record a failed code attempt against a ticket. Returns the number of attempts
     * remaining; when it reaches zero the ticket is invalidated so it can no longer
     * be used.
     */
    public int recordFailedAttempt(String token) {
        String key = ATTEMPT_PREFIX + token;
        Long attempts = redis.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redis.expire(key, TTL);
        }
        int used = attempts == null ? MAX_ATTEMPTS : attempts.intValue();
        int remaining = Math.max(0, MAX_ATTEMPTS - used);
        if (remaining == 0) {
            invalidate(token);
        }
        return remaining;
    }

    private static String newToken() {
        byte[] bytes = new byte[32]; // 256 bits of entropy
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
