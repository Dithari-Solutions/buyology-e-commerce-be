package com.buyology.ecommerce.supplier.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A supplier-initiated, superadmin-approved change to one of the supplier's own
 * products. Suppliers cannot edit/delete/restore directly — they file a request
 * here which a superadmin approves (action applied) or rejects.
 */
@Entity
@Table(name = "supplier_product_change_requests", indexes = {
        @Index(name = "idx_spcr_supplier_id", columnList = "supplier_id"),
        @Index(name = "idx_spcr_product_id", columnList = "product_id"),
        @Index(name = "idx_spcr_status", columnList = "status")
})
public class SupplierProductChangeRequest {

    public enum Action { EDIT, DELETE, RESTORE }

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20, nullable = false)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private Status status = Status.PENDING;

    /** For EDIT requests: JSON of the proposed UpdateProductRequest. Null otherwise. */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @PrePersist
    void onCreate() {
        this.requestedAt = Instant.now();
        if (this.status == null) this.status = Status.PENDING;
    }

    // Getters & setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }
}
