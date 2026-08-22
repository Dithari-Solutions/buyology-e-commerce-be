package com.buyology.ecommerce.payment.enums;

/**
 * Ways a settled payment can fail to buy anything.
 *
 * <p>Persisted as a plain string (see PaymentAnomaly) — adding a kind must never need a migration.
 */
public enum PaymentAnomalyKind {

    /**
     * The payment settled after its order left PENDING_PAYMENT — cancelled in another tab, or
     * superseded by a re-entered checkout — and money is captured against an order that will never
     * ship. Auto-refunded: the codebase already refunds with no human in the loop when a paid
     * order is cancelled, and this is the same event with the steps reversed.
     */
    PAID_AFTER_CANCELLED,

    /**
     * The order is already settled by a DIFFERENT successful payment — the customer was charged
     * twice for one order. Auto-refunded for the same reason: unambiguous, bounded, and the guard
     * counting PENDING refunds makes a second send impossible.
     */
    DUPLICATE_CHARGE,

    /**
     * The payment does not cover the order's total. NOT auto-refunded: the customer may complete
     * payment, and refunding a partial capture is a decision, not a default.
     */
    UNDERPAID,

    /** A SUCCESS payment referencing an order row that does not exist. A human must look. */
    ORPHANED_NO_ORDER,

    /**
     * Anything else — the branch where we explicitly do not know what happened, which is exactly
     * where automation must not move money.
     */
    UNEXPECTED_ORDER_STATE;

    /** Which kinds the sweep may refund without a human. The two unambiguous "money bought nothing" cases only. */
    public boolean autoRefunds() {
        return this == PAID_AFTER_CANCELLED || this == DUPLICATE_CHARGE;
    }
}
