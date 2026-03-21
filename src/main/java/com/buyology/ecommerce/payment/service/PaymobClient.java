package com.buyology.ecommerce.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Thin HTTP wrapper for the Paymob API.
 * All three steps of the Paymob flow are encapsulated here so that
 * PaymentService stays focused on orchestration rather than HTTP mechanics.
 *
 * Paymob flow:
 *   Step 1 — POST /api/auth/tokens                  → short-lived auth token
 *   Step 2 — POST /api/ecommerce/orders             → Paymob-side order ID
 *   Step 3 — POST /api/acceptance/payment_keys      → payment key token
 */
@Component
public class PaymobClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PaymobClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Step 1 — Authenticate with Paymob and return a short-lived auth token.
     * Token is valid for ~1 hour. Cache it in Redis keyed by provider ID rather
     * than calling this on every request.
     */
    public String authenticate(String apiKey, String baseUrl) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("api_key", apiKey);

        JsonNode response = post(baseUrl + "/api/auth/tokens", body, null);
        return response.get("token").asText();
    }

    /**
     * Step 2 — Register a Paymob-side order and return the Paymob order ID.
     *
     * @param amountCents   integer cents (e.g. 10000 = 100.00 AED)
     * @param currency      ISO 4217 currency code (e.g. "AED")
     * @param items         array of order item objects; see Paymob docs for shape
     */
    public String createOrder(String authToken, String baseUrl,
                              long amountCents, String currency,
                              ArrayNode items) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("auth_token", authToken);
        body.put("delivery_needed", false);
        body.put("amount_cents", amountCents);
        body.put("currency", currency);
        body.set("items", items);

        JsonNode response = post(baseUrl + "/api/ecommerce/orders", body, null);
        return response.get("id").asText();
    }

    /**
     * Step 3 — Generate a payment key for a specific integration (method type).
     * The returned token is short-lived (~1 hour) and is what the frontend
     * passes to the Paymob iframe or redirect URL.
     *
     * @param integrationId  Paymob integration ID for the chosen method (CARD/TABBY/TAMARA)
     * @param billingData    map of billing address fields required by Paymob
     */
    public String generatePaymentKey(String authToken, String baseUrl,
                                     String paymobOrderId, long amountCents,
                                     String currency, String integrationId,
                                     String customerEmail, Map<String, String> billingData) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("auth_token", authToken);
        body.put("amount_cents", amountCents);
        body.put("expiration", 3600);
        body.put("order_id", paymobOrderId);
        body.put("currency", currency);
        body.put("integration_id", Integer.parseInt(integrationId));

        ObjectNode billing = objectMapper.createObjectNode();
        billing.put("apartment", billingData.getOrDefault("apartment", "NA"));
        billing.put("email", customerEmail);
        billing.put("floor", billingData.getOrDefault("floor", "NA"));
        billing.put("first_name", billingData.getOrDefault("first_name", "NA"));
        billing.put("street", billingData.getOrDefault("street", "NA"));
        billing.put("building", billingData.getOrDefault("building", "NA"));
        billing.put("phone_number", billingData.getOrDefault("phone_number", "NA"));
        billing.put("shipping_method", "PKG");
        billing.put("postal_code", billingData.getOrDefault("postal_code", "NA"));
        billing.put("city", billingData.getOrDefault("city", "NA"));
        billing.put("country", billingData.getOrDefault("country", "NA"));
        billing.put("last_name", billingData.getOrDefault("last_name", "NA"));
        billing.put("state", billingData.getOrDefault("state", "NA"));
        body.set("billing_data", billing);

        JsonNode response = post(baseUrl + "/api/acceptance/payment_keys", body, null);
        return response.get("token").asText();
    }

    /**
     * Submit a refund request to Paymob for a completed transaction.
     *
     * @param paymobTransactionId  Paymob's transaction ID (from webhook)
     * @param amountCents          amount to refund in integer cents
     * @return Paymob refund transaction ID
     */
    public String refund(String authToken, String baseUrl,
                         String paymobTransactionId, long amountCents) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("auth_token", authToken);
        body.put("transaction_id", paymobTransactionId);
        body.put("amount_cents", amountCents);

        JsonNode response = post(baseUrl + "/api/acceptance/void_refund/refund", body, null);
        return response.get("id").asText();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private JsonNode post(String url, ObjectNode body, String bearerToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (bearerToken != null) {
                headers.setBearerAuth(bearerToken);
            }
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Paymob API call failed for URL " + url + ": " + e.getMessage(), e);
        }
    }
}
