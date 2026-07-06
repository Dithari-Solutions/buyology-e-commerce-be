package com.buyology.ecommerce.order.domain.enums;

/**
 * Unified order status. New flow (admin-managed, no courier backend):
 *   Delivery: PENDING_PAYMENT → PAID → PACKAGING → IN_COURIER → IN_TRANSIT → DELIVERED
 *   Pickup:   PENDING_PAYMENT → PAID → PACKAGING → READY_FOR_PICKUP → DELIVERED
 *                                                                    ↘ CANCELLED / FAILED
 *
 * Legacy values (PROCESSING / COURIER_ASSIGNED / PICKED_UP / SHIPPED) are
 * preserved for historical orders created before the courier-backend was
 * disabled. New orders should not use them.
 */
public enum OrderStatus {

    /** Order created, waiting for payment confirmation. */
    PENDING_PAYMENT,

    /** Payment confirmed (set automatically via PaymentSucceededEvent). */
    PAID,

    /** Admin is preparing/packaging the order. */
    PACKAGING,

    /** Store-pickup order is packed and waiting at the store for the customer to collect. */
    READY_FOR_PICKUP,

    /** Order has been handed over to a courier (admin uploads pickup photo here). */
    IN_COURIER,

    /** Order is on its way to the customer. */
    IN_TRANSIT,

    /** Order has been successfully delivered to the customer (admin uploads dropoff photo). */
    DELIVERED,

    /** Order was cancelled (reason captured from customer or admin). */
    CANCELLED,

    /** Delivery attempt failed (e.g., customer unreachable, address invalid). */
    FAILED,

    // ── Legacy values (kept for historical orders) ────────────────────────────

    /** @deprecated Replaced by {@link #PACKAGING}. */
    @Deprecated
    PROCESSING,

    /** @deprecated Courier-backend integration has been disabled. */
    @Deprecated
    COURIER_ASSIGNED,

    /** @deprecated Courier-backend integration has been disabled. */
    @Deprecated
    PICKED_UP,

    /** @deprecated Replaced by {@link #IN_COURIER}. */
    @Deprecated
    SHIPPED
}
