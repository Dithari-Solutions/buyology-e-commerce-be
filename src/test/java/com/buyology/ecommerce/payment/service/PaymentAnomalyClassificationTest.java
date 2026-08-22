package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.payment.enums.PaymentAnomalyKind;
import com.buyology.ecommerce.payment.service.PaymentAnomalyService.Classification;
import com.buyology.ecommerce.payment.service.PaymentAnomalyService.Outcome;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the decision table for a settled payment, because two of its outcomes move money with no
 * human in the loop and one of them used to be silence.
 *
 * <p>The old code was `if (status == PENDING_PAYMENT) { apply }` with no else. A payment settling
 * after its order was cancelled — the customer cancels in one tab and completes checkout in the
 * other — was money captured with no log, no record, no refund. Every branch here now ends
 * somewhere explicit, and the two auto-refunding kinds are exactly the two where "money bought
 * nothing" is unambiguous.
 */
class PaymentAnomalyClassificationTest {

    private static Classification classify(OrderStatus status, boolean own, boolean sufficient,
                                           boolean settledByAnother) {
        return PaymentAnomalyService.classify(status, own, sufficient, () -> settledByAnother);
    }

    // ── the happy path stays the happy path ──────────────────────────────────

    @Test
    void aSufficientPaymentOnAPayableOrderApplies() {
        Classification c = classify(OrderStatus.PENDING_PAYMENT, false, true, false);
        assertEquals(Outcome.APPLY, c.outcome());
    }

    @Test
    void anUnderpaymentIsRecordedNotApplied() {
        Classification c = classify(OrderStatus.PENDING_PAYMENT, false, false, false);
        assertEquals(Outcome.ANOMALY, c.outcome());
        assertEquals(PaymentAnomalyKind.UNDERPAID, c.kind());
        assertFalse(c.kind().autoRefunds(),
                "an underpayer may still complete payment; refunding a partial capture is a decision");
    }

    // ── the silence this replaces ────────────────────────────────────────────

    @Test
    void aPaymentLandingOnACancelledOrderIsTheAutoRefundCase() {
        // THE original bug: cancel in one tab, finish paying in the other. Money captured, order
        // CANCELLED, and nothing anywhere recorded it.
        for (OrderStatus terminal : new OrderStatus[]{OrderStatus.CANCELLED, OrderStatus.FAILED}) {
            Classification c = classify(terminal, false, true, false);
            assertEquals(Outcome.ANOMALY, c.outcome());
            assertEquals(PaymentAnomalyKind.PAID_AFTER_CANCELLED, c.kind());
            assertTrue(c.kind().autoRefunds(),
                    "money captured against a " + terminal + " order bought nothing — it goes back");
        }
    }

    @Test
    void aSecondSettlementOfAnAlreadyPaidOrderIsADuplicateCharge() {
        Classification c = classify(OrderStatus.PAID, false, true, true);
        assertEquals(PaymentAnomalyKind.DUPLICATE_CHARGE, c.kind());
        assertTrue(c.kind().autoRefunds());
    }

    // ── replays are not duplicates ───────────────────────────────────────────

    @Test
    void theOrdersOwnTransactionReplayedIsNotAnAnomaly() {
        // Paymob delivers the same settlement via webhook AND browser redirect. The order already
        // carrying THIS transaction means a replayed event, and classifying it as a duplicate
        // charge would refund the customer's one real payment.
        Classification c = classify(OrderStatus.PAID, true, true, false);
        assertEquals(Outcome.ALREADY_APPLIED, c.outcome());
    }

    // ── the unknown branch never moves money ─────────────────────────────────

    @Test
    void anythingUnexplainedIsHeldForAHuman() {
        Classification c = classify(OrderStatus.IN_TRANSIT, false, true, false);
        assertEquals(PaymentAnomalyKind.UNEXPECTED_ORDER_STATE, c.kind());
        assertFalse(c.kind().autoRefunds(),
                "the branch where we do not know what happened is exactly where automation must not move money");
    }

    // ── the supplier is lazy ─────────────────────────────────────────────────

    @Test
    void theDuplicateCheckOnlyRunsWhenItCanMatter() {
        // The settled-by-another lookup is an extra query; the happy path every payment takes must
        // never pay for it.
        AtomicBoolean asked = new AtomicBoolean(false);
        PaymentAnomalyService.classify(OrderStatus.PENDING_PAYMENT, false, true, () -> {
            asked.set(true);
            return false;
        });
        assertFalse(asked.get(), "the happy path must not issue the duplicate-charge query");

        PaymentAnomalyService.classify(OrderStatus.PAID, true, true, () -> {
            asked.set(true);
            return false;
        });
        assertFalse(asked.get(), "a replayed own-transaction must not issue it either");
    }
}
