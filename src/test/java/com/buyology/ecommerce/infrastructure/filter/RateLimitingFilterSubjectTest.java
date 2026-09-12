package com.buyology.ecommerce.infrastructure.filter;

import com.buyology.ecommerce.infrastructure.filter.RateLimitingFilter.RateLimitTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins which rate-limit tiers are keyed on the authenticated caller and which stay keyed on the
 * client IP.
 *
 * <p>Both halves matter. ADMIN must be per-admin: every admin reaches the API through the same nginx
 * load balancer, and with {@code app.trust-forwarded-headers=false} an IP-keyed bucket is shared by
 * every admin and every open dashboard tab, which is what returned 429s during normal dashboard use.
 *
 * <p>Every other tier must stay IP-keyed — AUTH_SENSITIVE above all. Brute force is a property of
 * where requests come from, so an authenticated caller must never be able to move themselves out of
 * the IP bucket that protects the credential endpoints.
 */
class RateLimitingFilterSubjectTest {

    private static final String CLIENT_IP = "203.0.113.7";

    /** Only the constructor's field assignments are exercised, so the Redis factory can be null. */
    private final RateLimitingFilter filter =
            new RateLimitingFilter(null, new ObjectMapper(), false, "", 0, 0, 0);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private UUID authenticate(String... roles) {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(roles).stream().map(SimpleGrantedAuthority::new).toList()));
        return userId;
    }

    /** JwtAuthenticationFilter derives ROLE_&lt;USER_TYPE&gt;, so every dashboard account has ROLE_ADMIN. */
    private UUID authenticateAdmin() {
        return authenticate("ROLE_ADMIN");
    }

    @Test
    void adminTierIsKeyedOnTheAuthenticatedAdmin() {
        UUID adminId = authenticateAdmin();

        assertEquals("u:" + adminId, filter.resolveBucketSubject(RateLimitTier.ADMIN, CLIENT_IP));
    }

    @Test
    void twoAdminsBehindOneProxyIpGetSeparateBuckets() {
        UUID first = authenticateAdmin();
        String firstSubject = filter.resolveBucketSubject(RateLimitTier.ADMIN, CLIENT_IP);

        SecurityContextHolder.clearContext();
        UUID second = authenticateAdmin();
        String secondSubject = filter.resolveBucketSubject(RateLimitTier.ADMIN, CLIENT_IP);

        assertNotEquals(first, second);
        assertNotEquals(firstSubject, secondSubject,
                "same proxy IP must not merge two admins into one bucket");
    }

    @Test
    void everyOtherTierStaysKeyedOnTheClientIpEvenWhenAuthenticated() {
        authenticateAdmin();

        for (RateLimitTier tier : RateLimitTier.values()) {
            if (tier == RateLimitTier.ADMIN) continue;
            assertEquals(CLIENT_IP, filter.resolveBucketSubject(tier, CLIENT_IP),
                    tier + " must stay IP-keyed: an authenticated caller must not be able to leave "
                            + "the IP bucket that throttles this tier");
        }
    }

    @Test
    void aShopperTokenPokingAtAdminPathsStaysInTheSharedIpBucket() {
        // /api/admin/** is only .authenticated() in the filter chain — the role check is a
        // method-level @PreAuthorize that runs after every filter, so a customer's token does reach
        // this limiter. It must not earn a private per-account budget on a path it can only be 403'd
        // from, or the ceiling for junk authenticated traffic would rise with every account created.
        authenticate("ROLE_CUSTOMER");

        assertEquals(CLIENT_IP, filter.resolveBucketSubject(RateLimitTier.ADMIN, CLIENT_IP));
    }

    @Test
    void everyDashboardRoleKeepsItsOwnBudgetRegardlessOfJobRole() {
        // A MARKETING / PROCUREMENT / REPAIR admin still carries ROLE_ADMIN from its user type, so
        // enumerating job roles is unnecessary — and would have dropped them into the shared bucket.
        UUID marketingAdmin = authenticate("ROLE_ADMIN", "ROLE_MARKETING");

        assertEquals("u:" + marketingAdmin, filter.resolveBucketSubject(RateLimitTier.ADMIN, CLIENT_IP));
    }

    @Test
    void adminTierFallsBackToTheIpWithoutAnAuthenticatedCaller() {
        // No authentication at all — e.g. if this filter ever runs before the security chain.
        assertEquals(CLIENT_IP, filter.resolveBucketSubject(RateLimitTier.ADMIN, CLIENT_IP));

        // Spring's anonymous token is authenticated() == true, so it must be rejected by name.
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertEquals(CLIENT_IP, filter.resolveBucketSubject(RateLimitTier.ADMIN, CLIENT_IP));
    }

    // ── Refresh must not share the credential tiers' ceiling ─────────────────

    @Test
    void refreshGetsItsOwnTierRatherThanAuthGeneral() throws Exception {
        // /auth/refresh sat in AUTH_GENERAL's 10/min. Every tier except ADMIN is keyed on the
        // client IP, and behind the proxy with trust-forwarded-headers off that is ONE bucket for
        // the whole platform — so ten reloads a minute, across all customers and admins together,
        // exhausted it and the rest got a 429. A failed refresh is a logout, which is exactly the
        // "it logs me out when I refresh, on the website and the dashboard" report.
        assertEquals(RateLimitTier.AUTH_REFRESH, tierOf("/auth/refresh"));
    }

    @Test
    void theCredentialEndpointsKeepTheirNarrowTier() throws Exception {
        // The new tier must not have widened anything it should not. These are guessable and stay
        // where they were.
        assertEquals(RateLimitTier.AUTH_SENSITIVE, tierOf("/auth/signin"));
        assertEquals(RateLimitTier.AUTH_SENSITIVE, tierOf("/auth/signup"));
        assertEquals(RateLimitTier.AUTH_SENSITIVE, tierOf("/auth/verify-otp"));
        assertEquals(RateLimitTier.AUTH_SENSITIVE, tierOf("/api/verify/phone/start"));
        assertEquals(RateLimitTier.AUTH_GENERAL, tierOf("/auth/google"));
    }

    @Test
    void refreshDoesNotFailClosedWhenRedisIsDown() {
        // The credential tiers fall back to a local bucket so brute-force protection survives an
        // outage. Refresh must not: it is validated against the database regardless of any
        // throttle, so refusing it during a Redis blip logs every signed-in user out and buys
        // nothing.
        assertFalse(RateLimitTier.AUTH_REFRESH.isAuthSensitive());
        assertFalse(RateLimitTier.AUTH_REFRESH.needsLocalFallback());
        assertTrue(RateLimitTier.AUTH_SENSITIVE.needsLocalFallback(),
                "credential endpoints must still fail closed");
    }

    /** determineTier is private; reach it the way the filter does. */
    private RateLimitTier tierOf(String path) throws Exception {
        var m = RateLimitingFilter.class.getDeclaredMethod("determineTier", String.class);
        m.setAccessible(true);
        return (RateLimitTier) m.invoke(filter, path);
    }
}
