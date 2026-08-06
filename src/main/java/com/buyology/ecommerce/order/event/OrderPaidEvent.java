package com.buyology.ecommerce.order.event;

import java.util.UUID;

/**
 * Published by OrderService once an order has been transitioned to PAID and saved —
 * from both payment paths (pre-created order, and the cart-first flow).
 *
 * <p>Distinct from {@link PaymentSucceededEvent}, which fires while the order may not exist
 * yet: listeners of this event are guaranteed the order row is committed and PAID.
 *
 * <p>Downstream integrations (currently the ERPNext push) subscribe to this instead of
 * being called inline, so OrderService keeps no dependency on them and a slow or failing
 * integration cannot affect the order or payment flow.
 */
public class OrderPaidEvent {

    private final UUID orderId;

    public OrderPaidEvent(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID getOrderId() { return orderId; }
}
