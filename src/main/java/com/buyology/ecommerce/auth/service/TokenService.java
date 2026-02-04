package com.buyology.ecommerce.auth.service;

import java.util.UUID;
import java.util.Base64;
import javax.crypto.Mac;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import com.buyology.ecommerce.auth.domain.RefreshToken;
import com.buyology.ecommerce.auth.domain.AuthCredentials;
import org.springframework.beans.factory.annotation.Value;
import com.buyology.ecommerce.auth.repository.RefreshTokenRepository;

@Service
public class TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final String secret;
    private final long accessTokenValidityMinutes;
    private final long refreshTokenValidityDays;

    public TokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-minutes}") long accessTokenValidityMinutes,
            @Value("${jwt.refresh-token-validity-days}") long refreshTokenValidityDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.secret = secret;
        this.accessTokenValidityMinutes = accessTokenValidityMinutes;
        this.refreshTokenValidityDays = refreshTokenValidityDays;
    }

    // ---------------------------
    // Get Access Token Expiry Seconds
    // ---------------------------

    // Optional getter for frontend
    public long getAccessTokenExpirySeconds() {
        return accessTokenValidityMinutes * 60;
    }

    public long getRefreshTokenValidityDays() {
        return refreshTokenValidityDays;
    }

    // ---------------------------
    // Generate access token manually
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

    // ---------------------------
    // Sign payload with HMAC-SHA256
    // ---------------------------
    private String sign(String data, String secretKey) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signatureBytes = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error signing token", e);
        }
    }

    // ---------------------------
    // Validate access token manually
    // ---------------------------
    public boolean validateAccessToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3)
                return false;

            String signatureCheck = sign(parts[0] + "." + parts[1], secret);
            if (!signatureCheck.equals(parts[2]))
                return false;

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            long exp = Long.parseLong(payloadJson.replaceAll(".*\"exp\":(\\d+).*", "$1"));
            return Instant.now().getEpochSecond() < exp;

        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------
    // Generate refresh token (UUID)
    // ---------------------------
    public RefreshToken generateRefreshToken(AuthCredentials authCredentials) {
        String tokenValue = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plus(refreshTokenValidityDays, ChronoUnit.DAYS);

        RefreshToken refreshToken = new RefreshToken(authCredentials, tokenValue, expiry);
        return refreshTokenRepository.save(refreshToken);
    }

    // ---------------------------
    // Refresh access token using refresh token
    // ---------------------------
    public String refreshAccessToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (refreshToken.isRevoked() || refreshToken.isExpired()) {
            throw new RuntimeException("Refresh token expired or revoked");
        }

        return generateAccessToken(refreshToken.getAuthCredential());
    }

    // ---------------------------
    // Revoke refresh token
    // ---------------------------
    public void revokeRefreshToken(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
        });
    }

}
