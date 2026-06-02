package com.buyology.ecommerce.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/** Ensures the app refuses to boot with a missing/weak/compromised JWT secret. */
class JwtSecretValidatorTest {

    private static final String LEAKED =
            "4b8c2c2d7c0c4b7f5d91e9d4c83e1a4f6c7d3a9b5e2f1c0d8a6b7e4f9c2d1a3e";

    private JwtSecretValidator withSecret(String secret) {
        JwtSecretValidator v = new JwtSecretValidator();
        ReflectionTestUtils.setField(v, "jwtSecret", secret);
        return v;
    }

    @Test
    void rejectsBlankSecret() {
        assertThrows(IllegalStateException.class, () -> withSecret("").validate());
        assertThrows(IllegalStateException.class, () -> withSecret(null).validate());
    }

    @Test
    void rejectsTooShortSecret() {
        assertThrows(IllegalStateException.class, () -> withSecret("short-secret").validate());
    }

    @Test
    void rejectsTheLeakedCommittedSecret() {
        assertThrows(IllegalStateException.class, () -> withSecret(LEAKED).validate());
        assertThrows(IllegalStateException.class, () -> withSecret("  " + LEAKED + "  ").validate());
    }

    @Test
    void acceptsAStrongSecret() {
        assertDoesNotThrow(() -> withSecret("a-fresh-strong-random-secret-of-good-length-123456").validate());
    }
}
