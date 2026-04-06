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

            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] raw = mac.doFinal(concat.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : raw) hex.append(String.format("%02x", b));
            String computed = hex.toString();

            boolean valid = computed.equalsIgnoreCase(receivedHmac);
            if (!valid) {
                log.warn("[HMAC] Mismatch — computed: {}, received: {}", computed, receivedHmac);
            }
            return valid;
        } catch (Exception e) {
            log.error("[HMAC] Validation error: {}", e.getMessage());
            return false;
        }
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
