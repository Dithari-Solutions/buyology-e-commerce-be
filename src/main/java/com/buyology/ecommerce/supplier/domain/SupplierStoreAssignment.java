package com.buyology.ecommerce.supplier.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "supplier_store_assignments")
public class SupplierStoreAssignment {

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "supplier_id", nullable = false)
        private UUID supplierId;

        @Column(name = "store_id", nullable = false)
        private UUID storeId;

        public Id() {}

        public Id(UUID supplierId, UUID storeId) {
            this.supplierId = supplierId;
            this.storeId = storeId;
        }

        public UUID getSupplierId() { return supplierId; }
        public UUID getStoreId() { return storeId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id)) return false;
            Id that = (Id) o;
            return Objects.equals(supplierId, that.supplierId) && Objects.equals(storeId, that.storeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(supplierId, storeId);
        }
    }

    @EmbeddedId
    private Id id;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @PrePersist
    protected void onCreate() {
        assignedAt = Instant.now();
    }

    public SupplierStoreAssignment() {}

    public SupplierStoreAssignment(UUID supplierId, UUID storeId) {
        this.id = new Id(supplierId, storeId);
    }

    public Id getId() { return id; }
    public void setId(Id id) { this.id = id; }
    public UUID getSupplierId() { return id != null ? id.getSupplierId() : null; }
    public UUID getStoreId() { return id != null ? id.getStoreId() : null; }
    public Instant getAssignedAt() { return assignedAt; }
}
