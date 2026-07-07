package com.buyology.ecommerce.b2b.productrequest.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A B2B product-sourcing request: a logged-in ACTIVE B2B member asks Buyology to source a
 * free-form product (name + quantity, not a catalog product) with an optional image and a
 * message to procurement. Owner is keyed on {@code credentialId} (auth_credentials.id / sub),
 * mirroring {@link com.buyology.ecommerce.b2b.quote.domain.B2bQuote} ownership.
 *
 * Column names mirror V21 (b2b_product_requests) exactly so Hibernate ddl-auto=update stays a
 * no-op.
 */
@Entity
@Table(name = "b2b_product_requests", indexes = {
        @Index(name = "idx_b2b_product_requests_credential", columnList = "credential_id"),
        @Index(name = "idx_b2b_product_requests_status", columnList = "status")
})
public class B2bProductRequest {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Owner — auth_credentials.id (sub). */
    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    /** users.id (uid) — resolved from the credential at creation time. */
    @Column(name = "user_id")
    private UUID userId;

    /** b2b_memberships.id of the ACTIVE membership that owns this request. */
    @Column(name = "membership_id")
    private UUID membershipId;

    /** Free-form product name (not a catalog product id). */
    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    /** Requested quantity — validated >= 5. */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    /** Contabo S3 object key (nullable) — presigned URL generated on read. */
    @Column(name = "image_key", length = 1024)
    private String imageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private B2bProductRequestStatus status = B2bProductRequestStatus.NEW;

    /** Last admin note / custom update text. */
    @Column(name = "procurement_note", columnDefinition = "text")
    private String procurementNote;

    /** users.id (uid) of the admin who last updated this request. */
    @Column(name = "updated_by")
    private UUID updatedBy;

    /** Denormalized member email captured at submit time for notifications. */
    @Column(name = "contact_email", length = 255)
    private String contactEmail;

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
        if (this.status == null) this.status = B2bProductRequestStatus.NEW;
        if (this.submittedAt == null) this.submittedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCredentialId() { return credentialId; }
    public void setCredentialId(UUID credentialId) { this.credentialId = credentialId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getMembershipId() { return membershipId; }
    public void setMembershipId(UUID membershipId) { this.membershipId = membershipId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getImageKey() { return imageKey; }
    public void setImageKey(String imageKey) { this.imageKey = imageKey; }

    public B2bProductRequestStatus getStatus() { return status; }
    public void setStatus(B2bProductRequestStatus status) { this.status = status; }

    public String getProcurementNote() { return procurementNote; }
    public void setProcurementNote(String procurementNote) { this.procurementNote = procurementNote; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
