package com.buyology.ecommerce.courier;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Component
public class CourierServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(CourierServiceTokenProvider.class);

    private final SecretKey key;
    private final String issuer;
    private final long expirySeconds;

    public CourierServiceTokenProvider(
            @Value("${courier.service.jwt.secret}") String secret,
            @Value("${courier.service.jwt.issuer:buyology-ecommerce-service}") String issuer,
            @Value("${courier.service.jwt.expiry-seconds:60}") long expirySeconds
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("courier.service.jwt.secret must be set");
        }
        this.key           = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer        = issuer;
        this.expirySeconds = expirySeconds;
    }

    /**
     * Generates a short-lived HMAC-signed service JWT per request.
     * The courier service validates this token with the same shared secret.
     */
    public String generateServiceToken() {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .issuer(issuer)
                .subject("ecommerce-backend")
                .claim("roles", List.of("COURIER_ADMIN"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirySeconds)))
                .signWith(key)
                .compact();

        // Decode and log the payload to verify iss/sub/roles are present
        try {
            String[] parts = token.split("\\.");
            String decodedPayload = new String(Base64.getUrlDecoder().decode(parts[1]));
            log.info("[COURIER-TOKEN] Generated service JWT payload: {}", decodedPayload);
        } catch (Exception e) {
            log.warn("[COURIER-TOKEN] Could not decode generated token for logging: {}", e.getMessage());
        }

        return token;
    }
}
