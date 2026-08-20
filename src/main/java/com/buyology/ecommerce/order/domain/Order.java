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

    /**
     * Optimistic-locking version — prevents lost updates when concurrent actors
     * (e.g. admin status change vs. courier-backend sync) mutate the same order.
     */
    @Version
    @Column(name = "version")
    private Long version;

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

    /** Assigned courier for EXPRESS orders. */
    @Column(name = "courier_user_id")
    private UUID courierUserId;

    /**
     * The delivery order ID on the courier backend.
     * Stored when a CourierAssignedEvent is received so the customer can subscribe
     * to the WebSocket topic for real-time location tracking.
     */
    @Column(name = "delivery_order_id")
    private UUID deliveryOrderId;

    /** Snapshot of the assigned courier's full name — shown to the customer. */
    @Column(name = "courier_name", length = 200)
    private String courierName;

    /** Snapshot of the assigned courier's phone number — shown to the customer. */
    @Column(name = "courier_phone", length = 30)
    private String courierPhone;

    // ── Delivery classification ───────────────────────────────────────────────

    @Convert(converter = com.buyology.ecommerce.order.domain.converter.DeliveryMethodConverter.class)
    @Column(name = "delivery_method", nullable = false, length = 50)
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

    /** B2B credit applied against this order (in {@link #creditCurrency}). */
    @Column(name = "credit_applied", precision = 19, scale = 4)
    private BigDecimal creditApplied;

    /** Currency of the credit applied — matches the wallet currency at time of use. */
    @Column(name = "credit_currency", length = 10)
    private String creditCurrency;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    /** Resolved promo code id — usage is recorded against it only once the order is PAID. */
    @Column(name = "promo_code_id")
    private UUID promoCodeId;

    // ── Store pickup (deliveryMethod == PICKUP) ──────────────────────────────
    // The customer collects from a chosen store branch instead of having it delivered.
    // A snapshot of the branch is stored so the admin/customer see where to collect.

    @Column(name = "pickup_store_id")
    private UUID pickupStoreId;

    @Column(name = "pickup_store_name", length = 200)
    private String pickupStoreName;

    @Column(name = "pickup_store_address", length = 500)
    private String pickupStoreAddress;

    // ── Carrier info (admin-set on SHIPPED) ──────────────────────────────────

    @Column(name = "tracking_code", length = 100)
    private String trackingCode;

    @Column(name = "carrier_name", length = 100)
    private String carrierName;

    // ── Admin-uploaded delivery proofs (Contabo object keys) ─────────────────

    /** Object key for the pickup photo uploaded by admin when handing to courier. */
    @Column(name = "pickup_proof_image_key", length = 500)
    private String pickupProofImageKey;

    @Column(name = "pickup_proof_taken_at")
    private Instant pickupProofTakenAt;

    /** Object key for the drop-off photo uploaded by admin on delivery. */
    @Column(name = "dropoff_proof_image_key", length = 500)
    private String dropoffProofImageKey;

    @Column(name = "dropoff_proof_taken_at")
    private Instant dropoffProofTakenAt;

    /** Free-form cancellation reason (set by admin or customer on CANCELLED). */
    @Column(name = "cancellation_reason", length = 1000)
    private String cancellationReason;

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

    // ── ERPNext sync ──────────────────────────────────────────────────────────
    // Populated by ErpOrderSyncService once the order is PAID. Purely a mirror of
    // what lives in ERPNext — the order flow never depends on these being set, and
    // a failed sync only records the error here (the order itself stays PAID).

    /** Name (primary key) of the ERPNext Sales Order created for this order. */
    @Column(name = "erp_sales_order", length = 140)
    private String erpSalesOrder;

    /** Name (primary key) of the ERPNext Sales Invoice created for this order. */
    @Column(name = "erp_sales_invoice", length = 140)
    private String erpSalesInvoice;

    /** When the ERPNext push last succeeded. */
    @Column(name = "erp_synced_at")
    private Instant erpSyncedAt;

    /** Last ERPNext sync failure message; null once a sync succeeds. */
    @Column(name = "erp_sync_error", length = 1000)
    private String erpSyncError;

    // ── Quiqup delivery ───────────────────────────────────────────────────────
    // Mirrors the ERP quartet above, and for the same reason: a dispatch that already carries a
    // Quiqup job id is skipped on retry, and the admin page can show per-order dispatch state.
    //
    // Deliberately NOT reusing deliveryOrderId — that column is a UUID and belongs to our own
    // courier service, whereas Quiqup issues its own opaque string id. Overloading it would make
    // "which carrier holds this parcel" unanswerable.

    /** Quiqup's own order id, once a job has been created for this order. Null = never dispatched. */
    @Column(name = "quiqup_order_id", length = 100)
    private String quiqupOrderId;

    /** The last delivery status Quiqup reported, verbatim, for support and for debugging mappings. */
    @Column(name = "quiqup_status", length = 60)
    private String quiqupStatus;

    @Column(name = "quiqup_dispatched_at")
    private Instant quiqupDispatchedAt;

    /**
     * Why the last dispatch attempt failed, or null after a success.
     *
     * <p>A failure never changes the order's status: the customer has paid and the order is still
     * valid, it simply has no courier yet. The retry job reads this column, and an order whose
     * dispatch keeps failing is visible to an admin rather than silently stuck.
     */
    @Column(name = "quiqup_dispatch_error", length = 1000)
    private String quiqupDispatchError;

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

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

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

    public BigDecimal getCreditApplied() { return creditApplied; }
    public void setCreditApplied(BigDecimal creditApplied) { this.creditApplied = creditApplied; }

    public String getCreditCurrency() { return creditCurrency; }
    public void setCreditCurrency(String creditCurrency) { this.creditCurrency = creditCurrency; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public UUID getPromoCodeId() { return promoCodeId; }
    public void setPromoCodeId(UUID promoCodeId) { this.promoCodeId = promoCodeId; }

    public UUID getPickupStoreId() { return pickupStoreId; }
    public void setPickupStoreId(UUID pickupStoreId) { this.pickupStoreId = pickupStoreId; }

    public String getPickupStoreName() { return pickupStoreName; }
    public void setPickupStoreName(String pickupStoreName) { this.pickupStoreName = pickupStoreName; }

    public String getPickupStoreAddress() { return pickupStoreAddress; }
    public void setPickupStoreAddress(String pickupStoreAddress) { this.pickupStoreAddress = pickupStoreAddress; }

    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }

    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }

    public String getPickupProofImageKey() { return pickupProofImageKey; }
    public void setPickupProofImageKey(String pickupProofImageKey) { this.pickupProofImageKey = pickupProofImageKey; }

    public Instant getPickupProofTakenAt() { return pickupProofTakenAt; }
    public void setPickupProofTakenAt(Instant pickupProofTakenAt) { this.pickupProofTakenAt = pickupProofTakenAt; }

    public String getDropoffProofImageKey() { return dropoffProofImageKey; }
    public void setDropoffProofImageKey(String dropoffProofImageKey) { this.dropoffProofImageKey = dropoffProofImageKey; }

    public Instant getDropoffProofTakenAt() { return dropoffProofTakenAt; }
    public void setDropoffProofTakenAt(Instant dropoffProofTakenAt) { this.dropoffProofTakenAt = dropoffProofTakenAt; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

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

    public String getErpSalesOrder() { return erpSalesOrder; }
    public void setErpSalesOrder(String erpSalesOrder) { this.erpSalesOrder = erpSalesOrder; }

    public String getErpSalesInvoice() { return erpSalesInvoice; }
    public void setErpSalesInvoice(String erpSalesInvoice) { this.erpSalesInvoice = erpSalesInvoice; }

    public Instant getErpSyncedAt() { return erpSyncedAt; }
    public void setErpSyncedAt(Instant erpSyncedAt) { this.erpSyncedAt = erpSyncedAt; }

    public String getQuiqupOrderId() { return quiqupOrderId; }
    public void setQuiqupOrderId(String quiqupOrderId) { this.quiqupOrderId = quiqupOrderId; }

    public String getQuiqupStatus() { return quiqupStatus; }
    public void setQuiqupStatus(String quiqupStatus) { this.quiqupStatus = quiqupStatus; }

    public Instant getQuiqupDispatchedAt() { return quiqupDispatchedAt; }
    public void setQuiqupDispatchedAt(Instant quiqupDispatchedAt) { this.quiqupDispatchedAt = quiqupDispatchedAt; }

    public String getQuiqupDispatchError() { return quiqupDispatchError; }
    public void setQuiqupDispatchError(String quiqupDispatchError) { this.quiqupDispatchError = quiqupDispatchError; }

    public String getErpSyncError() { return erpSyncError; }
    public void setErpSyncError(String erpSyncError) { this.erpSyncError = erpSyncError; }
}
