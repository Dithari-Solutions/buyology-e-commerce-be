package com.buyology.ecommerce.order.event;

import java.util.UUID;

/**
 * Published by PaymentService when a payment webhook returns a non-success
 * terminal state (FAILED / CANCELLED). OrderService listens to this event and
 * transitions the matching order to FAILED.
 */
public class PaymentFailedEvent {

    private final UUID orderId;
    private final UUID transactionId;
    private final String reason;

    public PaymentFailedEvent(UUID orderId, UUID transactionId, String reason) {
        this.orderId = orderId;
        this.transactionId = transactionId;
        this.reason = reason;
    }

    public UUID getOrderId() { return orderId; }
    public UUID getTransactionId() { return transactionId; }
    public String getReason() { return reason; }
}
