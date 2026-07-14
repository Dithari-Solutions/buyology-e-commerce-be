package com.buyology.ecommerce.repair.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A customer device-repair request. Any logged-in customer can open one (there is no B2B
 * membership gate, unlike {@link com.buyology.ecommerce.b2b.productrequest.domain.B2bProductRequest}).
 * Ownership is keyed on {@code credentialId} (auth_credentials.id / sub); the users.id (uid) is
 * denormalized alongside it. Contact email/phone are snapshotted from the customer's profile at
 * submit time so notifications don't need a live lookup.
 *
 * Up to four problem images are stored as newline-delimited Contabo S3 keys in {@code imageKeys};
 * presigned URLs are generated on read. Column names mirror V23 (repair_requests) exactly so
 * Hibernate ddl-auto=update stays a no-op.
 */
@Entity
@Table(name = "repair_requests", indexes = {
        @Index(name = "idx_repair_requests_credential", columnList = "credential_id"),
        @Index(name = "idx_repair_requests_status", columnList = "status"),
        @Index(name = "idx_repair_requests_admin_unread", columnList = "admin_unread")
})
public class RepairRequest {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Human-friendly display reference, e.g. RR-2026-001. */
    @Column(name = "reference", length = 30)
    private String reference;

    /** Owner — auth_credentials.id (sub). */
    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    /** users.id (uid) — resolved from the credential at creation time. */
    @Column(name = "user_id")
    private UUID userId;

    // ── Device / problem ──────────────────────────────────────────────────────

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "brand", nullable = false, length = 255)
    private String brand;

    @Column(name = "model", nullable = false, length = 255)
    private String model;

    /** Optional purchase date. */
    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    /** Up to four Contabo S3 object keys, newline-delimited (presigned URLs generated on read). */
    @Column(name = "image_keys", columnDefinition = "text")
    private String imageKeys;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RepairStatus status = RepairStatus.SUBMITTED;

    /** How the device reaches the store (chosen after submission). */
    @Enumerated(EnumType.STRING)
    @Column(name = "inbound_delivery_method", length = 20)
    private RepairDeliveryMethod inboundDeliveryMethod;

    /** Store branch chosen for drop-off / courier destination. */
    @Column(name = "store_location_id")
    private UUID storeLocationId;

    /** How the device returns to the customer (chosen only after a DECLINE). */
    @Enumerated(EnumType.STRING)
    @Column(name = "return_delivery_method", length = 20)
    private RepairDeliveryMethod returnDeliveryMethod;

    /** Courier fee charged for a pickup/return, converted to the customer's currency at choice time. */
    @Column(name = "courier_fee_amount", precision = 12, scale = 2)
    private BigDecimal courierFeeAmount;

    @Column(name = "courier_fee_currency", length = 3)
    private String courierFeeCurrency;

    /** Whether the fee for the currently-selected courier leg has been paid via Paymob. */
    @Column(name = "courier_fee_paid", nullable = false)
    private boolean courierFeePaid = false;

    // ── Pricing ───────────────────────────────────────────────────────────────

    @Column(name = "estimated_price", precision = 12, scale = 2)
    private BigDecimal estimatedPrice;

    @Column(name = "estimated_price_currency", length = 3)
    private String estimatedPriceCurrency;

    /** Free-text turnaround, e.g. "3-5 business days". */
    @Column(name = "estimated_time", length = 120)
    private String estimatedTime;

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

    /** True when the customer has done something the repair team hasn't seen yet (drives the dashboard badge). */
    @Column(name = "admin_unread", nullable = false)
    private boolean adminUnread = true;

    /** True when the team has posted an update the customer hasn't opened yet. */
    @Column(name = "customer_unread", nullable = false)
    private boolean customerUnread = false;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Column(name = "device_received_at")
    private Instant deviceReceivedAt;

    @Column(name = "priced_at")
    private Instant pricedAt;

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
        if (this.status == null) this.status = RepairStatus.SUBMITTED;
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

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageKeys() { return imageKeys; }
    public void setImageKeys(String imageKeys) { this.imageKeys = imageKeys; }

    public RepairStatus getStatus() { return status; }
    public void setStatus(RepairStatus status) { this.status = status; }

    public RepairDeliveryMethod getInboundDeliveryMethod() { return inboundDeliveryMethod; }
    public void setInboundDeliveryMethod(RepairDeliveryMethod inboundDeliveryMethod) { this.inboundDeliveryMethod = inboundDeliveryMethod; }

    public UUID getStoreLocationId() { return storeLocationId; }
    public void setStoreLocationId(UUID storeLocationId) { this.storeLocationId = storeLocationId; }

    public RepairDeliveryMethod getReturnDeliveryMethod() { return returnDeliveryMethod; }
    public void setReturnDeliveryMethod(RepairDeliveryMethod returnDeliveryMethod) { this.returnDeliveryMethod = returnDeliveryMethod; }

    public BigDecimal getCourierFeeAmount() { return courierFeeAmount; }
    public void setCourierFeeAmount(BigDecimal courierFeeAmount) { this.courierFeeAmount = courierFeeAmount; }

    public String getCourierFeeCurrency() { return courierFeeCurrency; }
    public void setCourierFeeCurrency(String courierFeeCurrency) { this.courierFeeCurrency = courierFeeCurrency; }

    public boolean isCourierFeePaid() { return courierFeePaid; }
    public void setCourierFeePaid(boolean courierFeePaid) { this.courierFeePaid = courierFeePaid; }

    public BigDecimal getEstimatedPrice() { return estimatedPrice; }
    public void setEstimatedPrice(BigDecimal estimatedPrice) { this.estimatedPrice = estimatedPrice; }

    public String getEstimatedPriceCurrency() { return estimatedPriceCurrency; }
    public void setEstimatedPriceCurrency(String estimatedPriceCurrency) { this.estimatedPriceCurrency = estimatedPriceCurrency; }

    public String getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(String estimatedTime) { this.estimatedTime = estimatedTime; }

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

    public Instant getPricedAt() { return pricedAt; }
    public void setPricedAt(Instant pricedAt) { this.pricedAt = pricedAt; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
