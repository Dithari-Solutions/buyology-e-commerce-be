package com.buyology.ecommerce.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class CartResponse {

    private UUID id;
    private UUID authCredentialId;
    private String status;
    private BigDecimal totalPrice;
    private String countryCode;
    private String currency;
    private List<CartItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;

    // Pricing policy snapshot, all expressed in {@link #currency}.
    private BigDecimal freeShippingThreshold;
    private BigDecimal deliveryFee;
    private Boolean qualifiesForFreeShipping;

    /**
     * Whether any item in the cart is held by a store within the customer's 30-minute radius, i.e.
     * whether 30-minute delivery can be chosen at checkout at all.
     */
    private Boolean expressAvailable;

    /**
     * Fee for 30-minute delivery, in {@link #currency}. Present only when
     * {@link #expressAvailable} is true. {@link #deliveryFee} remains the standard rate, so the two
     * can be shown side by side and a client that ignores this field is unaffected.
     */
    private BigDecimal expressDeliveryFee;

    public CartResponse() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAuthCredentialId() { return authCredentialId; }
    public void setAuthCredentialId(UUID authCredentialId) { this.authCredentialId = authCredentialId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public List<CartItemResponse> getItems() { return items; }
    public void setItems(List<CartItemResponse> items) { this.items = items; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public BigDecimal getFreeShippingThreshold() { return freeShippingThreshold; }
    public void setFreeShippingThreshold(BigDecimal freeShippingThreshold) { this.freeShippingThreshold = freeShippingThreshold; }

    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }

    public Boolean getExpressAvailable() { return expressAvailable; }
    public void setExpressAvailable(Boolean expressAvailable) { this.expressAvailable = expressAvailable; }

    public BigDecimal getExpressDeliveryFee() { return expressDeliveryFee; }
    public void setExpressDeliveryFee(BigDecimal expressDeliveryFee) { this.expressDeliveryFee = expressDeliveryFee; }

    public Boolean getQualifiesForFreeShipping() { return qualifiesForFreeShipping; }
    public void setQualifiesForFreeShipping(Boolean qualifiesForFreeShipping) { this.qualifiesForFreeShipping = qualifiesForFreeShipping; }
}
