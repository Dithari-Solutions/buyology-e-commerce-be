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

    /**
     * The dashboard's own refresh-token cookie.
     *
     * <p>A cookie is a single slot per name per host, and the dashboard, the supplier portal, the
     * storefront and the mobile web view all talk to the SAME api host. While they shared the name
     * {@code refresh_token} they shared one session: an admin who signed in to the storefront in the
     * same browser OVERWROTE their own dashboard cookie, and a storefront sign-out revoked the token
     * and cleared the cookie — ending the dashboard session from another tab, with no action in the
     * dashboard at all. That is invisible from inside the dashboard and looks exactly like "it logs
     * me out by itself".
     *
     * <p>Only the dashboard is given a separate name. The storefront and mobile keep
     * {@code refresh_token} so sessions already in the field are untouched by this change; a
     * dashboard session holding the old name is migrated on its next rotation, because
     * {@code AuthController} reads both and writes the one that belongs to the caller.
     */
    public static final String DASHBOARD_REFRESH_TOKEN_COOKIE = "refresh_token_dashboard";

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
    /** How long after a rotation the superseded token is still accepted. See rotateTokens. */
    private final long refreshReuseGraceSeconds;

    public TokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRoleRepository userRoleRepository,
            RolePermissionRepository rolePermissionRepository,
            UserPermissionRepository userPermissionRepository,
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer:buyology-ecommerce-service}") String issuer,
            @Value("${jwt.access-token-validity-minutes}") long accessTokenValidityMinutes,
            @Value("${jwt.refresh-token-validity-days}") long refreshTokenValidityDays,
            @Value("${cookie.secure:true}") boolean cookieSecure,
            @Value("${jwt.refresh-reuse-grace-seconds:30}") long refreshReuseGraceSeconds) {
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
        this.refreshReuseGraceSeconds = refreshReuseGraceSeconds;
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
        return generateRefreshToken(authCredentials, deviceInfo, null);
    }

    /**
     * Issues a refresh token that remembers which client it was issued to.
     *
     * <p>The audience is stamped on the row so {@link #rotateTokens} can re-mint access tokens for
     * the SAME client without asking the request again — see the note there.
     */
    public IssuedRefreshToken generateRefreshToken(AuthCredentials authCredentials, String deviceInfo,
                                                   String audience) {
        String rawValue = newOpaqueToken();
        String hashed = SecurityUtils.sha256Hex(rawValue);
        Instant expiry = Instant.now().plus(refreshTokenValidityDays, ChronoUnit.DAYS);
        RefreshToken token = new RefreshToken(authCredentials, hashed, expiry, deviceInfo);
        token.setClientType(normalizeAudience(audience));
        RefreshToken saved = refreshTokenRepository.save(token);
        return new IssuedRefreshToken(saved, rawValue);
    }

    /**
     * The canonical spelling of a client type, or null when there isn't one.
     *
     * <p>Null is meaningful and is NOT collapsed to "web": it records that the session never said
     * which client it belongs to, which is what lets rotation tell "this is a storefront session"
     * apart from "nobody ever said", and treat only the second as open to the header.
     */
    private static String normalizeAudience(String audience) {
        if (audience == null || audience.isBlank()) {
            return null;
        }
        String normalized = audience.trim().toLowerCase();
        return switch (normalized) {
            case "dashboard", "web", "mobile" -> normalized;
            default -> null;
        };
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
     * The cookie name a given client's refresh token belongs in.
     *
     * <p>See {@link #DASHBOARD_REFRESH_TOKEN_COOKIE} for why the dashboard needs its own slot.
     */
    public static String refreshCookieNameFor(String audience) {
        return "dashboard".equalsIgnoreCase(audience == null ? "" : audience.trim())
                ? DASHBOARD_REFRESH_TOKEN_COOKIE
                : REFRESH_TOKEN_COOKIE;
    }

    /**
     * Returns the Set-Cookie header value for a new refresh token.
     * HttpOnly=true, Path=/auth/refresh, MaxAge=jwt.refresh-token-validity-days.
     * Secure flag is driven by the cookie.secure property (false in dev, true in prod).
     */
    public String buildRefreshTokenCookieString(String tokenValue) {
        return buildRefreshTokenCookieString(tokenValue, null);
    }

    /** As above, in the cookie slot belonging to {@code audience} — see {@link #refreshCookieNameFor}. */
    public String buildRefreshTokenCookieString(String tokenValue, String audience) {
        return ResponseCookie.from(refreshCookieNameFor(audience), tokenValue)
                .httpOnly(true)
                .secure(cookieSecure)
                // Frontends live on different subdomains (buyology.online / admin. /
                // supplier.) than the API (api.buyology.online), so the refresh cookie
                // must cross origins on a credentialed XHR. SameSite=None (Secure)
                // allows that in prod; Strict/Lax silently drop it for some browsers
                // (notably Safari) and after OAuth redirects. Dev is insecure, where
                // None is rejected, so fall back to Lax there.
                .sameSite(cookieSecure ? "None" : "Lax")
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
        return buildClearRefreshTokenCookieString(null);
    }

    /**
     * Clears only the cookie belonging to {@code audience}.
     *
     * <p>Scoped deliberately: clearing every slot would make a storefront sign-out end the admin's
     * dashboard session, which is the behaviour the split cookie exists to stop.
     */
    public String buildClearRefreshTokenCookieString(String audience) {
        return ResponseCookie.from(refreshCookieNameFor(audience), "")
                .httpOnly(true)
                .secure(cookieSecure)
                // Frontends live on different subdomains (buyology.online / admin. /
                // supplier.) than the API (api.buyology.online), so the refresh cookie
                // must cross origins on a credentialed XHR. SameSite=None (Secure)
                // allows that in prod; Strict/Lax silently drop it for some browsers
                // (notably Safari) and after OAuth redirects. Dev is insecure, where
                // None is rejected, so fall back to Lax there.
                .sameSite(cookieSecure ? "None" : "Lax")
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

        if (existing.isExpired()) {
            throw new SecurityException("Refresh token expired or revoked");
        }

        if (existing.isRevoked()) {
            // A token presented after it was already rotated. Strictly single-use rotation treated
            // this as theft and threw, which logged people out constantly for an entirely innocent
            // reason: one page load fires several API calls at once, they all get a 401 on the same
            // expired access token, and they all then present the SAME refresh cookie. The first
            // rotation revokes it and the rest were rejected — so a simple refresh signed the user
            // out of both the storefront and the dashboard.
            //
            // Age separates the two cases. A browser's parallel requests arrive within milliseconds
            // of each other, so a token reused seconds after its rotation is the same client racing
            // itself. A token replayed minutes or days later is the case single-use rotation exists
            // to catch, and that still fails.
            //
            // updatedAt is the revocation time: revoke() stamps it, and nothing updates a token
            // after it is revoked.
            Instant revokedAt = existing.getUpdatedAt();
            boolean withinGrace = revokedAt != null
                    && revokedAt.isAfter(Instant.now().minusSeconds(refreshReuseGraceSeconds));
            if (!withinGrace) {
                throw new SecurityException("Refresh token expired or revoked");
            }
            // Fall through and issue a fresh pair. Deliberately NOT re-revoking: re-stamping
            // updatedAt on every reuse would slide the window forward indefinitely, so the grace
            // period is measured once from the original rotation and cannot be extended.
            log.debug("[AUTH] Refresh token reused {}ms after rotation — treating as a concurrent "
                            + "refresh rather than a replay",
                    Duration.between(revokedAt, Instant.now()).toMillis());
        } else {
            existing.revoke();
            refreshTokenRepository.save(existing);
        }

        AuthCredentials creds = existing.getAuthCredential();

        // Which client this session belongs to is a property OF THE SESSION, not of the request
        // doing the refresh. Taking it from X-Client-Type on every call is what signed admins out
        // of the dashboard: the audience decides whether a privileged account is authenticated at
        // all (JwtAuthenticationFilter drops any admin/supplier principal whose token is not
        // audience "dashboard"), the header defaults to "web" when absent, and a page reload's
        // refresh that omitted it therefore re-minted a working admin token as one the filter
        // rejects. Every later request was anonymous, which the dashboard reads as a logout.
        //
        // The stored value wins, so rotation cannot change what the session is. The header is
        // consulted only for sessions issued before that value was recorded, which heal on the
        // next sign-in.
        String effectiveAudience = existing.getClientType() != null
                ? existing.getClientType()
                : audience;

        String newAccessToken = generateAccessToken(creds, effectiveAudience);
        IssuedRefreshToken newRefreshToken = generateRefreshToken(creds, deviceInfo, effectiveAudience);

        return new RotateTokensResult(
                new SignInResponse(newAccessToken, getAccessTokenExpirySeconds()),
                buildRefreshTokenCookieString(newRefreshToken.rawValue(), effectiveAudience)
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
