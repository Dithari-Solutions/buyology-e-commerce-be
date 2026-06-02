package com.buyology.ecommerce.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Fails application startup if the JWT signing secret is missing, too weak, or
 * matches the value that was previously committed to source control. This makes
 * it impossible to run with a public/guessable secret (which would let anyone
 * forge admin tokens).
 */
@Configuration
public class JwtSecretValidator {

    // The secret that used to ship as a default in application.properties.
    // It is now considered compromised and is rejected outright.
    private static final String LEAKED_SECRET =
            "4b8c2c2d7c0c4b7f5d91e9d4c83e1a4f6c7d3a9b5e2f1c0d8a6b7e4f9c2d1a3e";

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @PostConstruct
    void validate() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Provide a strong, random secret of at least 32 characters.");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short (" + jwtSecret.length() + " chars). Use at least 32 characters.");
        }
        if (LEAKED_SECRET.equals(jwtSecret.trim())) {
            throw new IllegalStateException(
                    "JWT_SECRET matches the previously committed (compromised) value. Rotate it immediately.");
        }
    }
}
