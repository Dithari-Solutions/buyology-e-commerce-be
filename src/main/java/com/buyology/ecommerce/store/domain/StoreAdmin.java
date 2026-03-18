package com.buyology.ecommerce.store.domain;

import com.buyology.ecommerce.store.enums.StoreAdminRole;
import com.buyology.ecommerce.user.domain.Users;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_admins", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "store_id", "user_id" })
}, indexes = {
        @Index(name = "idx_store_admins_store_id", columnList = "store_id"),
        @Index(name = "idx_store_admins_user_id", columnList = "user_id"),
        @Index(name = "idx_store_admins_active", columnList = "store_id, is_active")
})
public class StoreAdmin {

    // Plain UUID PK — not @EmbeddedId because this entity has additional state
    // fields (storeRole, isActive, assignedBy, assignedAt) beyond the unique key
    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "store_role", nullable = false, length = 20)
    private StoreAdminRole storeRole;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Who assigned this admin (null if seeded or self-assigned)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private Users assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Constructors

    public StoreAdmin() {
    }

    public StoreAdmin(Store store, Users user, StoreAdminRole storeRole, Users assignedBy) {
        this.store = store;
        this.user = user;
        this.storeRole = storeRole;
        this.assignedBy = assignedBy;
    }

    // Lifecycle hooks

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.assignedAt = now;
        if (this.isActive == null) this.isActive = true;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public Users getUser() {
        return user;
    }

    public void setUser(Users user) {
        this.user = user;
    }

    public StoreAdminRole getStoreRole() {
        return storeRole;
    }

    public void setStoreRole(StoreAdminRole storeRole) {
        this.storeRole = storeRole;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Users getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(Users assignedBy) {
        this.assignedBy = assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
