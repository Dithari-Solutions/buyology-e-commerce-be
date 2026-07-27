package com.buyology.ecommerce.b2b.quote.dto;

import java.util.UUID;

/**
 * Member checks out an accepted quote by bank transfer, uploading proof of payment.
 * Carries the delivery target (same as {@link CheckoutQuoteRequest}) but no gateway
 * payment method — payment happens externally via the member's bank and is verified
 * by procurement. Sent as the JSON {@code data} part of a multipart request alongside
 * the {@code proof} file.
 */
public class BankTransferCheckoutRequest {

    /** "PICKUP" collects from a store; anything else (or null) is treated as DELIVERY. */
    private String deliveryMethod;

    /** Required for delivery. */
    private UUID addressId;

    /** Required for pickup. */
    private UUID pickupStoreId;

    public String getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }

    public UUID getAddressId() { return addressId; }
    public void setAddressId(UUID addressId) { this.addressId = addressId; }

    public UUID getPickupStoreId() { return pickupStoreId; }
    public void setPickupStoreId(UUID pickupStoreId) { this.pickupStoreId = pickupStoreId; }
}
