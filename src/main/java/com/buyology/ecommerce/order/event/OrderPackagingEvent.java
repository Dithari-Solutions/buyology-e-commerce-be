package com.buyology.ecommerce.order.event;

import java.util.UUID;

/**
 * Published when an order moves to PACKAGING, whichever path moved it: the admin status change,
 * the admin tracking update or the supplier portal.
 *
 * <p>PACKAGING is the point where the shop has the order in hand, so it is when a courier should
 * be summoned. The Quiqup integration listens after commit: it creates the job if the order has
 * none yet (a cash order is never dispatchable before this), and marks the job ready for
 * collection, which is what makes Quiqup send a courier.
 */
public class OrderPackagingEvent {

    private final UUID orderId;

    public OrderPackagingEvent(UUID orderId) {
        this.orderId = orderId;
    }

    public UUID getOrderId() { return orderId; }
}
