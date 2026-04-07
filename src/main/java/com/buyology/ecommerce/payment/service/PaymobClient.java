package com.buyology.ecommerce.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Thin HTTP wrapper for the Paymob Intention API (v2).
 *
 * Authentication: every request carries the Secret Key in the
 * Authorization header as a Token — there is no separate auth step.
 *
 * Flow:
 *   Step 1 — POST /v1/intention/   → returns intentionId + clientSecret
 *   Step 2 — frontend redirects to UnifiedCheckout using publicKey + clientSecret
 *   Step 3 — Paymob POSTs webhook to our callback URL
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
     * Result of a successful intention creation.
     *
     * @param intentionId  Paymob intention ID — store as the provider order reference
     * @param clientSecret Single-use token passed to the frontend checkout UI
     */
    public record IntentionResult(String intentionId, String clientSecret) {}

    /**
     * Create a payment intention — single API call that replaces the old 3-step flow.
     *
     * @param secretKey      Paymob Secret Key (from dashboard Settings)
     * @param baseUrl        Regional base URL, e.g. https://uae.paymob.com
     * @param amountCents    Total amount in smallest currency unit (e.g. 10000 = 100.00 AED)
     * @param currency       ISO 4217 code — "AED", "EGP", "SAR" …
     * @param integrationId  Paymob integration ID for the chosen payment method
     * @param specialReference  Your internal order/reference ID
     * @param billingData    Customer billing details node
     * @param customer       Customer identity node
     * @param items          Line items array
     */
    public IntentionResult createIntention(String secretKey, String baseUrl,
                                           long amountCents, String currency,
                                           int integrationId, String specialReference,
                                           ObjectNode billingData, ObjectNode customer,
                                           ArrayNode items, String notificationUrl,
                                           String redirectionUrl) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("amount", amountCents);
        body.put("currency", currency);
        body.put("special_reference", specialReference);

        ArrayNode paymentMethods = objectMapper.createArrayNode();
        paymentMethods.add(integrationId);
        body.set("payment_methods", paymentMethods);

        body.set("items", items);
        body.set("billing_data", billingData);
        body.set("customer", customer);

        if (notificationUrl != null && !notificationUrl.isBlank()) {
            body.put("notification_url", notificationUrl);
        }
        if (redirectionUrl != null && !redirectionUrl.isBlank()) {
            body.put("redirection_url", redirectionUrl);
        }

        JsonNode response = post(baseUrl + "/v1/intention/", body, "Token " + secretKey);
        
        String intentionId = response.get("id").asText();
        String providerOrderId = intentionId;
        
        // Paymob UAE Intention API: the webhook obj.order.id matches response.order.id (numeric)
        // rather than response.id (pi_...). We prioritize the numeric ID for better matching.
        if (response.has("order") && response.get("order").has("id")) {
            providerOrderId = response.get("order").get("id").asText();
        }

        return new IntentionResult(providerOrderId, response.get("client_secret").asText());
    }

    /**
     * Submit a refund for a completed transaction.
     *
     * @param secretKey            Paymob Secret Key
     * @param baseUrl              Regional base URL
     * @param paymobTransactionId  Paymob's transaction ID (from webhook)
     * @param amountCents          Amount to refund in smallest currency unit
     * @return Paymob refund transaction ID
     */
    public String refund(String secretKey, String baseUrl,
                         String paymobTransactionId, long amountCents) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("transaction_id", paymobTransactionId);
        body.put("amount_cents", amountCents);

        JsonNode response = post(baseUrl + "/api/acceptance/void_refund/refund", body, "Token " + secretKey);
        return response.get("id").asText();
    }

    // -------------------------------------------------------------------------
    // Internal helper
    // -------------------------------------------------------------------------

    private JsonNode post(String url, ObjectNode body, String authHeader) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (authHeader != null) {
                headers.set(HttpHeaders.AUTHORIZATION, authHeader);
            }
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Paymob API call failed for URL " + url + ": " + e.getMessage(), e);
        }
    }
}
