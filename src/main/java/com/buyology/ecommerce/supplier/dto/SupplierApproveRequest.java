package com.buyology.ecommerce.supplier.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public class SupplierApproveRequest {

    @NotEmpty
    private List<UUID> storeIds;

    public List<UUID> getStoreIds() { return storeIds; }
    public void setStoreIds(List<UUID> storeIds) { this.storeIds = storeIds; }
}
