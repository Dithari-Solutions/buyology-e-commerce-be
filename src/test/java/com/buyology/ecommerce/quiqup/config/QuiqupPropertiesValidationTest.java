package com.buyology.ecommerce.quiqup.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins that signature enforcement cannot be switched on without a secret to enforce with.
 *
 * <p>That pairing was the most dangerous configuration the module offered. Verification cannot run
 * without the secret, so {@code hmacValid} was null rather than false, the rejection test read
 * {@code Boolean.FALSE.equals(null)} — which is false — and every forged webhook was accepted with
 * a 200 while the admin config page reported enforcement as on. The failure was silent in exactly
 * the configuration where an operator is most confident they are protected.
 *
 * <p>Failing at startup is the posture the JWT-secret and CORS-allowlist checks already take.
 */
class QuiqupPropertiesValidationTest {

    private static QuiqupProperties props(boolean enabled, boolean require, String secret) {
        QuiqupProperties p = new QuiqupProperties();
        p.setEnabled(enabled);
        p.setWebhookRequireSignature(require);
        p.setWebhookSecret(secret);
        return p;
    }

    @Test
    void refusesToStartWhenEnforcementIsOnWithNoSecret() {
        IllegalStateException blank = assertThrows(IllegalStateException.class,
                () -> props(true, true, "").validate());
        assertTrue(blank.getMessage().contains("QUIQUP_WEBHOOK_SECRET"),
                "the message must name the variable to set");

        assertThrows(IllegalStateException.class, () -> props(true, true, null).validate());
        assertThrows(IllegalStateException.class, () -> props(true, true, "   ").validate());
    }

    @Test
    void startsWhenEnforcementIsOnAndASecretIsPresent() {
        assertDoesNotThrow(() -> props(true, true, "a-real-webhook-secret").validate());
    }

    @Test
    void startsWhenEnforcementIsOff() {
        // The shipped default: observe signatures before being allowed to drop deliveries.
        assertDoesNotThrow(() -> props(true, false, "").validate());
    }

    @Test
    void aDeploymentWithQuiqupOffIsUnaffected() {
        // Most deployments never set a Quiqup variable; they must not be made to fail boot by a
        // check about a module they do not run.
        assertDoesNotThrow(() -> props(false, true, "").validate());
    }
}
