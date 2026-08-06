package com.buyology.ecommerce.sell.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A customer sell (trade-in) request: "buy my device". Any logged-in customer with a complete
 * contact profile can open one — there is no B2B membership gate, exactly like
 * {@link com.buyology.ecommerce.repair.domain.RepairRequest}, whose shape this deliberately mirrors
 * so the two flows behave identically for the customer.
 *
 * Ownership is keyed on {@code credentialId} (auth_credentials.id / sub); the users.id (uid) is
 * denormalized alongside it. Contact email/phone are snapshotted from the customer's profile at
 * submit time so notifications don't need a live lookup.
 *
 * Up to four device photos are stored as newline-delimited Contabo S3 keys in {@code imageKeys};
 * presigned URLs are generated on read. Column names mirror V28 (sell_requests) exactly so
 * Hibernate ddl-auto=update stays a no-op.
 */
@Entity
@Table(name = "sell_requests", indexes = {
        @Index(name = "idx_sell_requests_credential", columnList = "credential_id"),
        @Index(name = "idx_sell_requests_status", columnList = "status"),
        @Index(name = "idx_sell_requests_admin_unread", columnList = "admin_unread")
})
public class SellRequest {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Human-friendly display reference, e.g. SR-2026-001. */
    @Column(name = "reference", length = 30)
    private String reference;

    /** Owner — auth_credentials.id (sub). */
    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    /** users.id (uid) — resolved from the credential at creation time. */
    @Column(name = "user_id")
    private UUID userId;

