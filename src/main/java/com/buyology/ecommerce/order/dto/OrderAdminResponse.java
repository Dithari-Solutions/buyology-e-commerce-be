package com.buyology.ecommerce.order.dto;

import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderAdminResponse extends OrderResponse {

    private String pickupProofImageUrl;
    private Instant pickupProofTakenAt;
    private String deliveryProofImageUrl;
    private String deliveryProofSignatureUrl;
    private String deliveredTo;
    private Instant deliveryProofTakenAt;
    private UUID storeId;

    // Getters and Setters

    public String getPickupProofImageUrl() { return pickupProofImageUrl; }
    public void setPickupProofImageUrl(String pickupProofImageUrl) { this.pickupProofImageUrl = pickupProofImageUrl; }

    public Instant getPickupProofTakenAt() { return pickupProofTakenAt; }
    public void setPickupProofTakenAt(Instant pickupProofTakenAt) { this.pickupProofTakenAt = pickupProofTakenAt; }

    public String getDeliveryProofImageUrl() { return deliveryProofImageUrl; }
    public void setDeliveryProofImageUrl(String deliveryProofImageUrl) { this.deliveryProofImageUrl = deliveryProofImageUrl; }

    public String getDeliveryProofSignatureUrl() { return deliveryProofSignatureUrl; }
    public void setDeliveryProofSignatureUrl(String deliveryProofSignatureUrl) { this.deliveryProofSignatureUrl = deliveryProofSignatureUrl; }

    public String getDeliveredTo() { return deliveredTo; }
    public void setDeliveredTo(String deliveredTo) { this.deliveredTo = deliveredTo; }

    public Instant getDeliveryProofTakenAt() { return deliveryProofTakenAt; }
    public void setDeliveryProofTakenAt(Instant deliveryProofTakenAt) { this.deliveryProofTakenAt = deliveryProofTakenAt; }

    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }
}
