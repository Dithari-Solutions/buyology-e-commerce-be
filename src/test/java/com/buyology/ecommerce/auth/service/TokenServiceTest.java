package com.buyology.ecommerce.auth.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.domain.RefreshToken;
import com.buyology.ecommerce.auth.repository.RefreshTokenRepository;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.role.repository.RolePermissionRepository;
import com.buyology.ecommerce.role.repository.UserPermissionRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the JWT signing/validation and the hashed-at-rest refresh-token logic —
 * the core of the round-2 auth hardening.
 */
class TokenServiceTest {

    private static final String SECRET = "unit-test-signing-secret-which-is-long-enough-1234567890";

    /** Matches the production default. Long enough to absorb a page load's parallel refreshes. */
    private static final long GRACE_SECONDS = 30;

    private RefreshTokenRepository refreshRepo;
    private TokenService svc;
    private AuthCredentials creds;
    private final UUID credId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        refreshRepo = mock(RefreshTokenRepository.class);
        UserRoleRepository roleRepo = mock(UserRoleRepository.class);
        RolePermissionRepository rolePermRepo = mock(RolePermissionRepository.class);
        UserPermissionRepository userPermRepo = mock(UserPermissionRepository.class);
        when(refreshRepo.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        svc = new TokenService(refreshRepo, roleRepo, rolePermRepo, userPermRepo,
                SECRET, "buyology-ecommerce-service", 15, 7, true, GRACE_SECONDS);

        creds = new AuthCredentials();
        creds.setId(credId);
        creds.setUserId(userId);
    }

    @Test
    void accessToken_roundTripsAndExposesClaims() {
        String token = svc.generateAccessToken(creds, "dashboard");
        assertTrue(svc.validateAccessToken(token));
        assertEquals(credId, svc.getAuthCredentialId(token));
        assertEquals("dashboard", svc.getAudience(token));
    }

    @Test
    void accessToken_defaultsAudienceToWeb() {
        String token = svc.generateAccessToken(creds);
        assertEquals("web", svc.getAudience(token));
    }

