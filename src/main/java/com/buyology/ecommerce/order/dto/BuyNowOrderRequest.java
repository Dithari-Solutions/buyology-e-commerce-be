package com.buyology.ecommerce.order.dto;

import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderPaymentMethod;
import jakarta.validation.constraints.Max;
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
    @Max(value = 1000, message = "quantity must not exceed 1000")
    private Integer quantity;

    /**
     * Required for EXPRESS/REGULAR delivery; omitted for PICKUP.
     *
     * <p>Deliberately not {@code @NotNull}: it was, and that alone made "Buy Now, collect from
     * store" impossible — the request was rejected before it reached the service, whatever
     * deliveryMethod said. Which of addressId and pickupStoreId is required depends on the
     * delivery method, so the choice is validated in the service (see
     * {@code OrderService.resolveFulfilment}) where the method is known, exactly as it is for a
     * normal cart checkout.
     */
    private UUID addressId;

    /** Required for PICKUP: the store branch the customer collects from. */
    private UUID pickupStoreId;

    private DeliveryMethod deliveryMethod;

    /** Null means ONLINE. See the note on {@code CreateOrderRequest.paymentMethod}. */
    private OrderPaymentMethod paymentMethod;

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

    public UUID getPickupStoreId() { return pickupStoreId; }
    public void setPickupStoreId(UUID pickupStoreId) { this.pickupStoreId = pickupStoreId; }

    public DeliveryMethod getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(DeliveryMethod deliveryMethod) { this.deliveryMethod = deliveryMethod; }

    public OrderPaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(OrderPaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
}
