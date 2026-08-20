package com.buyology.ecommerce.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the CORS allowlist, which fails in the one way that is hardest to diagnose.
 *
 * <p>{@code app.cors.allowed-origins} resolves straight from {@code CORS_ALLOWED_ORIGINS} with no
 * default, so the storefront's access to the API used to depend entirely on a value set separately
 * on each server. When that value is wrong nothing in the code is wrong: every request still
 * succeeds from curl and every request fails from the browser, with the reason visible only in a
 * devtools console nobody is watching. The first test below is the guarantee that this can no
 * longer happen to the storefront.
 *
 * <p>The rest pin the guards that must survive that guarantee — in particular that adding built-in
 * origins did not quietly turn a wiped {@code CORS_ALLOWED_ORIGINS} into a booting application with
 * a half-working dashboard.
 */
class SecurityConfigCorsTest {

    private static final String SHOP = "https://buyology.online";
    private static final String ASSISTANT_SITE = "https://v2.buyology.online";

    @Test
    void storefrontIsAllowedEvenWhenTheEnvironmentForgetsIt() {
        List<String> allowed = SecurityConfig.resolveAllowedOrigins("https://admin.buyology.online");

        assertTrue(allowed.contains(SHOP),
                "the storefront must not depend on per-server configuration");
        assertTrue(allowed.contains("https://admin.buyology.online"),
                "configured origins are kept, not replaced");
    }

    @Test
    void doesNotDuplicateAnOriginThatIsAlsoConfigured() {
        List<String> allowed = SecurityConfig.resolveAllowedOrigins(
                "https://admin.buyology.online," + SHOP);

        assertEquals(1, allowed.stream().filter(SHOP::equals).count());
        assertEquals(3, allowed.size(), "one configured origin plus both built-ins");
    }

    @Test
    void keepsConfiguredOriginsFirstAndInOrder() {
        List<String> allowed = SecurityConfig.resolveAllowedOrigins(
                "https://a.example.com,https://b.example.com");

        assertEquals("https://a.example.com", allowed.get(0));
        assertEquals("https://b.example.com", allowed.get(1));
        assertEquals(SHOP, allowed.get(2), "built-ins are appended, never inserted");
        assertEquals(ASSISTANT_SITE, allowed.get(3));
    }

    @Test
    void trimsWhitespaceAndIgnoresEmptyEntries() {
        List<String> allowed = SecurityConfig.resolveAllowedOrigins(
                "  https://a.example.com ,, https://b.example.com  ,");

        assertTrue(allowed.contains("https://a.example.com"));
        assertTrue(allowed.contains("https://b.example.com"));
        assertFalse(allowed.contains(""));
    }

    @Test
    void everyBuiltInOriginIsPresentInTheResult() {
        List<String> allowed = SecurityConfig.resolveAllowedOrigins("https://admin.buyology.online");

        assertTrue(allowed.containsAll(SecurityConfig.BUILT_IN_ALLOWED_ORIGINS),
                "adding to BUILT_IN_ALLOWED_ORIGINS must take effect without touching this method");
    }

    // ── Guards that must survive the built-in list ───────────────────────────

    @Test
    void stillRefusesToStartOnAWildcard() {
        // A wildcard with allowCredentials=true is rejected by the browser AND a security hole.
        assertThrows(IllegalStateException.class,
                () -> SecurityConfig.resolveAllowedOrigins("https://a.example.com,*"));
        assertThrows(IllegalStateException.class,
                () -> SecurityConfig.resolveAllowedOrigins("*"));
    }

    @Test
    void stillRefusesToStartOnAnEmptyValue() {
        // The built-ins must not mask a wiped CORS_ALLOWED_ORIGINS: the app would boot, the
        // storefront would work, and the dashboard would silently lose access — which is a far
        // worse failure than not booting.
        assertThrows(IllegalStateException.class, () -> SecurityConfig.resolveAllowedOrigins(""));
        assertThrows(IllegalStateException.class, () -> SecurityConfig.resolveAllowedOrigins("   "));
        assertThrows(IllegalStateException.class, () -> SecurityConfig.resolveAllowedOrigins(",, ,"));
        assertThrows(IllegalStateException.class, () -> SecurityConfig.resolveAllowedOrigins(null));
    }

    @Test
    void theResultIsImmutable() {
        List<String> allowed = SecurityConfig.resolveAllowedOrigins("https://a.example.com");

        assertThrows(UnsupportedOperationException.class, () -> allowed.add("https://evil.example"));
    }
}
