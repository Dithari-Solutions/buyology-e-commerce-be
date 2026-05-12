package com.buyology.ecommerce.payout.domain;

import com.buyology.ecommerce.payout.enums.PayoutRequestStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Supplier-initiated request to be paid out their accumulated revenue. Admin
 * disburses funds manually outside the system and marks the request PAID.
 */
@Entity
@Table(name = "payout_requests", indexes = {
        @Index(name = "idx_payout_requests_supplier", columnList = "supplier_id"),
        @Index(name = "idx_payout_requests_status", columnList = "status")
})
public class PayoutRequest {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "amount_aed", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountAed;

    /** Frozen JSON copy of {@code SupplierPayoutAccount} at submit time for audit. */
    @Column(name = "account_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String accountSnapshotJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutRequestStatus status = PayoutRequestStatus.PENDING;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "paid_by_admin_id")
    private UUID paidByAdminId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = PayoutRequestStatus.PENDING;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }

    public BigDecimal getAmountAed() { return amountAed; }
    public void setAmountAed(BigDecimal amountAed) { this.amountAed = amountAed; }

    public String getAccountSnapshotJson() { return accountSnapshotJson; }
    public void setAccountSnapshotJson(String accountSnapshotJson) { this.accountSnapshotJson = accountSnapshotJson; }

    public PayoutRequestStatus getStatus() { return status; }
    public void setStatus(PayoutRequestStatus status) { this.status = status; }

    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }

    public UUID getPaidByAdminId() { return paidByAdminId; }
    public void setPaidByAdminId(UUID paidByAdminId) { this.paidByAdminId = paidByAdminId; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
