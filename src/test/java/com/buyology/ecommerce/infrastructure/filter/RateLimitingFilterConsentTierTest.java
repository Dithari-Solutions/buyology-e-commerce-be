package com.buyology.ecommerce.infrastructure.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.buyology.ecommerce.infrastructure.filter.RateLimitingFilter.RateLimitTier.*;

class RateLimitingFilterConsentTierTest {
    final RateLimitingFilter filter = new RateLimitingFilter(null, new ObjectMapper(), false, "", 0, 0, 0);
    @Test void anonymousConsentWritesAreThrottledEvenWithoutRedisAndDoNotSpendTheShoppingBudget() {
        assertEquals(ANALYTICS_BEACON, filter.determineTier("/api/privacy/consents"));
        assertEquals(true, ANALYTICS_BEACON.needsLocalFallback());
        assertEquals(PUBLIC, filter.determineTier("/api/privacy/cookies"));
    }
    @Test void readingConsentHistoryUsesTheAuthenticatedAdminBudget() {
        assertEquals(ADMIN, filter.determineTier("/api/admin/privacy/consents"));
    }
}
