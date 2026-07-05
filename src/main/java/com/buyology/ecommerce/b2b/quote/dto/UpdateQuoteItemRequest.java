package com.buyology.ecommerce.b2b.quote.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Change the quantity of an existing B2B cart line. */
public class UpdateQuoteItemRequest {

    @NotNull
    @Min(1) // >= B2B_MIN_QTY_PER_LINE is enforced in the service
    private Integer quantity;

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
