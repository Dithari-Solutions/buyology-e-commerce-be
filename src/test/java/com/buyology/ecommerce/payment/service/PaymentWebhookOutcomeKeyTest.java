package com.buyology.ecommerce.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the half of the webhook idempotency key that decides whether a paid order is ever
 * marked paid.
 *
 * <p>Paymob sends one terminal webhook for a card, but an instalment payment (Tabby, Tamara)
 * reports <em>pending first and success later, on the same transaction id</em>. While the key
 * was the id alone, that second delivery collided with the first and was discarded as a replay:
 * the customer had paid, the transaction stayed PROCESSING and the order sat in PENDING_PAYMENT
 * — no dispatch, no confirmation, and nothing in the logs but "duplicate event".
 *
 * <p>So the rule these tests hold: the same event must key the same (replays stay idempotent),
 * and a change of outcome must key differently (a real settlement gets through).
 */
class PaymentWebhookOutcomeKeyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode payload(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String outcome(String json) {
        return PaymentService.webhookOutcome(payload(json));
    }

    @Test
    void aSettledPaymentIsSuccess() {
        assertEquals("success", outcome("{\"success\":true}"));
        // Paymob sends pending alongside success on some methods; success wins, as it must.
        assertEquals("success", outcome("{\"success\":true,\"pending\":true}"));
    }

    @Test
    void anInstalmentAwaitingTheProviderIsPending() {
        assertEquals("pending", outcome("{\"success\":false,\"pending\":true}"));
    }

    @Test
    void anythingElseIsFailed() {
        assertEquals("failed", outcome("{\"success\":false,\"pending\":false}"));
        assertEquals("failed", outcome("{}"), "a payload claiming nothing has not succeeded");
    }

    @Test
    void pendingThenSuccessAreDifferentKeys() {
        // THE regression. Same transaction id, two deliveries: if these two keys are equal the
        // settlement is swallowed as a duplicate and the customer's paid order never advances.
        String txnId = "123456789";
        String pendingKey = txnId + ":" + outcome("{\"success\":false,\"pending\":true}");
        String successKey = txnId + ":" + outcome("{\"success\":true}");

        assertNotEquals(pendingKey, successKey,
                "a Tabby/Tamara settlement must not collide with its own earlier pending event");
        assertEquals("123456789:pending", pendingKey);
        assertEquals("123456789:success", successKey);
    }

    @Test
    void theSameDeliveryTwiceKeysIdentically() {
        // The property that makes the ledger work at all: a genuine replay is still rejected.
        String first = "555:" + outcome("{\"success\":true,\"amount_cents\":159899}");
        String replay = "555:" + outcome("{\"success\":true,\"amount_cents\":159899}");
        assertEquals(first, replay);
    }
}