    // ── Device ────────────────────────────────────────────────────────────────

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "brand", nullable = false, length = 255)
    private String brand;

    @Column(name = "model", nullable = false, length = 255)
    private String model;

    /** Optional purchase date — a strong signal for how much the device has depreciated. */
    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    /** Customer's own grading of the device. Procurement re-grades it on arrival. */
    @Enumerated(EnumType.STRING)
    @Column(name = "device_condition", nullable = false, length = 20)
    private DeviceCondition deviceCondition = DeviceCondition.GOOD;

    /** Free text: what's included, any faults, accessories, storage/spec details. */
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    /** Up to four Contabo S3 object keys, newline-delimited (presigned URLs generated on read). */
    @Column(name = "image_keys", columnDefinition = "text")
    private String imageKeys;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SellStatus status = SellStatus.SUBMITTED;

    /** How the device reaches the store (chosen after submission). */
    @Enumerated(EnumType.STRING)
    @Column(name = "inbound_delivery_method", length = 20)
    private SellDeliveryMethod inboundDeliveryMethod;

    /** Store branch chosen for drop-off / courier destination / payout collection. */
    @Column(name = "store_location_id")
    private UUID storeLocationId;

    /** How the device returns to the customer (chosen only after a DECLINE). */
    @Enumerated(EnumType.STRING)
    @Column(name = "return_delivery_method", length = 20)
    private SellDeliveryMethod returnDeliveryMethod;

    /** Courier fee charged for a pickup/return, converted to the customer's currency at choice time. */
    @Column(name = "courier_fee_amount", precision = 12, scale = 2)
    private BigDecimal courierFeeAmount;

    @Column(name = "courier_fee_currency", length = 3)
    private String courierFeeCurrency;

    /** Whether the fee for the currently-selected courier leg has been paid via Paymob. */
    @Column(name = "courier_fee_paid", nullable = false)
    private boolean courierFeePaid = false;

    /**
     * True when the customer paid for a courier pickup and then switched to a free store
     * drop-off: we took money for a collection we will no longer make, so a refund is owed.
     * Surfaced to procurement in the dashboard — nothing refunds automatically.
     */
    @Column(name = "courier_fee_refund_due", nullable = false)
    private boolean courierFeeRefundDue = false;

    /** The inbound method in force before the customer's last change (null if never changed). */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_inbound_delivery_method", length = 20)
    private SellDeliveryMethod previousInboundDeliveryMethod;

    /** When the customer last changed how the device reaches us. Drives the "changed" flag for the team. */
    @Column(name = "inbound_delivery_changed_at")
    private Instant inboundDeliveryChangedAt;

    // ── Offer (what Buyology pays) ────────────────────────────────────────────

    @Column(name = "offer_price", precision = 12, scale = 2)
    private BigDecimal offerPrice;

    @Column(name = "offer_price_currency", length = 3)
    private String offerPriceCurrency;

    /** Free-text validity, e.g. "valid for 7 days". */
    @Column(name = "offer_valid_for", length = 120)
    private String offerValidFor;

    /** Condition procurement graded the device at after inspecting it (may differ from the claim). */
    @Enumerated(EnumType.STRING)
    @Column(name = "inspected_condition", length = 20)
    private DeviceCondition inspectedCondition;

    // ── Payout ────────────────────────────────────────────────────────────────

    /** How the customer takes the money. WALLET_CREDIT is reserved and currently rejected. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payout_method", length = 20)
    private SellPayoutMethod payoutMethod;

    /** Set when the store hands the money over (→ COMPLETED). */
    @Column(name = "paid_out_at")
    private Instant paidOutAt;

    // ── AI preliminary valuation (advisory) ───────────────────────────────────
    // Produced by Claude from the device photos + description + declared condition, priced for the
    // UAE second-hand market and ALWAYS stored in AED. Never binding: procurement still sends the
    // real offer via offerPrice. Null until the async valuation lands (or permanently, if the
    // feature is disabled / the call fails).

    @Column(name = "ai_estimate_min_price", precision = 12, scale = 2)
    private BigDecimal aiEstimateMinPrice;

    @Column(name = "ai_estimate_max_price", precision = 12, scale = 2)
    private BigDecimal aiEstimateMaxPrice;

    /** Always "AED" — the market the valuation is priced for. */
    @Column(name = "ai_estimate_currency", length = 3)
    private String aiEstimateCurrency;

    /** LOW / MEDIUM / HIGH — how much the model trusts its own read of the photos. */
    @Column(name = "ai_estimate_confidence", length = 16)
    private String aiEstimateConfidence;

    /** One- or two-sentence assessment shown to the customer and to procurement. */
    @Column(name = "ai_estimate_summary", columnDefinition = "text")
    private String aiEstimateSummary;

    /** Condition the model reads off the photos — a cross-check on the customer's claim. */
    @Column(name = "ai_estimate_condition", length = 20)
    private String aiEstimateCondition;

    @Column(name = "ai_estimated_at")
    private Instant aiEstimatedAt;

    // ── Admin / notes ─────────────────────────────────────────────────────────

    /** Last admin note / custom update text. */
    @Column(name = "admin_note", columnDefinition = "text")
    private String adminNote;

    /** users.id (uid) of the admin who last updated this request. */
    @Column(name = "updated_by")
    private UUID updatedBy;

    // ── Contact snapshot ──────────────────────────────────────────────────────

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    // ── Notification flags ────────────────────────────────────────────────────

    /** True when the customer has done something procurement hasn't seen yet (drives the badge). */
    @Column(name = "admin_unread", nullable = false)
    private boolean adminUnread = true;

    /** True when procurement has posted an update the customer hasn't opened yet. */
    @Column(name = "customer_unread", nullable = false)
    private boolean customerUnread = false;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Column(name = "device_received_at")
    private Instant deviceReceivedAt;

    @Column(name = "offered_at")
    private Instant offeredAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = SellStatus.SUBMITTED;
        if (this.deviceCondition == null) this.deviceCondition = DeviceCondition.GOOD;
        if (this.submittedAt == null) this.submittedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public UUID getCredentialId() { return credentialId; }
    public void setCredentialId(UUID credentialId) { this.credentialId = credentialId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }

    public DeviceCondition getDeviceCondition() { return deviceCondition; }
    public void setDeviceCondition(DeviceCondition deviceCondition) { this.deviceCondition = deviceCondition; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageKeys() { return imageKeys; }
    public void setImageKeys(String imageKeys) { this.imageKeys = imageKeys; }

    public SellStatus getStatus() { return status; }
    public void setStatus(SellStatus status) { this.status = status; }

    public SellDeliveryMethod getInboundDeliveryMethod() { return inboundDeliveryMethod; }
    public void setInboundDeliveryMethod(SellDeliveryMethod inboundDeliveryMethod) { this.inboundDeliveryMethod = inboundDeliveryMethod; }

    public UUID getStoreLocationId() { return storeLocationId; }
    public void setStoreLocationId(UUID storeLocationId) { this.storeLocationId = storeLocationId; }

    public SellDeliveryMethod getReturnDeliveryMethod() { return returnDeliveryMethod; }
    public void setReturnDeliveryMethod(SellDeliveryMethod returnDeliveryMethod) { this.returnDeliveryMethod = returnDeliveryMethod; }

    public BigDecimal getCourierFeeAmount() { return courierFeeAmount; }
    public void setCourierFeeAmount(BigDecimal courierFeeAmount) { this.courierFeeAmount = courierFeeAmount; }

    public String getCourierFeeCurrency() { return courierFeeCurrency; }
    public void setCourierFeeCurrency(String courierFeeCurrency) { this.courierFeeCurrency = courierFeeCurrency; }

    public boolean isCourierFeePaid() { return courierFeePaid; }
    public void setCourierFeePaid(boolean courierFeePaid) { this.courierFeePaid = courierFeePaid; }

    public boolean isCourierFeeRefundDue() { return courierFeeRefundDue; }
    public void setCourierFeeRefundDue(boolean courierFeeRefundDue) { this.courierFeeRefundDue = courierFeeRefundDue; }

    public SellDeliveryMethod getPreviousInboundDeliveryMethod() { return previousInboundDeliveryMethod; }
    public void setPreviousInboundDeliveryMethod(SellDeliveryMethod m) { this.previousInboundDeliveryMethod = m; }

    public Instant getInboundDeliveryChangedAt() { return inboundDeliveryChangedAt; }
    public void setInboundDeliveryChangedAt(Instant inboundDeliveryChangedAt) { this.inboundDeliveryChangedAt = inboundDeliveryChangedAt; }

    public BigDecimal getOfferPrice() { return offerPrice; }
    public void setOfferPrice(BigDecimal offerPrice) { this.offerPrice = offerPrice; }

    public String getOfferPriceCurrency() { return offerPriceCurrency; }
    public void setOfferPriceCurrency(String offerPriceCurrency) { this.offerPriceCurrency = offerPriceCurrency; }

    public String getOfferValidFor() { return offerValidFor; }
    public void setOfferValidFor(String offerValidFor) { this.offerValidFor = offerValidFor; }

    public DeviceCondition getInspectedCondition() { return inspectedCondition; }
    public void setInspectedCondition(DeviceCondition inspectedCondition) { this.inspectedCondition = inspectedCondition; }

    public SellPayoutMethod getPayoutMethod() { return payoutMethod; }
    public void setPayoutMethod(SellPayoutMethod payoutMethod) { this.payoutMethod = payoutMethod; }

    public Instant getPaidOutAt() { return paidOutAt; }
    public void setPaidOutAt(Instant paidOutAt) { this.paidOutAt = paidOutAt; }

    public BigDecimal getAiEstimateMinPrice() { return aiEstimateMinPrice; }
    public void setAiEstimateMinPrice(BigDecimal aiEstimateMinPrice) { this.aiEstimateMinPrice = aiEstimateMinPrice; }

    public BigDecimal getAiEstimateMaxPrice() { return aiEstimateMaxPrice; }
    public void setAiEstimateMaxPrice(BigDecimal aiEstimateMaxPrice) { this.aiEstimateMaxPrice = aiEstimateMaxPrice; }

    public String getAiEstimateCurrency() { return aiEstimateCurrency; }
    public void setAiEstimateCurrency(String aiEstimateCurrency) { this.aiEstimateCurrency = aiEstimateCurrency; }

    public String getAiEstimateConfidence() { return aiEstimateConfidence; }
    public void setAiEstimateConfidence(String aiEstimateConfidence) { this.aiEstimateConfidence = aiEstimateConfidence; }

    public String getAiEstimateSummary() { return aiEstimateSummary; }
    public void setAiEstimateSummary(String aiEstimateSummary) { this.aiEstimateSummary = aiEstimateSummary; }

    public String getAiEstimateCondition() { return aiEstimateCondition; }
    public void setAiEstimateCondition(String aiEstimateCondition) { this.aiEstimateCondition = aiEstimateCondition; }

    public Instant getAiEstimatedAt() { return aiEstimatedAt; }
    public void setAiEstimatedAt(Instant aiEstimatedAt) { this.aiEstimatedAt = aiEstimatedAt; }

    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public boolean isAdminUnread() { return adminUnread; }
    public void setAdminUnread(boolean adminUnread) { this.adminUnread = adminUnread; }

    public boolean isCustomerUnread() { return customerUnread; }
    public void setCustomerUnread(boolean customerUnread) { this.customerUnread = customerUnread; }

    public Instant getDeviceReceivedAt() { return deviceReceivedAt; }
    public void setDeviceReceivedAt(Instant deviceReceivedAt) { this.deviceReceivedAt = deviceReceivedAt; }

    public Instant getOfferedAt() { return offeredAt; }
    public void setOfferedAt(Instant offeredAt) { this.offeredAt = offeredAt; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
