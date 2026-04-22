package com.buyology.ecommerce.order.dto;

import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderResponse {

    private UUID id;
    private UUID userId;
    private UUID cartId;
    private UUID paymentTransactionId;
    private UUID courierUserId;
    private UUID deliveryOrderId;
    private String courierName;
    private String courierPhone;
    private DeliveryMethod deliveryMethod;
    private OrderStatus status;

    // Address snapshot
    private UUID deliveryAddressId;
    private String recipientFirstName;
    private String recipientLastName;
    private String recipientPhone;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private Double deliveryLatitude;
    private Double deliveryLongitude;

    // Store Location
    private Double storeLatitude;
    private Double storeLongitude;

    // Pricing
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal discount;
    private BigDecimal totalAmount;
    private String currency;
    private String countryCode;
    private String couponCode;

    /** Estimated delivery time description (e.g. "30-45 mins", "Tomorrow by 6 PM"). */
    private String estimatedDeliveryTime;

    // Carrier info (admin-set on SHIPPED)
    private String trackingCode;
    private String carrierName;

    // Milestones
    private Instant paidAt;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant cancelledAt;
    private Instant createdAt;
    private Instant updatedAt;

    private List<OrderItemResponse> items;
    private List<TrackingEventResponse> trackingHistory;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getCartId() { return cartId; }
    public void setCartId(UUID cartId) { this.cartId = cartId; }

    public UUID getPaymentTransactionId() { return paymentTransactionId; }
    public void setPaymentTransactionId(UUID paymentTransactionId) { this.paymentTransactionId = paymentTransactionId; }

    public UUID getCourierUserId() { return courierUserId; }
    public void setCourierUserId(UUID courierUserId) { this.courierUserId = courierUserId; }

    public UUID getDeliveryOrderId() { return deliveryOrderId; }
    public void setDeliveryOrderId(UUID deliveryOrderId) { this.deliveryOrderId = deliveryOrderId; }

    public String getCourierName() { return courierName; }
    public void setCourierName(String courierName) { this.courierName = courierName; }

    public String getCourierPhone() { return courierPhone; }
    public void setCourierPhone(String courierPhone) { this.courierPhone = courierPhone; }

    public DeliveryMethod getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(DeliveryMethod deliveryMethod) { this.deliveryMethod = deliveryMethod; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public UUID getDeliveryAddressId() { return deliveryAddressId; }
    public void setDeliveryAddressId(UUID deliveryAddressId) { this.deliveryAddressId = deliveryAddressId; }

    public String getRecipientFirstName() { return recipientFirstName; }
    public void setRecipientFirstName(String recipientFirstName) { this.recipientFirstName = recipientFirstName; }

    public String getRecipientLastName() { return recipientLastName; }
    public void setRecipientLastName(String recipientLastName) { this.recipientLastName = recipientLastName; }

    public String getRecipientPhone() { return recipientPhone; }
    public void setRecipientPhone(String recipientPhone) { this.recipientPhone = recipientPhone; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public Double getDeliveryLatitude() { return deliveryLatitude; }
    public void setDeliveryLatitude(Double deliveryLatitude) { this.deliveryLatitude = deliveryLatitude; }

    public Double getDeliveryLongitude() { return deliveryLongitude; }
    public void setDeliveryLongitude(Double deliveryLongitude) { this.deliveryLongitude = deliveryLongitude; }

    public Double getStoreLatitude() { return storeLatitude; }
    public void setStoreLatitude(Double storeLatitude) { this.storeLatitude = storeLatitude; }

    public Double getStoreLongitude() { return storeLongitude; }
    public void setStoreLongitude(Double storeLongitude) { this.storeLongitude = storeLongitude; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(BigDecimal shippingFee) { this.shippingFee = shippingFee; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public String getEstimatedDeliveryTime() { return estimatedDeliveryTime; }
    public void setEstimatedDeliveryTime(String estimatedDeliveryTime) { this.estimatedDeliveryTime = estimatedDeliveryTime; }

    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }

    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public Instant getShippedAt() { return shippedAt; }
    public void setShippedAt(Instant shippedAt) { this.shippedAt = shippedAt; }

    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }

    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<OrderItemResponse> getItems() { return items; }
    public void setItems(List<OrderItemResponse> items) { this.items = items; }

    public List<TrackingEventResponse> getTrackingHistory() { return trackingHistory; }
    public void setTrackingHistory(List<TrackingEventResponse> trackingHistory) { this.trackingHistory = trackingHistory; }
}
