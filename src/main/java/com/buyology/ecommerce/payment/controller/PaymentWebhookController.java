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
            } catch (Exception ignored) {}
        }

        paymentService.handleWebhook(rawPayload, hmac);

        // Always return 200 so Paymob does not retry — we handle idempotency internally
        return ResponseEntity.ok().build();
    }
}
