package com.buyology.ecommerce.store.dto;

import com.buyology.ecommerce.store.enums.StoreAdminRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class AssignStoreAdminRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Store role is required")
    private StoreAdminRole storeRole;

    private UUID assignedById;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public StoreAdminRole getStoreRole() { return storeRole; }
    public void setStoreRole(StoreAdminRole storeRole) { this.storeRole = storeRole; }

    public UUID getAssignedById() { return assignedById; }
    public void setAssignedById(UUID assignedById) { this.assignedById = assignedById; }
}
