package com.buyology.ecommerce.order.domain;

import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Central order record created after a successful payment or at cart checkout.
 *
 * Design notes:
 * - userId / cartId / paymentTransactionId are stored as plain UUIDs (cross-service boundary — no DB-level FK).
 * - Delivery address fields are snapshot copies taken from UserAddress at order creation time
 *   so the address can be changed later without affecting historical orders.
 * - trackingCode / carrierName are set by admin when marking an order as SHIPPED.
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user_id",    columnList = "user_id"),
        @Index(name = "idx_orders_status",     columnList = "status"),
        @Index(name = "idx_orders_courier",    columnList = "courier_user_id"),
        @Index(name = "idx_orders_payment_tx", columnList = "payment_transaction_id")
})
public class Order {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ── Actor IDs (cross-service UUIDs, no DB-level FK) ──────────────────────

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "auth_credential_id", nullable = false)
    private UUID authCredentialId;

    /** Reference to the cart from which this order was created. */
    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    /** Set once payment succeeds (via PaymentSucceededEvent). */
    @Column(name = "payment_transaction_id")
    private UUID paymentTransactionId;

    /** Assigned courier for EXPRESS_DELIVERY orders. */
    @Column(name = "courier_user_id")
    private UUID courierUserId;

    // ── Delivery classification ───────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, columnDefinition = "TEXT")
    private DeliveryMethod deliveryMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    /** Estimated delivery time description (e.g. "30-45 mins", "Tomorrow by 6 PM"). */
    @Column(name = "estimated_delivery_time", length = 100)
    private String estimatedDeliveryTime;

    // ── Delivery address snapshot ─────────────────────────────────────────────

    /** Original UserAddress.id kept for reference; the fields below are the immutable snapshot. */
    @Column(name = "delivery_address_id")
    private UUID deliveryAddressId;

    @Column(name = "recipient_first_name", length = 100)
    private String recipientFirstName;

    @Column(name = "recipient_last_name", length = 100)
    private String recipientLastName;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "country", length = 3)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "delivery_latitude")
    private Double deliveryLatitude;

    @Column(name = "delivery_longitude")
    private Double deliveryLongitude;

    // ── Pricing ───────────────────────────────────────────────────────────────

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    // ── Carrier info (admin-set on SHIPPED) ──────────────────────────────────

    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    @Column(name = "carrier_name", length = 100)
    private String carrierName;

    // ── Milestone timestamps ──────────────────────────────────────────────────

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Relations ─────────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<OrderTrackingEvent> trackingHistory = new ArrayList<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null)      this.status = OrderStatus.PENDING_PAYMENT;
        if (this.shippingFee == null) this.shippingFee = BigDecimal.ZERO;
        if (this.discount == null)    this.discount = BigDecimal.ZERO;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getAuthCredentialId() { return authCredentialId; }
    public void setAuthCredentialId(UUID authCredentialId) { this.authCredentialId = authCredentialId; }

    public UUID getCartId() { return cartId; }
    public void setCartId(UUID cartId) { this.cartId = cartId; }

    public UUID getPaymentTransactionId() { return paymentTransactionId; }
    public void setPaymentTransactionId(UUID paymentTransactionId) { this.paymentTransactionId = paymentTransactionId; }

    public UUID getCourierUserId() { return courierUserId; }
    public void setCourierUserId(UUID courierUserId) { this.courierUserId = courierUserId; }

    public DeliveryMethod getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(DeliveryMethod deliveryMethod) { this.deliveryMethod = deliveryMethod; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public String getEstimatedDeliveryTime() { return estimatedDeliveryTime; }
    public void setEstimatedDeliveryTime(String estimatedDeliveryTime) { this.estimatedDeliveryTime = estimatedDeliveryTime; }

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
    public Instant getUpdatedAt() { return updatedAt; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public List<OrderTrackingEvent> getTrackingHistory() { return trackingHistory; }
    public void setTrackingHistory(List<OrderTrackingEvent> trackingHistory) { this.trackingHistory = trackingHistory; }
}
