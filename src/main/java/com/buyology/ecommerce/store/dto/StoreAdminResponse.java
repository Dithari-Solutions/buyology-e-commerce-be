package com.buyology.ecommerce.store.dto;

import com.buyology.ecommerce.store.enums.StoreAdminRole;

import java.time.Instant;
import java.util.UUID;

public class StoreAdminResponse {

    private UUID id;
    private UUID storeId;
    private String storeName;
    private UUID userId;
    private String userFirstName;
    private String userLastName;
    private StoreAdminRole storeRole;
    private Boolean isActive;
    private UUID assignedById;
    private Instant assignedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getUserFirstName() { return userFirstName; }
    public void setUserFirstName(String userFirstName) { this.userFirstName = userFirstName; }

    public String getUserLastName() { return userLastName; }
    public void setUserLastName(String userLastName) { this.userLastName = userLastName; }

    public StoreAdminRole getStoreRole() { return storeRole; }
    public void setStoreRole(StoreAdminRole storeRole) { this.storeRole = storeRole; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public UUID getAssignedById() { return assignedById; }
    public void setAssignedById(UUID assignedById) { this.assignedById = assignedById; }

    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
