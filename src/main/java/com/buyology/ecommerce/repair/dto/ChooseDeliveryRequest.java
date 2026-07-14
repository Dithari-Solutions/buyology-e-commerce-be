package com.buyology.ecommerce.repair.dto;

import com.buyology.ecommerce.repair.domain.RepairDeliveryMethod;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Customer's choice for how the device reaches the store (after submission).
 * {@code method} must be COURIER_PICKUP (20 AED) or STORE_DROPOFF (free); a STORE_DROPOFF
 * requires {@code storeLocationId}. {@code currency} is the customer's preferred currency the
 * 20 AED courier fee should be converted into (optional; defaults to AED).
 */
public class ChooseDeliveryRequest {

    @NotNull
    private RepairDeliveryMethod method;

    private UUID storeLocationId;

    private String currency;

    public RepairDeliveryMethod getMethod() { return method; }
    public void setMethod(RepairDeliveryMethod method) { this.method = method; }
    public UUID getStoreLocationId() { return storeLocationId; }
    public void setStoreLocationId(UUID storeLocationId) { this.storeLocationId = storeLocationId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
