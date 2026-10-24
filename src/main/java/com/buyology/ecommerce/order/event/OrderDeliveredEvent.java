package com.buyology.ecommerce.order.event;

import java.util.UUID;

/**
 * Published when an order moves to DELIVERED, whichever path moved it: the admin, the courier
 * app, the delivery partner or the supplier portal.
 *
 * <p>Delivery is when the customer has the goods, so it is when warranty cards are issued. The
 * warranty module listens after commit: a delivery that rolls back issues nothing.
 */
public class OrderDeliveredEvent {

    private final UUID orderId;

    public OrderDeliveredEvent(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID getOrderId() { return orderId; }
}
