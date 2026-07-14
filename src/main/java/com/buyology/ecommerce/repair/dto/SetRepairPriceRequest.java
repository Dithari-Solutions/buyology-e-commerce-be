package com.buyology.ecommerce.repair.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Repair team's fixing-price quote. Sets the request to PRICE_ESTIMATED and emails the customer.
 * {@code currency} defaults to AED when omitted; {@code estimatedTime} is free text
 * (e.g. "3-5 business days").
 */
public class SetRepairPriceRequest {

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;

    @Size(max = 3)
    private String currency;

    @Size(max = 120)
    private String estimatedTime;

    @Size(max = 4000)
    private String note;

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(String estimatedTime) { this.estimatedTime = estimatedTime; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
