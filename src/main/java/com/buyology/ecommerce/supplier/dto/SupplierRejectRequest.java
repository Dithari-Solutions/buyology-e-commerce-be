package com.buyology.ecommerce.supplier.dto;

import jakarta.validation.constraints.NotBlank;

public class SupplierRejectRequest {

    @NotBlank
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
