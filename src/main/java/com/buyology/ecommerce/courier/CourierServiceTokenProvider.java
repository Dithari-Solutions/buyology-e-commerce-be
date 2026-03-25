package com.buyology.ecommerce.courier;

import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Generates short-lived RS256-signed JWTs for service-to-service calls to the
 * courier service. The courier service validates these tokens with the
 * corresponding RSA public key — no shared secret required.
 *
 * Set COURIER_SERVICE_PRIVATE_KEY to the full PEM content of courier-private.pem.
 */
@Component
public class CourierServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(CourierServiceTokenProvider.class);

    private final RSAPrivateKey privateKey;
    private final String issuer;
    private final long expirySeconds;

    public CourierServiceTokenProvider(
            @Value("${COURIER_SERVICE_PRIVATE_KEY:}") String privateKeyPem,
            @Value("${courier.service.jwt.issuer:buyology-ecommerce-service}") String issuer,
            @Value("${courier.service.jwt.expiry-seconds:60}") long expirySeconds
    ) {
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            throw new IllegalStateException(
                    "courier.service.jwt.private-key must be set (set COURIER_SERVICE_PRIVATE_KEY env var)");
        }
        this.privateKey    = parsePrivateKey(privateKeyPem);
        this.issuer        = issuer;
        this.expirySeconds = expirySeconds;
        log.info("[COURIER-TOKEN] RS256 token provider initialised (issuer={})", issuer);
    }

    /**
     * Generates a short-lived RS256-signed JWT identifying the acting admin.
     *
     * @param adminId the ecommerce-backend user ID of the admin making the call
     *                (embedded as JWT subject for courier-service audit logging)
     */
    public String generateToken(String adminId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(adminId)
                .claim("roles", List.of("COURIER_ADMIN"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirySeconds)))
                .signWith(privateKey)   // JJWT auto-selects RS256 for RSA private keys
                .compact();
    }

    // ── private ───────────────────────────────────────────────────────────────

    /**
     * Accepts two formats:
     *  1. Raw PKCS8 PEM  (starts with "-----BEGIN")
     *  2. Base64-encoded PEM (no newlines) — used by CI/CD to safely pass
     *     multiline values through shell env vars
     */
    private static RSAPrivateKey parsePrivateKey(String input) {
        try {
            String trimmed = input.trim();

            // If not a PEM header, assume the whole value is base64-encoded PEM
            String pem = trimmed.startsWith("-----")
                    ? trimmed
                    : new String(Base64.getDecoder().decode(trimmed));

            String cleaned = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(cleaned);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse COURIER_SERVICE_PRIVATE_KEY — ensure it is a PKCS8 PEM " +
                    "(raw or base64-encoded)", e);
        }
    }
}
