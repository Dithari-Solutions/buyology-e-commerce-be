package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the check that stops a signed callback settling a payment it did not pay for.
 *
 * <p>Paymob's HMAC covers {@code amount_cents}, {@code currency} and {@code order.id} — but not
 * {@code order.merchant_order_id}, and that unsigned field is the one this service consults FIRST
 * to decide which of our transactions a callback belongs to. So a valid signature proves the
 * numbers came from Paymob; on its own it proves nothing about which of our rows they settle.
 *
 * <p>Before this check, the webhook handler read only {@code success} and {@code pending}. A
 * genuine signed callback for a small payment could therefore be replayed with that one field
 * edited and would mark any other transaction SUCCESS — a large order settled on the strength of a
 * small order's money, with the signature still verifying.
 *
 * <p>The comparison is what closes it, and it needs no change to the signature: a callback carrying
 * 100 cents can only ever settle a transaction that is owed 100 cents.
 */
class WebhookAmountBindingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Only the guard is exercised, so the service's collaborators are never touched. */
    private static final PaymentService SERVICE =
            (PaymentService) org.springframework.beans.BeanUtils.instantiateClass(
                    firstConstructor(), new Object[firstConstructor().getParameterCount()]);

    private static java.lang.reflect.Constructor<?> firstConstructor() {
        return PaymentService.class.getDeclaredConstructors()[0];
    }

    private static PaymentTransaction tx(long cents, String currency) {
        PaymentTransaction t = new PaymentTransaction();
        t.setAmountCents(cents);
        t.setCurrency(currency);
        return t;
    }

    private JsonNode payload(String json) throws Exception {
        return mapper.readTree(json);
    }

    private boolean matches(PaymentTransaction t, JsonNode obj) {
        return Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(SERVICE, "webhookMoneyMatchesTransaction", t, obj));
    }

    @Test
    void acceptsACallbackWhoseMoneyMatches() throws Exception {
        assertTrue(matches(tx(150_00L, "AED"),
                payload("{\"amount_cents\":15000,\"currency\":\"AED\",\"success\":true}")));
    }

    @Test
    void rejectsASmallPaymentPointedAtALargeTransaction() throws Exception {
        // The attack: a genuine, correctly signed 1 AED callback with merchant_order_id edited to
        // name a 15,000 AED transaction. The signature still verifies — that field is not signed.
        assertFalse(matches(tx(1_500_000L, "AED"),
                payload("{\"amount_cents\":100,\"currency\":\"AED\",\"success\":true,"
                        + "\"order\":{\"merchant_order_id\":\"someone-elses-transaction\"}}")));
    }

    @Test
    void rejectsACurrencyMismatch() throws Exception {
        // 100 units of a weaker currency must not settle 100 units of a stronger one.
        assertFalse(matches(tx(10_000L, "AED"),
                payload("{\"amount_cents\":10000,\"currency\":\"USD\",\"success\":true}")));
    }

    @Test
    void treatsAMissingAmountAsAMismatch() throws Exception {
        // A callback that does not say what it paid is not evidence that anything was paid.
        assertFalse(matches(tx(10_000L, "AED"), payload("{\"currency\":\"AED\",\"success\":true}")));
        assertFalse(matches(tx(10_000L, "AED"),
                payload("{\"amount_cents\":null,\"currency\":\"AED\",\"success\":true}")));
        assertFalse(matches(tx(10_000L, "AED"), payload("{\"amount_cents\":10000,\"success\":true}")));
    }

    @Test
    void refusesToSettleATransactionWithNoRecordedAmount() throws Exception {
        assertFalse(matches(nullAmount(),
                payload("{\"amount_cents\":10000,\"currency\":\"AED\",\"success\":true}")));
    }

    private static PaymentTransaction nullAmount() {
        PaymentTransaction t = new PaymentTransaction();
        t.setCurrency("AED");
        t.setAmountCents(null);
        return t;
    }

    @Test
    void currencyComparisonIsCaseInsensitive() throws Exception {
        // Paymob's casing is not something to fail a real payment over.
        assertTrue(matches(tx(10_000L, "AED"),
                payload("{\"amount_cents\":10000,\"currency\":\"aed\",\"success\":true}")));
    }
}
