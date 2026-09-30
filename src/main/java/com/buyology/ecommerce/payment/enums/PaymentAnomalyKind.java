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
     * The payment settled but the order could not be created because the units were gone.
     *
     * <p>Only reachable on the cart-first flow, where the gateway captures the money and the order is
     * built afterwards from the cart. Somebody else took the last unit in between.
     *
     * <p>Auto-refunded, and it belongs in that set for exactly the reason the other two do: no order
     * exists, nothing will ever ship, so the money unambiguously bought nothing. It is also the one
     * anomaly the customer is guaranteed to notice, because they are sitting in front of a payment
     * confirmation for something they will never receive.
     */
    STOCK_UNAVAILABLE,

    /**
     * The basket's live prices moved between the money being captured and the order being built.
     *
     * <p>Only reachable on the cart-first flow, where the gateway captures the quoted total and the
     * order is assembled from the cart seconds to minutes later (webhooks retry). A sale can start or
     * end inside that gap.
     *
     * <p>The order IS created, at the total the customer was quoted and charged — after capture the
     * quote is authoritative, so re-pricing may not rewrite it in either direction. See
     * {@code CheckoutRepricing} and {@code OrderService.repriceForCheckout}. What is left is a
     * difference somebody has to decide about, and it goes in both directions: a sale that STARTED in
     * the gap means the customer paid more than the shop is now asking and may be owed the
     * difference; a sale that ENDED means the shop honoured a price it no longer advertises.
     *
     * <p>NOT auto-refunded, for the same reason {@link #UNDERPAID} is not: an order exists and
     * something shipped or will ship, so refunding part of a settled payment is a decision, not a
     * default. Recording it is what makes that decision possible at all — before this existed the
     * difference was silently kept.
     */
    PRICE_CHANGED_AFTER_CAPTURE,

    /**
     * The payment settled but building the order threw, for a reason with no kind of its own.
     *
     * <p>The backstop on the cart-first flow: the money is captured before the order exists, so ANY
     * escape from order creation would otherwise leave a captured payment with no order and no record
     * that anything went wrong — the exact silence this whole enum exists to end. The throw still
     * rolls the order's own writes back (see the catch in {@code OrderService.onPaymentSucceeded});
     * this record survives it, because the insert runs on its own connection.
     *
     * <p>NOT auto-refunded, and that is the difference from {@link #STOCK_UNAVAILABLE}: the cause is
     * by definition unknown here, and a webhook retry may well build the order successfully a minute
     * later. Refunding automatically would race that retry and refund a paid order.
     */
    ORDER_CREATION_FAILED,

    /**
     * Anything else — the branch where we explicitly do not know what happened, which is exactly
     * where automation must not move money.
     */
    UNEXPECTED_ORDER_STATE;

    /** Which kinds the sweep may refund without a human. The unambiguous "money bought nothing" cases only. */
    public boolean autoRefunds() {
        return this == PAID_AFTER_CANCELLED || this == DUPLICATE_CHARGE || this == STOCK_UNAVAILABLE;
    }
}
