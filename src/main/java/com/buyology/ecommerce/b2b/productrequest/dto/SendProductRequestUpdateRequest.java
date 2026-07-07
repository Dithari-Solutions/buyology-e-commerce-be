package com.buyology.ecommerce.b2b.productrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Procurement sends a free-form update message to the member (emailed, no status change). */
public class SendProductRequestUpdateRequest {

    @NotBlank
    @Size(max = 4000)
    private String message;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
