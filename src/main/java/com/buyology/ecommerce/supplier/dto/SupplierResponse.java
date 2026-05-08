package com.buyology.ecommerce.supplier.dto;

import com.buyology.ecommerce.supplier.domain.Supplier;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight view of a {@link Supplier} for the admin detail page.
 */
public class SupplierResponse {

    private UUID id;
    private UUID applicationId;
    private UUID userId;
    private String businessName;
    private String contactEmail;
    private String contactPhone;
    private Supplier.SupplierStatus status;
    private Instant frozenAt;
    private String frozenBy;
    private Instant deletedAt;
    private String deletedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static SupplierResponse from(Supplier s) {
        SupplierResponse r = new SupplierResponse();
        r.id = s.getId();
        r.applicationId = s.getApplicationId();
        r.userId = s.getUserId();
        r.businessName = s.getBusinessName();
        r.contactEmail = s.getContactEmail();
        r.contactPhone = s.getContactPhone();
        r.status = s.getStatus();
        r.frozenAt = s.getFrozenAt();
        r.frozenBy = s.getFrozenBy();
        r.deletedAt = s.getDeletedAt();
        r.deletedBy = s.getDeletedBy();
        r.createdAt = s.getCreatedAt();
        r.updatedAt = s.getUpdatedAt();
        return r;
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public UUID getUserId() { return userId; }
    public String getBusinessName() { return businessName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public Supplier.SupplierStatus getStatus() { return status; }
    public Instant getFrozenAt() { return frozenAt; }
    public String getFrozenBy() { return frozenBy; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
