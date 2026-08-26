package com.buyology.ecommerce.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(PaymobClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PaymobClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Result of a successful intention creation.
     */
    public record IntentionResult(String intentionId, String clientSecret, Long paymobOrderId) {}

    /**
     * Create a payment intention — single API call for Paymob UAE Intention API.
     *
     * @param secretKey      Paymob Secret Key
     * @param baseUrl        Regional base URL (e.g. https://uae.paymob.com)
     * @param amountCents    Total amount in cents
     * @param currency       ISO 4217 code (e.g. "AED")
     * @param integrationId  Paymob integration ID
     * @param merchantOrderId OUR internal transaction UUID (mapped to Paymob's merchant_order_id)
     * @param billingData    Customer billing details node
     * @param customer       Customer identity node
     * @param items          Line items array
     */
    public IntentionResult createIntention(String secretKey, String baseUrl,
                                           long amountCents, String currency,
                                           int integrationId, String merchantOrderId,
                                           ObjectNode billingData, ObjectNode customer,
                                           ArrayNode items, String notificationUrl,
                                           String redirectionUrl) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("amount", amountCents);
        body.put("currency", currency);
        body.put("merchant_order_id", merchantOrderId); // Root level for Intention API
        
        ArrayNode paymentMethods = objectMapper.createArrayNode();
        paymentMethods.add(integrationId);
        body.set("payment_methods", paymentMethods);

        // Intention API (v2) also supports an optional 'order' object for grouping items/billing
        ObjectNode orderNode = objectMapper.createObjectNode();
        orderNode.put("amount", amountCents);
        orderNode.put("currency", currency);
        orderNode.put("merchant_order_id", merchantOrderId); // Double-verify at order level
        orderNode.set("items", items);
        if (notificationUrl != null && !notificationUrl.isBlank()) {
            orderNode.put("notification_url", notificationUrl);
        }
        body.set("order", orderNode);

        body.set("billing_data", billingData);
        body.set("customer", customer);

        // Extras for redirection
        ObjectNode extras = objectMapper.createObjectNode();
        if (redirectionUrl != null && !redirectionUrl.isBlank()) {
            extras.put("redirection_url", redirectionUrl);
        }
        body.set("extras", extras);

        String url = baseUrl + "/v1/intention/";
        log.info("[PAYMOB] Creating intention: url={}, merchant_order_id={}", url, merchantOrderId);
        JsonNode response = post(url, body, "Token " + secretKey);
        log.info("[PAYMOB] Intention response: {}", response.toString());
        
        // Defensive: a 200 with an unexpected body (no id/client_secret) must NOT NPE on
        // .asText() — surface it as a clear PaymentGatewayException instead of a generic 500.
        if (!response.has("id") || response.get("id").isNull()
                || !response.has("client_secret") || response.get("client_secret").isNull()) {
            throw new com.buyology.ecommerce.payment.exception.PaymentGatewayException(
                    "Payment provider returned an unexpected response: " + response.toString(), null);
        }
        String intentionId = response.get("id").asText();
        String clientSecret = response.get("client_secret").asText();
        Long paymobOrderId = null;

        // Paymob Intention API returns the order ID as "intention_order_id" at the root level
        if (response.has("intention_order_id") && !response.get("intention_order_id").isNull()) {
            paymobOrderId = response.get("intention_order_id").asLong();
            log.info("[PAYMOB] Extracted numeric paymobOrderId: {}", paymobOrderId);
        } else if (response.has("order") && !response.get("order").isNull()) {
            JsonNode respOrder = response.get("order");
            if (respOrder.has("id") && !respOrder.get("id").isNull()) {
                paymobOrderId = respOrder.get("id").asLong();
                log.info("[PAYMOB] Extracted numeric paymobOrderId from order.id: {}", paymobOrderId);
            }
        }

        return new IntentionResult(intentionId, clientSecret, paymobOrderId);
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

    /**
     * Ask Paymob what a transaction's real state is.
     *
     * <p>The reply carries the same fields as the webhook body ({@code success}, {@code pending},
     * {@code amount_cents}, {@code currency}, {@code order.merchant_order_id}), which is the point:
     * a recovery path can feed it through exactly the same settlement logic a webhook takes,
     * rather than a second, subtly different one that decides on its own what "paid" means.
     */
    public JsonNode getTransaction(String secretKey, String apiKey, String baseUrl,
                                   String paymobTransactionId) {
        String url = baseUrl + "/api/acceptance/transactions/" + paymobTransactionId;
        try {
            // Same credential style the refund call uses, so if refunds work this does too.
            return get(url, "Token " + secretKey);
        } catch (RuntimeException secretKeyFailure) {
            if (apiKey == null || apiKey.isBlank()) throw secretKeyFailure;
            // Paymob's older acceptance endpoints want a short-lived auth token minted from the
            // API key instead. Which of the two a merchant account accepts is not something we
            // can know from here, so try the other one before giving up on a payment the customer
            // has already made.
            log.warn("[PAYMOB] Secret-key lookup of transaction {} failed ({}); retrying with an "
                    + "auth token", paymobTransactionId, secretKeyFailure.getMessage());
            ObjectNode authBody = objectMapper.createObjectNode();
            authBody.put("api_key", apiKey);
            JsonNode auth = post(baseUrl + "/api/auth/tokens", authBody, null);
            String token = auth.hasNonNull("token") ? auth.get("token").asText() : null;
            if (token == null || token.isBlank()) {
                throw secretKeyFailure;
            }
            return get(url, "Bearer " + token);
        }
    }

    /**
     * Ask Paymob about a payment using <em>our</em> reference for it, when we never learned theirs.
     *
     * <p>{@link #getTransaction} needs a Paymob transaction id, which only ever reaches us in a
     * webhook. When no webhook arrives at all — the failure that left a paid Tabby order sitting in
     * PENDING_PAYMENT — there is no id to ask about, and the payment is invisible to every
     * automatic recovery path. The merchant order id is the one handle we always hold, because we
     * generated it ourselves when we created the intention.
     *
     * <p>Returns {@code null} rather than throwing when Paymob has nothing under that reference:
     * for a sweep over many candidates, "this checkout was genuinely abandoned" is the ordinary
     * case and must not read as a failure.
     */
    public JsonNode inquireByMerchantOrderId(String apiKey, String baseUrl, String merchantOrderId) {
        if (apiKey == null || apiKey.isBlank() || merchantOrderId == null || merchantOrderId.isBlank()) {
            return null;
        }
        ObjectNode authBody = objectMapper.createObjectNode();
        authBody.put("api_key", apiKey);
        JsonNode auth = post(baseUrl + "/api/auth/tokens", authBody, null);
        String token = auth.hasNonNull("token") ? auth.get("token").asText() : null;
        if (token == null || token.isBlank()) return null;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("auth_token", token);
        body.put("merchant_order_id", merchantOrderId);
        try {
            JsonNode result = post(baseUrl + "/api/ecommerce/orders/transaction_inquiry", body, null);
            // An empty envelope, or one with no id, means Paymob has no such transaction — the
            // shopper never got as far as paying. Not an error, just nothing to settle.
            return result != null && result.hasNonNull("id") ? result : null;
        } catch (RuntimeException notFound) {
            log.debug("[PAYMOB] No transaction under merchant order id {}: {}",
                    merchantOrderId, notFound.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Internal helper
    // -------------------------------------------------------------------------

    private JsonNode get(String url, String authHeader) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (authHeader != null) {
                headers.set(HttpHeaders.AUTHORIZATION, authHeader);
            }
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return objectMapper.readTree(response.getBody());
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("[PAYMOB] {} from {} — body={}", e.getStatusCode(), url, responseBody);
            throw new com.buyology.ecommerce.payment.exception.PaymentGatewayException(
                    "Payment provider rejected the request (" + e.getStatusCode() + "): "
                            + (responseBody == null || responseBody.isBlank() ? e.getMessage() : responseBody),
                    e);
        } catch (Exception e) {
            log.error("[PAYMOB] Call to {} failed", url, e);
            throw new com.buyology.ecommerce.payment.exception.PaymentGatewayException(
                    "Could not reach the payment provider: " + e.getMessage(), e);
        }
    }

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
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // A non-2xx from Paymob (bad secret key, wrong integration id, unsupported
            // currency, malformed billing, etc.). The response body carries the real reason —
            // log it in full and propagate a trimmed version so it's diagnosable client-side.
            String responseBody = e.getResponseBodyAsString();
            log.error("[PAYMOB] {} from {} — body={}", e.getStatusCode(), url, responseBody);
            throw new com.buyology.ecommerce.payment.exception.PaymentGatewayException(
                    "Payment provider rejected the request (" + e.getStatusCode() + "): "
                            + (responseBody == null || responseBody.isBlank() ? e.getMessage() : responseBody),
                    e);
        } catch (Exception e) {
            log.error("[PAYMOB] Call to {} failed", url, e);
            throw new com.buyology.ecommerce.payment.exception.PaymentGatewayException(
                    "Could not reach the payment provider: " + e.getMessage(), e);
        }
    }
}
