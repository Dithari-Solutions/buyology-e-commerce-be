package com.buyology.ecommerce.payment.controller;

import com.buyology.ecommerce.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Receives Paymob webhook callbacks.
 *
 * Security note: This endpoint must be excluded from JWT authentication in
 * SecurityConfig (it is verified by HMAC instead). Add it to the public
 * permit-all list:
 *   .requestMatchers("/api/payments/webhook").permitAll()
 *
 * The HMAC is sent by Paymob as the "hmac" query parameter, e.g.:
 *   POST /api/payments/webhook?hmac=<sha512_hex>
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentWebhookController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PaymentService paymentService;

    public PaymentWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveWebhook(
            @RequestParam(value = "hmac", required = false) String hmacParam,
            HttpServletRequest request) throws IOException {

        String rawPayload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // Paymob Intention API sends HMAC inside the JSON body under "hmac" key,
        // not as a query parameter. Fall back to query param for legacy compatibility.
        String hmac = hmacParam;
        if (hmac == null && !rawPayload.isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode root =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(rawPayload);
                if (root.has("hmac") && !root.get("hmac").isNull()) {
                    hmac = root.get("hmac").asText();
                }
            } catch (Exception e) {
                // Malformed JSON body — log so a genuine-but-broken event leaves a trail
                // instead of being silently dropped. handleWebhook will reject on null HMAC.
                log.warn("[WEBHOOK] Failed to parse webhook body for HMAC extraction: {}", e.getMessage());
            }
        }

        paymentService.handleWebhook(rawPayload, hmac);

        // Always return 200 so Paymob does not retry — we handle idempotency internally
        return ResponseEntity.ok().build();
    }

    /**
     * Confirms a payment from the Paymob browser redirect ("transaction response
     * callback") as a resilient fallback to the server-to-server webhook. The
     * storefront forwards the redirect query parameters here when the shopper lands
     * back on the site; the service verifies the HMAC Paymob signed over those
     * params before mutating anything, then runs the same idempotent path the
     * webhook uses. Public (HMAC-authenticated) so it works even if the shopper's
     * session token has expired by the time they return from the 3-D Secure step.
     */
    @PostMapping("/confirm-redirect")
    public ResponseEntity<Void> confirmRedirect(@RequestBody java.util.Map<String, String> params) {
        try {
            // Reconstruct the webhook-shaped payload, then run it through the *proxied*
            // handleWebhook so its own @Transactional boundary (and AFTER_COMMIT
            // order-paid event) apply — exactly as for a real webhook delivery.
            String payload = paymentService.buildRedirectWebhookPayload(params);
            if (payload != null) {
                paymentService.handleWebhook(payload, params.get("hmac"));
            }
        } catch (Exception e) {
            // Best-effort fallback: a bad HMAC / unknown order must not break the
            // shopper's return-to-site UX. handleWebhook has already rolled back.
            log.warn("[REDIRECT-CONFIRM] Confirmation failed: {}", e.getMessage());
        }
        // Always 200 so the storefront's return flow proceeds to status polling.
        return ResponseEntity.ok().build();
    }
}
