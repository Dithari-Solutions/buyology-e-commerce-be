package com.buyology.ecommerce.infrastructure.filter;

import com.buyology.ecommerce.infrastructure.filter.RateLimitingFilter.RateLimitTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins Paymob's callbacks to a bucket of their own.
 *
 * <p>In PUBLIC they shared one bucket with every shopper on the platform — forwarded headers are
 * untrusted, so the key is the proxy's address — and a 429 there is a paid order that never learns
 * it was paid. Still throttled, though: the webhook is unauthenticated.
 */
class RateLimitingFilterPaymentTierTest {

    private final RateLimitingFilter filter =
            new RateLimitingFilter(null, new ObjectMapper(), false, "", 0, 0, 0);

    @Test
    void paymobCallbacksHaveTheirOwnBucket() {
        assertEquals(RateLimitTier.PAYMENT_CALLBACK, filter.determineTier("/api/payments/webhook"));
        assertEquals(RateLimitTier.PAYMENT_CALLBACK, filter.determineTier("/api/payments/confirm-redirect"));
    }

    @Test
    void theRestOfThePaymentApiStaysWhereItWas() {
        assertEquals(RateLimitTier.PUBLIC, filter.determineTier("/api/payments/transactions/123"));
    }
}
