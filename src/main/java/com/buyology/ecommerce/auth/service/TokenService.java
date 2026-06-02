package com.buyology.ecommerce.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.domain.RefreshToken;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.auth.repository.RefreshTokenRepository;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.role.domain.UserPermission;
import com.buyology.ecommerce.role.repository.RolePermissionRepository;
import com.buyology.ecommerce.role.repository.UserPermissionRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final SecretKey signingKey;
    private final String issuer;
    private final long accessTokenValidityMinutes;
    private final long refreshTokenValidityDays;
    private final boolean cookieSecure;

    public TokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRoleRepository userRoleRepository,
            RolePermissionRepository rolePermissionRepository,
            UserPermissionRepository userPermissionRepository,
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer:buyology-ecommerce-service}") String issuer,
            @Value("${jwt.access-token-validity-minutes}") long accessTokenValidityMinutes,
            @Value("${jwt.refresh-token-validity-days}") long refreshTokenValidityDays,
            @Value("${cookie.secure:true}") boolean cookieSecure) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userPermissionRepository = userPermissionRepository;
        // HS256 signing key. JwtSecretValidator guarantees the secret is >= 32 chars
        // (>= 256 bits), which Keys.hmacShaKeyFor requires.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTokenValidityMinutes = accessTokenValidityMinutes;
        this.refreshTokenValidityDays = refreshTokenValidityDays;
        this.cookieSecure = cookieSecure;
    }

    // ---------------------------
    // Expiry helpers
    // ---------------------------

    public long getAccessTokenExpirySeconds() {
        return accessTokenValidityMinutes * 60;
    }

    // ---------------------------
    // Generate access token (JWT — HS256, signed with jjwt)
    // ---------------------------
    public String generateAccessToken(AuthCredentials authCredentials) {
        return generateAccessToken(authCredentials, "web");
    }

    public String generateAccessToken(AuthCredentials authCredentials, String audience) {
        String safeAudience = (audience == null || audience.isBlank()) ? "web" : audience;

        Instant now = Instant.now();
        Instant exp = now.plus(accessTokenValidityMinutes, ChronoUnit.MINUTES);

        UUID userId = authCredentials.getUserId();

        // Fetch assigned role names and IDs
        List<String> roleNames = userRoleRepository.findRoleNamesByUserId(userId);
        List<UUID> roleIds = userRoleRepository.findRoleIdsByUserId(userId);

        // Start with permissions granted by the assigned roles
        Set<String> effectivePermissions = new HashSet<>();
        if (!roleIds.isEmpty()) {
            effectivePermissions.addAll(rolePermissionRepository.findPermissionCodesByRoleIds(roleIds));
        }

        // Apply direct ALLOW / DENY overrides
        List<UserPermission> overrides = userPermissionRepository.findWithPermissionByUserId(userId);
        for (UserPermission override : overrides) {
            if (override.getEffect() == UserPermission.Effect.ALLOW) {
                effectivePermissions.add(override.getPermission().getCode());
            } else if (override.getEffect() == UserPermission.Effect.DENY) {
                effectivePermissions.remove(override.getPermission().getCode());
            }
        }

        return Jwts.builder()
                .issuer(issuer)
                .audience().add(safeAudience).and()
                .subject(authCredentials.getId().toString())
                .claim("uid", userId.toString())
                .claim("roles", List.copyOf(roleNames))
                .claim("permissions", List.copyOf(effectivePermissions))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    // ---------------------------
    // Validate / parse access token (jjwt — verifies signature, expiry, issuer)
    // ---------------------------
    public boolean validateAccessToken(String token) {
        return parseClaims(token) != null;
    }

    /**
     * Returns the authenticated subject (the AuthCredentials id) from a valid token, or null.
     */
    public UUID getAuthCredentialId(String token) {
        Claims claims = parseClaims(token);
        if (claims == null) return null;
        try {
            return UUID.fromString(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the audience claim from a valid token, or "web" when absent/invalid.
     */
    public String getAudience(String token) {
        Claims claims = parseClaims(token);
        if (claims == null) return "web";
        Set<String> aud = claims.getAudience();
        if (aud == null || aud.isEmpty()) return "web";
        return aud.iterator().next();
    }

    /**
     * Parses and fully validates the token (signature, expiry, issuer). Returns the
     * claims on success, or null on any validation failure. Never logs token material.
     */
    private Claims parseClaims(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token);
            return jws.getPayload();
        } catch (Exception e) {
            log.warn("[JWT] Access token validation failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    // ---------------------------
    // Persist refresh token (opaque, random, stored hashed)
    // ---------------------------

    /**
     * Holds a freshly issued refresh token: the persisted entity (with the HASH stored)
     * and the RAW value that must be handed to the client (and never persisted).
     */
    public record IssuedRefreshToken(RefreshToken token, String rawValue) {}

    public IssuedRefreshToken generateRefreshToken(AuthCredentials authCredentials, String deviceInfo) {
        String rawValue = newOpaqueToken();
        String hashed = SecurityUtils.sha256Hex(rawValue);
        Instant expiry = Instant.now().plus(refreshTokenValidityDays, ChronoUnit.DAYS);
        RefreshToken saved = refreshTokenRepository.save(
                new RefreshToken(authCredentials, hashed, expiry, deviceInfo));
        return new IssuedRefreshToken(saved, rawValue);
    }

    private static String newOpaqueToken() {
        byte[] bytes = new byte[32]; // 256 bits of entropy
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ---------------------------
    // Cookie string builders
    // These return the full Set-Cookie header value as a String.
    // The caller includes it in a ResponseEntity via .header(HttpHeaders.SET_COOKIE, ...).
    // ---------------------------

    /**
     * Returns the Set-Cookie header value for a new refresh token.
     * HttpOnly=true, Path=/auth/refresh, MaxAge=7d.
     * Secure flag is driven by the cookie.secure property (false in dev, true in prod).
     */
    public String buildRefreshTokenCookieString(String tokenValue) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, tokenValue)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/auth/refresh")
                .maxAge(Duration.ofDays(refreshTokenValidityDays))
                .build()
                .toString();
    }

    /**
     * Returns a Set-Cookie header value that immediately expires the refresh token cookie.
     * Used by /auth/logout to force the browser to delete it.
     */
    public String buildClearRefreshTokenCookieString() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/auth/refresh")
                .maxAge(0)
                .build()
                .toString();
    }

    // ---------------------------
    // Token rotation for /auth/refresh
    // ---------------------------

    /**
     * Result of a token rotation.
     * The controller puts the cookie string in the ResponseEntity via ResponseEntity.ok()
     * .header(HttpHeaders.SET_COOKIE, result.refreshCookieHeader()).body(...).
     */
    public record RotateTokensResult(SignInResponse signInResponse, String refreshCookieHeader) {}

    /**
     * Validates the incoming refresh token, revokes it (one-time use), issues a fresh pair,
     * and returns both the new access token response and the new cookie header string.
     *
     * @throws SecurityException when the token is invalid, expired, or already revoked
     */
    public RotateTokensResult rotateTokens(String refreshTokenValue, String deviceInfo) {
        return rotateTokens(refreshTokenValue, deviceInfo, "web");
    }

    public RotateTokensResult rotateTokens(String refreshTokenValue, String deviceInfo, String audience) {
        String hashed = SecurityUtils.sha256Hex(refreshTokenValue);
        RefreshToken existing = refreshTokenRepository.findByToken(hashed)
                .orElseThrow(() -> new SecurityException("Invalid refresh token"));

        if (existing.isRevoked() || existing.isExpired()) {
            throw new SecurityException("Refresh token expired or revoked");
        }

        existing.revoke();
        refreshTokenRepository.save(existing);

        AuthCredentials creds = existing.getAuthCredential();
        String newAccessToken = generateAccessToken(creds, audience);
        IssuedRefreshToken newRefreshToken = generateRefreshToken(creds, deviceInfo);

        return new RotateTokensResult(
                new SignInResponse(newAccessToken, getAccessTokenExpirySeconds()),
                buildRefreshTokenCookieString(newRefreshToken.rawValue())
        );
    }

    // ---------------------------
    // Revoke refresh token (logout)
    // ---------------------------
    public void revokeRefreshToken(String refreshTokenValue) {
        String hashed = SecurityUtils.sha256Hex(refreshTokenValue);
        refreshTokenRepository.findByToken(hashed).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
        });
    }
}
