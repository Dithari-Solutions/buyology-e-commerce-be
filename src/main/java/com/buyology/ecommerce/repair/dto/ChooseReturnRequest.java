package com.buyology.ecommerce.repair.dto;

import com.buyology.ecommerce.repair.domain.RepairDeliveryMethod;
import jakarta.validation.constraints.NotNull;

/**
 * Customer's choice for how a declined device is returned. {@code method} must be
 * COURIER_RETURN (20 AED) or STORE_PICKUP (free). {@code currency} is the customer's preferred
 * currency the 20 AED courier fee should be converted into (optional; defaults to AED).
 */
public class ChooseReturnRequest {

    @NotNull
    private RepairDeliveryMethod method;

    private String currency;

    public RepairDeliveryMethod getMethod() { return method; }
    public void setMethod(RepairDeliveryMethod method) { this.method = method; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
