package com.buyology.ecommerce.order.event;

import java.util.UUID;

/**
 * Published by PaymentService when a payment webhook confirms SUCCESS.
 * OrderService listens to this event and transitions the matching order to PAID.
 *
 * Using Spring's ApplicationEvent mechanism keeps the payment and order modules
 * decoupled — PaymentService does not depend on OrderService.
 */
public class PaymentSucceededEvent {

    /** The app-level order ID stored on the PaymentTransaction. */
    private final UUID orderId;

    /** The PaymentTransaction that succeeded. */
    private final UUID transactionId;

    public PaymentSucceededEvent(UUID orderId, UUID transactionId) {
        this.orderId = orderId;
        this.transactionId = transactionId;
    }

    public UUID getOrderId() { return orderId; }

    public UUID getTransactionId() { return transactionId; }
}
