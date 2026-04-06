package com.buyology.ecommerce.payment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies HMAC-SHA512 validation against the sample data provided by Paymob UAE support (Apr 2026).
 *
 * Sample source:
 *   HMAC key      : C2C4F6EEA4B42B2E9B0332123D46A972
 *   Expected HMAC : 389dca274e752122d7a5b8722e6cbf26d71a5ace649f0848417f104c38b0aa8b
 *                   2b0a5caaa0f9f66aab51475c746290189dcc822d5dc53d9a9a1fd15e1059fa39
 */
class PaymobHmacValidatorTest {

    // ── Paymob sample data ───────────────────────────────────────────────────

    private static final String SAMPLE_HMAC_KEY =
            "C2C4F6EEA4B42B2E9B0332123D46A972";

    private static final String EXPECTED_HMAC =
            "389dca274e752122d7a5b8722e6cbf26d71a5ace649f0848417f104c38b0aa8b" +
            "2b0a5caaa0f9f66aab51475c746290189dcc822d5dc53d9a9a1fd15e1059fa39";

    /**
     * Full callback payload provided by Paymob in their test email.
     * Wrapped in {"obj": ...} to match the UAE Intention API format.
     */
    private static final String SAMPLE_PAYLOAD = """
            {
              "type": "TRANSACTION",
              "obj": {
                "id": 364384894,
                "pending": false,
                "amount_cents": 123000,
                "success": true,
                "is_auth": false,
                "is_capture": false,
                "is_standalone_payment": true,
                "is_voided": false,
                "is_refunded": false,
                "is_3d_secure": false,
                "integration_id": 5133859,
                "profile_id": 1000843,
                "has_parent_transaction": false,
                "order": {
                  "id": 411153756,
                  "created_at": "2025-11-03T10:47:18.435660",
                  "currency": "EGP",
                  "amount_cents": 123000
                },
                "created_at": "2025-11-03T10:47:27.721414",
                "currency": "EGP",
                "source_data": {
                  "type": "apple pay",
                  "sub_type": "APPLE_PAY",
                  "pan": "1962"
                },
                "error_occured": false,
                "owner": 1854248
              }
            }
            """;

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void validHmac_matchesPaymobSampleData() {
        assertTrue(
            PaymobHmacValidator.validate(SAMPLE_PAYLOAD, EXPECTED_HMAC, SAMPLE_HMAC_KEY),
            "HMAC should match Paymob's provided sample"
        );
    }

    @Test
    void invalidHmac_wrongSignature_returnsFalse() {
        assertFalse(
            PaymobHmacValidator.validate(SAMPLE_PAYLOAD, "000000wrongsignature", SAMPLE_HMAC_KEY),
            "Tampered HMAC should fail validation"
        );
    }

    @Test
    void invalidHmac_nullHmac_returnsFalse() {
        assertFalse(
            PaymobHmacValidator.validate(SAMPLE_PAYLOAD, null, SAMPLE_HMAC_KEY),
            "Null HMAC should fail validation"
        );
    }

    @Test
    void invalidHmac_nullSecret_returnsFalse() {
        assertFalse(
            PaymobHmacValidator.validate(SAMPLE_PAYLOAD, EXPECTED_HMAC, null),
            "Null secret should fail validation"
        );
    }

    @Test
    void concatString_matchesPaymobExpectedOrder() {
        // Concatenated message confirmed by Paymob support
        String expected = "1230002025-11-03T10:47:27.721414EGPfalsefalse364384894" +
                          "5133859falsefalsefalsefalsetruefalse4111537561854248false" +
                          "1962APPLE_PAYapple paytrue";

        com.fasterxml.jackson.databind.JsonNode txn;
        try {
            txn = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(SAMPLE_PAYLOAD)
                    .get("obj");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertEquals(expected, PaymobHmacValidator.buildConcatString(txn),
                "Concatenated string must match Paymob's documented field order exactly");
    }
}
