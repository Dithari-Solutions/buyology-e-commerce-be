package com.buyology.ecommerce.verification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Used for both email start (code ignored) and email check. */
public class VerifyEmailRequest {

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    /** Required only for the check call. */
    @Size(max = 6)
    private String code;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
