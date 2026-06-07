package com.buyology.ecommerce.verification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Used for both phone start (code ignored) and phone check. */
public class VerifyPhoneRequest {

    // E.164 format required: +971501234567
    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$",
            message = "Phone number must be in E.164 format, e.g. +971501234567")
    private String phoneNumber;

    /** Required only for the check call. */
    @Size(max = 10)
    private String code;

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
