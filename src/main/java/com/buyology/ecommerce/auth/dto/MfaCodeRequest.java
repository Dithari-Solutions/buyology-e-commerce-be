package com.buyology.ecommerce.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Ticket + code pair. Used by enroll/confirm and login/verify. The {@code code}
 * is either a 6-digit TOTP code or a recovery code (verify step only).
 */
public class MfaCodeRequest {

    @NotBlank
    private String mfaToken;

    @NotBlank
    private String code;

    public String getMfaToken() { return mfaToken; }
    public void setMfaToken(String mfaToken) { this.mfaToken = mfaToken; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
