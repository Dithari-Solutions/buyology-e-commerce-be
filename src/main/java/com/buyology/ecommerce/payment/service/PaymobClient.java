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

import java.time.Instant;

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
     * Asks Paymob for the transaction under one of its orders — the lookup the settlement sweep
     * uses when no callback ever told us Paymob's transaction id.
     *
     * <p>Keyed on <strong>Paymob's own order id</strong> when we have it. We store it for every
     * attempt (the intention's {@code intention_order_id}), and it is what Paymob's Transaction
     * Inquiry documents: {@code {"order_id": ...}} with {@code Authorization: Bearer <token>}. The
     * older lookup sent only our merchant reference, with the token in the body and no header —
     * and a Tabby or Tamara payment that Paymob showed as paid came back as "no such transaction",
     * which the sweep read as "the shopper never paid". Our merchant reference remains the fallback
     * for an attempt with no Paymob order id. The token also stays in the body, so either auth
     * style Paymob's regional API accepts is satisfied.
     *
     * @return Paymob's transaction object, or {@code null} when Paymob genuinely has none (404, an
     *         empty reply, or no id) — for a sweep, an abandoned checkout is the ordinary case
     * @throws PaymentGatewayException for anything else (refused credentials, 5xx, timeout), so a
     *         credentials problem is never reported as "the customer did not pay"
     */
    public JsonNode inquireTransaction(String apiKey, String baseUrl, Long paymobOrderId,
                                       String merchantOrderId) {
        if (paymobOrderId == null && (merchantOrderId == null || merchantOrderId.isBlank())) {
            return null;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new com.buyology.ecommerce.payment.exception.PaymentGatewayException(
                    "No Paymob API key is configured, so the payment cannot be looked up", null);
        }
        try {
            return inquire(apiKey, baseUrl, paymobOrderId, merchantOrderId);
        } catch (com.buyology.ecommerce.payment.exception.PaymentGatewayException e) {
            if (!(e.getCause() instanceof org.springframework.web.client.HttpClientErrorException.Unauthorized
                    || e.getCause() instanceof org.springframework.web.client.HttpClientErrorException.Forbidden)) {
                throw e;
            }
            // A cached token Paymob no longer honours would otherwise fail every lookup until it
            // aged out. Mint a fresh one and ask once more; a second refusal is real.
            this.cachedToken = null;
            return inquire(apiKey, baseUrl, paymobOrderId, merchantOrderId);
        }
    }

    private JsonNode inquire(String apiKey, String baseUrl, Long paymobOrderId, String merchantOrderId) {
        String token = authToken(apiKey, baseUrl);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("auth_token", token);
        if (paymobOrderId != null) {
            body.put("order_id", paymobOrderId);
        } else {
            body.put("merchant_order_id", merchantOrderId);
        }
        try {
            JsonNode result = post(baseUrl + "/api/ecommerce/orders/transaction_inquiry", body,
                    "Bearer " + token, true);
            return result != null && result.hasNonNull("id") ? result : null;
        } catch (com.buyology.ecommerce.payment.exception.PaymentGatewayException e) {
            if (e.getCause() instanceof org.springframework.web.client.HttpClientErrorException.NotFound) {
                return null;   // Paymob has no transaction under that order: nothing to settle
            }
            throw e;
        }
    }

    /** A short-lived Paymob auth token, minted from the API key and reused for 50 minutes. */
    private String authToken(String apiKey, String baseUrl) {
        CachedToken cached = this.cachedToken;
        if (cached != null && cached.matches(apiKey, baseUrl) && Instant.now().isBefore(cached.expiresAt())) {
            return cached.token();
        }
        ObjectNode authBody = objectMapper.createObjectNode();
        authBody.put("api_key", apiKey);
        JsonNode auth = post(baseUrl + "/api/auth/tokens", authBody, null);
        String token = auth != null && auth.hasNonNull("token") ? auth.get("token").asText() : null;
        if (token == null || token.isBlank()) {
            throw new com.buyology.ecommerce.payment.exception.PaymentGatewayException(
                    "Paymob did not issue an auth token for the configured API key", null);
        }
        // Paymob's tokens last an hour; renewing at 50 minutes keeps a sweep from racing expiry.
        this.cachedToken = new CachedToken(Integer.toHexString(apiKey.hashCode()), baseUrl, token,
                Instant.now().plus(java.time.Duration.ofMinutes(50)));
        return token;
    }

    private volatile CachedToken cachedToken;

    /** The key is held only as a hash, so a heap dump does not carry the API key twice. */
    private record CachedToken(String keyHash, String baseUrl, String token, Instant expiresAt) {
        boolean matches(String apiKey, String url) {
            return keyHash.equals(Integer.toHexString(apiKey.hashCode())) && baseUrl.equals(url);
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
        return post(url, body, authHeader, false);
    }

    /**
     * @param notFoundIsAnswer a 404 is an ordinary answer here ("no such transaction"), logged at
     *                         DEBUG — the settlement sweep asks about every abandoned checkout for
     *                         days, and logging each at ERROR would bury the failures that matter
     */
    private JsonNode post(String url, ObjectNode body, String authHeader, boolean notFoundIsAnswer) {
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
            if (notFoundIsAnswer && e.getStatusCode().value() == 404) {
                log.debug("[PAYMOB] 404 from {} — body={}", url, responseBody);
            } else {
                log.error("[PAYMOB] {} from {} — body={}", e.getStatusCode(), url, responseBody);
            }
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
