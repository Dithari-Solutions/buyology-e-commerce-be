package com.buyology.ecommerce.sell.dto;

import com.buyology.ecommerce.sell.domain.DeviceCondition;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Procurement's buy-back offer — what Buyology will pay for the device. Sets the request to
 * OFFER_MADE and emails the customer. {@code currency} defaults to AED when omitted;
 * {@code validFor} is free text (e.g. "valid for 7 days"); {@code inspectedCondition} records the
 * grade procurement gave the device after inspecting it.
 */
public class SetSellOfferRequest {

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false, message = "Offer must be greater than 0")
    private BigDecimal price;

    @Size(max = 3)
    private String currency;

    @Size(max = 120)
    private String validFor;

    private DeviceCondition inspectedCondition;

    @Size(max = 4000)
    private String note;

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getValidFor() { return validFor; }
    public void setValidFor(String validFor) { this.validFor = validFor; }
    public DeviceCondition getInspectedCondition() { return inspectedCondition; }
    public void setInspectedCondition(DeviceCondition inspectedCondition) { this.inspectedCondition = inspectedCondition; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
