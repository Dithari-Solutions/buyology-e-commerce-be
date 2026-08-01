package com.buyology.ecommerce.sell.dto;

import com.buyology.ecommerce.sell.domain.SellDeliveryMethod;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Customer's choice for how the device reaches the store (after submission).
 * {@code method} must be COURIER_PICKUP (20 AED) or STORE_DROPOFF (free); a STORE_DROPOFF
 * requires {@code storeLocationId}. {@code currency} is the customer's preferred currency the
 * 20 AED courier fee should be converted into (optional; defaults to AED).
 */
public class ChooseSellDeliveryRequest {

    @NotNull
    private SellDeliveryMethod method;

    private UUID storeLocationId;

    private String currency;

    /** Where Paymob returns the browser after the courier-fee checkout (courier pickup only). */
    private String redirectionUrl;

    public SellDeliveryMethod getMethod() { return method; }
    public void setMethod(SellDeliveryMethod method) { this.method = method; }
    public UUID getStoreLocationId() { return storeLocationId; }
    public void setStoreLocationId(UUID storeLocationId) { this.storeLocationId = storeLocationId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getRedirectionUrl() { return redirectionUrl; }
    public void setRedirectionUrl(String redirectionUrl) { this.redirectionUrl = redirectionUrl; }
}
