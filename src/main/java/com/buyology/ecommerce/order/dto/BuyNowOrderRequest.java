package com.buyology.ecommerce.order.dto;

import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request for a "Buy Now" order — checks out a SINGLE product directly, without
 * touching the user's persistent cart. The backend builds an ephemeral,
 * single-item checked-out cart and runs it through the normal order pipeline.
 */
public class BuyNowOrderRequest {

    @NotNull
    private UUID productId;

    @NotNull
    private UUID storeId;

    /** Defaults to 1 when null/invalid. */
    private Integer quantity;

    @NotNull
    private UUID addressId;

    private DeliveryMethod deliveryMethod;

    private BigDecimal shippingFee;

    private String couponCode;

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public UUID getAddressId() { return addressId; }
    public void setAddressId(UUID addressId) { this.addressId = addressId; }

    public DeliveryMethod getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(DeliveryMethod deliveryMethod) { this.deliveryMethod = deliveryMethod; }

    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
}
