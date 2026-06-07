package com.buyology.ecommerce.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Carries only the short-lived MFA ticket (used to start enrollment). */
public class MfaTicketRequest {

    @NotBlank
    private String mfaToken;

    public String getMfaToken() { return mfaToken; }
    public void setMfaToken(String mfaToken) { this.mfaToken = mfaToken; }
}
