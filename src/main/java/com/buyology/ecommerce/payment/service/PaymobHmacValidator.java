package com.buyology.ecommerce.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Stateless HMAC-SHA512 validator for Paymob webhook callbacks.
 *
 * Field order confirmed by Paymob UAE support (Apr 2026):
 *   amount_cents, created_at, currency, error_occured, has_parent_transaction,
 *   id, integration_id, is_3d_secure, is_auth, is_capture, is_refunded,
 *   is_standalone_payment, is_voided, order.id, owner, pending,
 *   source_data.pan, source_data.sub_type, source_data.type, success
 *
 * UAE Intention API wraps the transaction under {"obj": {...}, "type": "TRANSACTION"}.
 */
class PaymobHmacValidator {

    private static final Logger log = LoggerFactory.getLogger(PaymobHmacValidator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PaymobHmacValidator() {}

    /**
     * Returns true when the received HMAC matches the one computed from the payload.
     */
    static boolean validate(String rawPayload, String receivedHmac, String hmacSecret) {
        if (receivedHmac == null || hmacSecret == null || hmacSecret.isBlank()) {
            log.warn("[HMAC] Missing HMAC value or secret — rejecting");
            return false;
        }
        try {
            JsonNode root = MAPPER.readTree(rawPayload);
            JsonNode txn = root.has("obj") ? root.get("obj") : root;

            String concat = buildConcatString(txn);

            // Attempt SHA-512 first (standard for modern Paymob UAE Transaction Callbacks)
            String computed512 = computeHmac(concat, hmacSecret, "HmacSHA512");
            if (computed512.equalsIgnoreCase(receivedHmac)) {
                return true;
            }

            // Fallback to SHA-256 (used by some older merchant accounts or specific intention callbacks)
            String computed256 = computeHmac(concat, hmacSecret, "HmacSHA256");
            if (computed256.equalsIgnoreCase(receivedHmac)) {
                log.info("[HMAC] Validated using SHA-256 fallback");
                return true;
            }

            log.warn("[HMAC] Mismatch — received: {}, computed512: {}, computed256: {}", 
                     receivedHmac, computed512, computed256);
            log.debug("[HMAC] Concatenated string used: {}", concat);
            
            return false;
        } catch (Exception e) {
            log.error("[HMAC] Validation error: {}", e.getMessage());
            return false;
        }
    }

    private static String computeHmac(String data, String key, String algorithm) throws Exception {
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : raw) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    static String buildConcatString(JsonNode txn) {
        JsonNode src   = txn.has("source_data") && !txn.get("source_data").isNull() ? txn.get("source_data") : null;
        JsonNode order = txn.has("order")       && !txn.get("order").isNull()       ? txn.get("order")       : null;
        return str(txn, "amount_cents")
             + str(txn, "created_at")
             + str(txn, "currency")
             + str(txn, "error_occured")
             + str(txn, "has_parent_transaction")
             + str(txn, "id")
             + str(txn, "integration_id")
             + str(txn, "is_3d_secure")
             + str(txn, "is_auth")
             + str(txn, "is_capture")
             + str(txn, "is_refunded")
             + str(txn, "is_standalone_payment")
             + str(txn, "is_voided")
             + (order != null ? str(order, "id") : "")
             + str(txn, "owner")
             + str(txn, "pending")
             + (src != null ? str(src, "pan")      : "")
             + (src != null ? str(src, "sub_type")  : "")
             + (src != null ? str(src, "type")      : "")
             + str(txn, "success");
    }

    private static String str(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return "";
        return node.get(field).asText();
    }
}
