package com.buyology.ecommerce.payout.domain;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Join row: which order items are being settled by a payout request.
 * The unique constraint on {@code order_item_id} guarantees an order item
 * cannot appear in two different payout requests (no double payout).
 */
@Entity
@Table(name = "payout_request_order_items", indexes = {
        @Index(name = "idx_payout_req_items_request", columnList = "payout_request_id"),
        @Index(name = "uq_payout_req_items_item", columnList = "order_item_id", unique = true)
})
public class PayoutRequestOrderItem {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "payout_request_id", nullable = false)
    private UUID payoutRequestId;

    @Column(name = "order_item_id", nullable = false, unique = true)
    private UUID orderItemId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPayoutRequestId() { return payoutRequestId; }
    public void setPayoutRequestId(UUID payoutRequestId) { this.payoutRequestId = payoutRequestId; }

    public UUID getOrderItemId() { return orderItemId; }
    public void setOrderItemId(UUID orderItemId) { this.orderItemId = orderItemId; }
}
