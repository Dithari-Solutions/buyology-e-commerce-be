package com.buyology.ecommerce.infrastructure.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static com.buyology.ecommerce.infrastructure.filter.RateLimitingFilter.RateLimitTier.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RateLimitingFilterWarrantyTierTest {
    final RateLimitingFilter filter = new RateLimitingFilter(null, new ObjectMapper(), false, "", 0, 0, 0);

    @Test void warrantyLookupsHaveTheirOwnBudgetApartFromBrowsing() {
        assertEquals(WARRANTY_LOOKUP, filter.determineTier("/api/warranty/DTH-7K4Q-2M9X-QH3T"));
        assertEquals(PUBLIC, filter.determineTier("/api/product/123"));
    }

    @Test void adminWarrantyEndpointsStayInTheAdminBudget() {
        assertEquals(ADMIN, filter.determineTier("/api/admin/warranty-presets"));
        assertEquals(ADMIN, filter.determineTier("/api/admin/warranty-cards/DTH-7K4Q-2M9X-QH3T/void"));
    }

    @Test void aReadOnlyLookupFailsOpenWhenRedisIsDownLikeOtherReads() {
        assertFalse(WARRANTY_LOOKUP.needsLocalFallback());
    }
}
