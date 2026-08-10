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
            new RateLimitingFilter(null, new ObjectMapper(), false, "", 0);

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
}
