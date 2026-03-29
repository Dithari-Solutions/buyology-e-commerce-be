package com.buyology.ecommerce.order.dto;

import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class CreateOrderRequest {

    @NotNull(message = "cartId is required")
    private UUID cartId;

    @NotNull(message = "addressId is required")
    private UUID addressId;

    @NotNull(message = "deliveryMethod is required")
    private DeliveryMethod deliveryMethod;

    private BigDecimal shippingFee;

    private String couponCode;

    private String notes;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getCartId() { return cartId; }
    public void setCartId(UUID cartId) { this.cartId = cartId; }

    public UUID getAddressId() { return addressId; }
    public void setAddressId(UUID addressId) { this.addressId = addressId; }

    public DeliveryMethod getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(DeliveryMethod deliveryMethod) { this.deliveryMethod = deliveryMethod; }

    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
