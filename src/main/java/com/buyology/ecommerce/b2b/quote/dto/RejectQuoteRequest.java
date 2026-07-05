package com.buyology.ecommerce.b2b.quote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Procurement declines to quote a SUBMITTED request. */
public class RejectQuoteRequest {

    @NotBlank
    @Size(max = 2000)
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
