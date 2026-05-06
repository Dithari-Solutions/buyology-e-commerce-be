package com.buyology.ecommerce.supplier.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers", indexes = {
        @Index(name = "idx_supplier_user_id", columnList = "user_id"),
        @Index(name = "idx_supplier_application_id", columnList = "application_id")
})
public class Supplier {

    public enum SupplierStatus {
        ACTIVE, SUSPENDED, TRASHED
    }

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "application_id", nullable = false, unique = true)
    private UUID applicationId;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "trade_license_key", columnDefinition = "TEXT")
    private String tradeLicenseKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierStatus status = SupplierStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 200)
    private String deletedBy;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Column(name = "frozen_by", length = 200)
    private String frozenBy;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = SupplierStatus.ACTIVE;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public void setApplicationId(UUID applicationId) { this.applicationId = applicationId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getTradeLicenseKey() { return tradeLicenseKey; }
    public void setTradeLicenseKey(String tradeLicenseKey) { this.tradeLicenseKey = tradeLicenseKey; }
    public SupplierStatus getStatus() { return status; }
    public void setStatus(SupplierStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }
    public Instant getFrozenAt() { return frozenAt; }
    public void setFrozenAt(Instant frozenAt) { this.frozenAt = frozenAt; }
    public String getFrozenBy() { return frozenBy; }
    public void setFrozenBy(String frozenBy) { this.frozenBy = frozenBy; }
}
