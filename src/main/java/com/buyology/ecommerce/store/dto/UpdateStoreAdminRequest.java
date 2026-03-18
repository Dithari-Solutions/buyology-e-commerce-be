package com.buyology.ecommerce.store.dto;

import com.buyology.ecommerce.store.enums.StoreAdminRole;

public class UpdateStoreAdminRequest {

    private StoreAdminRole storeRole;
    private Boolean isActive;

    public StoreAdminRole getStoreRole() { return storeRole; }
    public void setStoreRole(StoreAdminRole storeRole) { this.storeRole = storeRole; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
