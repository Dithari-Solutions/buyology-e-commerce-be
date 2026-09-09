package com.buyology.ecommerce.customeremail.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Limits on how much customer mail can leave, modelled on {@code PhoneVerificationGuard}.
 *
 * <p>Same reasoning as the SMS guard, learned the same way: the endpoint that reaches people in
 * bulk is the one that needs a ceiling, an accountable subject and a kill switch — and it fails
 * CLOSED, because if we cannot count what we are sending we should not send it.
 *
 * <p>Counted in Redis rather than in memory so the limits hold across both application hosts. An
 * in-memory counter would give each host its own allowance and quietly double every cap.
 */
@Service
public class CustomerEmailGuard {

    private static final Logger log = LoggerFactory.getLogger(CustomerEmailGuard.class);

    private static final String ADMIN_PREFIX = "cemail:admin:";
    private static final String GLOBAL_PREFIX = "cemail:global:";
    private static final Duration DAY = Duration.ofHours(25);

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final int maxPerCampaign;
    private final int maxCampaignsPerAdminPerDay;
    private final int maxRecipientsPerDay;

    public CustomerEmailGuard(
            StringRedisTemplate redis,
            @Value("${customer-email.enabled:true}") boolean enabled,
            // Deliberately low to start. There is no email_verified column anywhere in this
            // backend, so the real bounce rate of these addresses is unknown, and one large burst
            // is the most expensive possible way to discover it.
            @Value("${customer-email.max-recipients-per-campaign:150}") int maxPerCampaign,
            @Value("${customer-email.max-campaigns-per-admin-per-day:5}") int maxCampaignsPerAdminPerDay,
            @Value("${customer-email.max-recipients-per-day:1000}") int maxRecipientsPerDay) {
        this.redis = redis;
        this.enabled = enabled;
        this.maxPerCampaign = maxPerCampaign;
        this.maxCampaignsPerAdminPerDay = maxCampaignsPerAdminPerDay;
        this.maxRecipientsPerDay = maxRecipientsPerDay;
    }

    /** Throws unless this campaign may be created and sent. */
    public void checkCampaign(UUID adminId, int recipientCount) {
        if (!enabled) {
            throw new IllegalStateException("Customer email is switched off.");
        }
        if (recipientCount > maxPerCampaign) {
            throw new IllegalStateException("That audience is " + recipientCount + " people, above the "
                    + maxPerCampaign + " limit for a single send. Narrow it, or raise the limit deliberately.");
        }
        String day = LocalDate.now(ZoneOffset.UTC).toString();
        consume(ADMIN_PREFIX + adminId + ":" + day, 1, maxCampaignsPerAdminPerDay,
                "You have sent the maximum number of campaigns for today.");
        consume(GLOBAL_PREFIX + day, recipientCount, maxRecipientsPerDay,
                "The daily limit on customer emails has been reached.");
    }

    private void consume(String key, int amount, int limit, String message) {
        Long used;
        try {
            used = redis.opsForValue().increment(key, amount);
            if (used != null && used == amount) redis.expire(key, DAY);
        } catch (Exception e) {
            // Fails closed, on purpose. See the class comment.
            log.error("[CUSTOMER-EMAIL] Redis unavailable, refusing to send: {}", e.getMessage());
            throw new IllegalStateException("Customer email is temporarily unavailable. Please try again shortly.");
        }
        if (used != null && used > limit) {
            throw new IllegalStateException(message);
        }
    }
}
