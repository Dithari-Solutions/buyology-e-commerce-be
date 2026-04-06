package com.buyology.ecommerce.order.domain.enums;

/**
 * Unified order status that covers both express and regular delivery flows.
 *
 * Express Delivery flow:
 *   PENDING_PAYMENT → PAID → COURIER_ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED / FAILED
 *
 * Regular Order flow:
 *   PENDING_PAYMENT → PAID → PROCESSING → SHIPPED → IN_TRANSIT → DELIVERED / FAILED
 *
 * Both flows allow CANCELLED from PENDING_PAYMENT or PAID.
 */
public enum OrderStatus {

    /** Order created, waiting for payment confirmation. */
    PENDING_PAYMENT,

    /** Payment confirmed (set automatically via PaymentSucceededEvent). */
    PAID,

    /** Admin has acknowledged the regular order and is preparing shipment. */
    PROCESSING,

    /** A local courier has been assigned to deliver the order. */
    COURIER_ASSIGNED,

    /** Local courier has picked up the order from the store. */
    PICKED_UP,

    /** Admin has dispatched the regular order and set the tracking code. */
    SHIPPED,

    /** Order is on its way to the customer (local or regular). */
    IN_TRANSIT,

    /** Order has been successfully delivered to the customer. */
    DELIVERED,

    /** Order was cancelled before delivery. */
    CANCELLED,

    /** Delivery attempt failed (e.g., customer unreachable, address invalid). */
    FAILED
}