    @Test
    void tamperedToken_isRejected() {
        String token = svc.generateAccessToken(creds, "web");
        // Flip a character in the payload segment.
        int dot = token.indexOf('.');
        char[] chars = token.toCharArray();
        chars[dot + 5] = chars[dot + 5] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);
        assertFalse(svc.validateAccessToken(tampered));
        assertNull(svc.getAuthCredentialId(tampered));
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        TokenService other = new TokenService(refreshRepo, mock(UserRoleRepository.class),
                mock(RolePermissionRepository.class), mock(UserPermissionRepository.class),
                "a-completely-different-secret-also-long-enough-0987654321", "buyology-ecommerce-service",
                15, 7, true, GRACE_SECONDS);
        String foreign = other.generateAccessToken(creds, "web");
        assertFalse(svc.validateAccessToken(foreign), "token signed with a different key must not validate");
    }

    @Test
    void garbageToken_isRejected() {
        assertFalse(svc.validateAccessToken("not-a-jwt"));
        assertFalse(svc.validateAccessToken("a.b.c"));
        assertNull(svc.getAuthCredentialId("garbage"));
    }

    @Test
    void refreshToken_isStoredHashedNotRaw() {
        var issued = svc.generateRefreshToken(creds, "device");
        assertNotNull(issued.rawValue());
        assertNotEquals(issued.rawValue(), issued.token().getToken(),
                "the raw token must never be persisted");
        assertEquals(SecurityUtils.sha256Hex(issued.rawValue()), issued.token().getToken(),
                "the stored value must be the SHA-256 hash of the raw token");
    }

    @Test
    void rotate_looksUpByHashNeverRaw() {
        String rawIncoming = "incoming-raw-refresh-token";
        String expectedHash = SecurityUtils.sha256Hex(rawIncoming);
        RefreshToken existing = new RefreshToken(creds, expectedHash,
                Instant.now().plus(1, ChronoUnit.DAYS), "device");
        when(refreshRepo.findByToken(expectedHash)).thenReturn(Optional.of(existing));

        var result = svc.rotateTokens(rawIncoming, "device", "web");

        assertNotNull(result);
        assertTrue(existing.isRevoked(), "the old refresh token must be revoked (one-time use)");
        verify(refreshRepo).findByToken(expectedHash);
        verify(refreshRepo, never()).findByToken(rawIncoming);
    }

    @Test
    void rotate_rejectsUnknownToken() {
        when(refreshRepo.findByToken(any())).thenReturn(Optional.empty());
        assertThrows(SecurityException.class, () -> svc.rotateTokens("whatever", "device", "web"));
    }

    // ── Concurrent refresh: the reason people were being logged out ──────────

    @Test
    void rotate_acceptsATokenReusedImmediatelyAfterRotation() {
        // One page load fires several API calls; they all 401 on the same expired access token and
        // all present the SAME refresh cookie. Strict single-use rotation rejected every request
        // after the first, and a 401 on refresh signs the user out — so an ordinary reload logged
        // people out of both the storefront and the dashboard.
        String raw = "raced-refresh-token";
        RefreshToken alreadyRotated = new RefreshToken(creds, SecurityUtils.sha256Hex(raw),
                Instant.now().plus(1, ChronoUnit.DAYS), "device");
        alreadyRotated.revoke();                       // stamps updatedAt = now
        when(refreshRepo.findByToken(SecurityUtils.sha256Hex(raw)))
                .thenReturn(Optional.of(alreadyRotated));

        var result = assertDoesNotThrow(() -> svc.rotateTokens(raw, "device", "web"),
                "a refresh racing itself must not log the user out");
        assertNotNull(result);
        assertNotNull(result.signInResponse().getAccessToken(), "the loser of the race still gets a token");
    }

    @Test
    void rotate_doesNotSlideTheGraceWindowForwardOnRepeatedReuse() {
        // Re-revoking on every reuse would re-stamp updatedAt and extend the window indefinitely,
        // which would quietly turn single-use rotation into unlimited reuse.
        String raw = "repeatedly-reused-token";
        RefreshToken rotated = new RefreshToken(creds, SecurityUtils.sha256Hex(raw),
                Instant.now().plus(1, ChronoUnit.DAYS), "device");
        rotated.revoke();
        Instant revokedAt = rotated.getUpdatedAt();
        when(refreshRepo.findByToken(SecurityUtils.sha256Hex(raw))).thenReturn(Optional.of(rotated));

        svc.rotateTokens(raw, "device", "web");
        svc.rotateTokens(raw, "device", "web");

        assertEquals(revokedAt, rotated.getUpdatedAt(),
                "the revocation time must not move, or the window never closes");
    }

    @Test
    void rotate_stillRejectsATokenReplayedAfterTheGraceWindow() {
        // The case single-use rotation exists to catch. A token replayed long after its rotation is
        // not a client racing itself, and it must still fail.
        String raw = "stale-replayed-token";
        RefreshToken longRotated = new RefreshToken(creds, SecurityUtils.sha256Hex(raw),
                Instant.now().plus(1, ChronoUnit.DAYS), "device");
        longRotated.revoke();
        longRotated.setUpdatedAt(Instant.now().minusSeconds(GRACE_SECONDS + 60));
        when(refreshRepo.findByToken(SecurityUtils.sha256Hex(raw)))
                .thenReturn(Optional.of(longRotated));

        assertThrows(SecurityException.class, () -> svc.rotateTokens(raw, "device", "web"),
                "a replay outside the grace window must still be refused");
    }

    // ── The session's client type survives rotation ──────────────────────────

    /**
     * The dashboard logout. For a privileged account the audience is not a label, it is whether the
     * request is authenticated at all: {@code JwtAuthenticationFilter} drops the authentication of
     * any admin/supplier principal whose token is not audience "dashboard", and
     * {@code AuthService.buildSigninResponse} refuses to sign one in under any other audience. So
     * an admin's session can only ever have been created as "dashboard" — and rotation re-derived
     * the audience from the X-Client-Type header, which defaults to "web" when absent. One refresh
     * without that header handed the admin a token the filter refuses, every following request was
     * anonymous, and the dashboard read that as a logout. On a page reload, which is precisely when
     * a refresh happens.
     */
    @Test
    void rotate_keepsTheSessionsOwnClientTypeWhenTheRequestDoesNotSayOne() {
        String raw = "dashboard-session";
        RefreshToken dashboardSession = new RefreshToken(creds, SecurityUtils.sha256Hex(raw),
                Instant.now().plus(1, ChronoUnit.DAYS), "device");
        dashboardSession.setClientType("dashboard");
        when(refreshRepo.findByToken(SecurityUtils.sha256Hex(raw)))
                .thenReturn(Optional.of(dashboardSession));

        // "web" is what the endpoint passes when the header is missing — the whole bug.
        var result = svc.rotateTokens(raw, "device", "web");

        assertEquals("dashboard", svc.getAudience(result.signInResponse().getAccessToken()),
                "an admin session must not be downgraded to an audience the filter refuses");
    }

    @Test
    void rotate_carriesTheClientTypeOntoTheNextRefreshToken() {
        // Rotation replaces the row, so the client type has to be copied forward or the very next
        // refresh is back to trusting the header.
        String raw = "dashboard-session-chained";
        RefreshToken dashboardSession = new RefreshToken(creds, SecurityUtils.sha256Hex(raw),
                Instant.now().plus(1, ChronoUnit.DAYS), "device");
        dashboardSession.setClientType("dashboard");
        when(refreshRepo.findByToken(SecurityUtils.sha256Hex(raw)))
                .thenReturn(Optional.of(dashboardSession));

        svc.rotateTokens(raw, "device", "web");

        ArgumentCaptor<RefreshToken> issued = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshRepo, atLeastOnce()).save(issued.capture());
        assertEquals("dashboard", issued.getValue().getClientType(),
                "the replacement token must inherit the session's client type");
    }

    @Test
    void rotate_fallsBackToTheRequestForSessionsIssuedBeforeClientTypeWasRecorded() {
        // Rows predating the column are null. Those keep the old header-derived behaviour rather
        // than being guessed at, and heal on the next sign-in.
        String raw = "legacy-session";
        RefreshToken legacy = new RefreshToken(creds, SecurityUtils.sha256Hex(raw),
                Instant.now().plus(1, ChronoUnit.DAYS), "device");
        assertNull(legacy.getClientType());
        when(refreshRepo.findByToken(SecurityUtils.sha256Hex(raw))).thenReturn(Optional.of(legacy));

        var result = svc.rotateTokens(raw, "device", "dashboard");

        assertEquals("dashboard", svc.getAudience(result.signInResponse().getAccessToken()));
    }

    @Test
    void issuedRefreshToken_recordsTheClientTypeItWasIssuedTo() {
        assertEquals("dashboard",
                svc.generateRefreshToken(creds, "device", "dashboard").token().getClientType());
        // An unrecognised value is not silently treated as a real client type.
        assertNull(svc.generateRefreshToken(creds, "device", "bogus").token().getClientType());
        assertNull(svc.generateRefreshToken(creds, "device").token().getClientType());
    }

    @Test
    void rotate_rejectsAnExpiredTokenEvenInsideTheGraceWindow() {
        // Expiry is not a race — it is the token's lifetime ending, and no leeway applies to it.
        String raw = "expired-token";
        RefreshToken expired = new RefreshToken(creds, SecurityUtils.sha256Hex(raw),
                Instant.now().minus(1, ChronoUnit.DAYS), "device");
        expired.revoke();
        when(refreshRepo.findByToken(SecurityUtils.sha256Hex(raw))).thenReturn(Optional.of(expired));

        assertThrows(SecurityException.class, () -> svc.rotateTokens(raw, "device", "web"));
    }

    // ── The dashboard's own cookie slot ──────────────────────────────────────────────────────────
    //
    // The dashboard, the supplier portal and the storefront all talk to one api host, so while they
    // shared the cookie NAME they shared one session: signing in to the storefront in the same
    // browser overwrote the admin's cookie, and signing out of it revoked the admin's token.

    @Test
    void refreshCookie_givesTheDashboardItsOwnSlot() {
        assertEquals(TokenService.DASHBOARD_REFRESH_TOKEN_COOKIE,
                TokenService.refreshCookieNameFor("dashboard"));
        assertEquals(TokenService.DASHBOARD_REFRESH_TOKEN_COOKIE,
                TokenService.refreshCookieNameFor("DASHBOARD"),
                "the client type arrives from a header and its case is not ours to rely on");
        assertNotEquals(TokenService.REFRESH_TOKEN_COOKIE, TokenService.DASHBOARD_REFRESH_TOKEN_COOKIE);
    }

    @Test
    void refreshCookie_leavesEveryOtherClientOnTheSharedName() {
        // Storefront and mobile sessions already in the field keep working untouched.
        assertEquals(TokenService.REFRESH_TOKEN_COOKIE, TokenService.refreshCookieNameFor("web"));
        assertEquals(TokenService.REFRESH_TOKEN_COOKIE, TokenService.refreshCookieNameFor("mobile"));
        assertEquals(TokenService.REFRESH_TOKEN_COOKIE, TokenService.refreshCookieNameFor(null));
        assertEquals(TokenService.REFRESH_TOKEN_COOKIE, TokenService.refreshCookieNameFor(""));
    }

    @Test
    void rotate_writesTheReplacementIntoTheSessionsOwnSlot() {
        // A dashboard session still holding the old shared cookie is migrated by its next rotation:
        // the audience comes from the stored row, so the Set-Cookie names the dashboard's slot even
        // though the request that carried it said nothing.
        String raw = "dashboard-session-to-migrate";
        RefreshToken dashboardSession = new RefreshToken(creds, SecurityUtils.sha256Hex(raw),
                Instant.now().plus(1, ChronoUnit.DAYS), "device");
        dashboardSession.setClientType("dashboard");
        when(refreshRepo.findByToken(SecurityUtils.sha256Hex(raw)))
                .thenReturn(Optional.of(dashboardSession));

        var result = svc.rotateTokens(raw, "device", "web");

        assertTrue(result.refreshCookieHeader()
                        .startsWith(TokenService.DASHBOARD_REFRESH_TOKEN_COOKIE + "="),
                "a dashboard session must be renewed into the dashboard's cookie, not the shared one");
    }

    @Test
    void rotate_keepsAStorefrontSessionOnTheSharedCookie() {
        String raw = "storefront-session";
        RefreshToken webSession = new RefreshToken(creds, SecurityUtils.sha256Hex(raw),
                Instant.now().plus(1, ChronoUnit.DAYS), "device");
        webSession.setClientType("web");
        when(refreshRepo.findByToken(SecurityUtils.sha256Hex(raw))).thenReturn(Optional.of(webSession));

        var result = svc.rotateTokens(raw, "device", "web");

        assertTrue(result.refreshCookieHeader().startsWith(TokenService.REFRESH_TOKEN_COOKIE + "="));
    }

    @Test
    void clearingTheCookie_onlyClearsTheClientThatAskedToSignOut() {
        // Signing out of the storefront must not clear the dashboard's cookie, or a shopper logging
        // out ends an admin's session in another tab.
        assertTrue(svc.buildClearRefreshTokenCookieString("web")
                .startsWith(TokenService.REFRESH_TOKEN_COOKIE + "="));
        assertTrue(svc.buildClearRefreshTokenCookieString("dashboard")
                .startsWith(TokenService.DASHBOARD_REFRESH_TOKEN_COOKIE + "="));
    }
}
