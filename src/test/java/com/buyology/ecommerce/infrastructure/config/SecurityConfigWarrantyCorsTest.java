package com.buyology.ecommerce.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dithari's site may read the warranty lookup cross-origin, and nothing else. The danger this pins
 * is the easy mistake: adding Dithari to the main list, which would let a page there make
 * credentialed calls to every endpoint with a shopper's cookies.
 */
class SecurityConfigWarrantyCorsTest {

    private static final String DITHARI = "https://v2.dithari.com";

    private static CorsConfiguration corsFor(String path) {
        SecurityConfig config = new SecurityConfig(null, null, null, null, null, null);
        ReflectionTestUtils.setField(config, "allowedOriginsCsv", "https://dashboard.buyology.online");
        UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) config.corsConfigurationSource();
        return source.getCorsConfiguration(new MockHttpServletRequest("GET", path));
    }

    @Test
    void dithariMayReadTheLookupWithoutCredentials() {
        CorsConfiguration lookup = corsFor("/api/warranty/DTH-7K4Q-2M9X-QH3T");
        assertNotNull(lookup);
        assertEquals("https://v2.dithari.com", lookup.checkOrigin(DITHARI));
        assertEquals("https://dithari.com", lookup.checkOrigin("https://dithari.com"));
        assertEquals(Boolean.FALSE, lookup.getAllowCredentials());
        assertEquals(List.of("GET", "OPTIONS"), lookup.getAllowedMethods());
    }

    @Test
    void dithariIsNotAllowedAnywhereElse() {
        CorsConfiguration main = corsFor("/api/orders/123");
        assertNotNull(main);
        assertNull(main.checkOrigin(DITHARI), "Dithari must not reach credentialed endpoints");
        assertEquals(Boolean.TRUE, main.getAllowCredentials(), "the main list is unchanged");
    }

    @Test
    void ourOwnFrontendsCanStillCallTheLookup() {
        CorsConfiguration lookup = corsFor("/api/warranty/DTH-7K4Q-2M9X-QH3T");
        assertEquals("https://buyology.online", lookup.checkOrigin("https://buyology.online"));
        assertEquals("https://dashboard.buyology.online", lookup.checkOrigin("https://dashboard.buyology.online"));
    }

    @Test
    void theLookupListHasNoDuplicatesWhenAnOriginIsInBoth() {
        CorsConfiguration lookup = SecurityConfig.warrantyLookupCors(List.of(DITHARI, "https://buyology.online"));
        assertEquals(1, lookup.getAllowedOrigins().stream().filter(DITHARI::equals).count());
        assertEquals(SecurityConfig.WARRANTY_LOOKUP_ORIGINS.size() + 1, lookup.getAllowedOrigins().size());
    }
}
