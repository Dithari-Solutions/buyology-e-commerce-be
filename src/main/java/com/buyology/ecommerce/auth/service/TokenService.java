package com.buyology.ecommerce.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.domain.RefreshToken;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.auth.repository.RefreshTokenRepository;

import java.nio.charset.StandardCharsets;

@Service
public class TokenService {

    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final RefreshTokenRepository refreshTokenRepository;
    private final String secret;
    private final long accessTokenValidityMinutes;
    private final long refreshTokenValidityDays;
    private final boolean cookieSecure;

    public TokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-minutes}") long accessTokenValidityMinutes,
            @Value("${jwt.refresh-token-validity-days}") long refreshTokenValidityDays,
            @Value("${cookie.secure:true}") boolean cookieSecure) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.secret = secret;
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
    // Generate access token (JWT — manual HMAC-SHA256)
    // ---------------------------
    public String generateAccessToken(AuthCredentials authCredentials) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

        Instant now = Instant.now();
        Instant exp = now.plus(accessTokenValidityMinutes, ChronoUnit.MINUTES);
        String payloadJson = String.format("{\"sub\":\"%s\",\"iat\":%d,\"exp\":%d}",
                authCredentials.getId(),
                now.getEpochSecond(),
                exp.getEpochSecond());

        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        String signature = sign(header + "." + payload, secret);
        return String.format("%s.%s.%s", header, payload, signature);
    }

    private String sign(String data, String secretKey) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(hmac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Error signing token", e);
        }
    }

    // ---------------------------
    // Validate access token
    // ---------------------------
    public boolean validateAccessToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return false;

            if (!sign(parts[0] + "." + parts[1], secret).equals(parts[2])) return false;

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            long exp = Long.parseLong(payloadJson.replaceAll(".*\"exp\":(\\d+).*", "$1"));
            return Instant.now().getEpochSecond() < exp;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------
    // Persist refresh token
    // ---------------------------
    public RefreshToken generateRefreshToken(AuthCredentials authCredentials, String deviceInfo) {
        String tokenValue = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plus(refreshTokenValidityDays, ChronoUnit.DAYS);
        return refreshTokenRepository.save(new RefreshToken(authCredentials, tokenValue, expiry, deviceInfo));
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
        RefreshToken existing = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new SecurityException("Invalid refresh token"));

        if (existing.isRevoked() || existing.isExpired()) {
            throw new SecurityException("Refresh token expired or revoked");
        }

        existing.revoke();
        refreshTokenRepository.save(existing);

        AuthCredentials creds = existing.getAuthCredential();
        String newAccessToken = generateAccessToken(creds);
        RefreshToken newRefreshToken = generateRefreshToken(creds, deviceInfo);

        return new RotateTokensResult(
                new SignInResponse(newAccessToken, getAccessTokenExpirySeconds()),
                buildRefreshTokenCookieString(newRefreshToken.getToken())
        );
    }

    // ---------------------------
    // Revoke refresh token (logout)
    // ---------------------------
    public void revokeRefreshToken(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
        });
    }
}
