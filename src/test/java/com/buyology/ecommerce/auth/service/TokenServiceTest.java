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
}
