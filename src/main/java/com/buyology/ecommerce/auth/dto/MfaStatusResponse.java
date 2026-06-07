package com.buyology.ecommerce.auth.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 2FA status for the authenticated user's security settings page. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MfaStatusResponse {

    private boolean enabled;
    private Instant enrolledAt;
    private Integer unusedRecoveryCodes;

    public MfaStatusResponse() {}

    public MfaStatusResponse(boolean enabled, Instant enrolledAt, Integer unusedRecoveryCodes) {
        this.enabled = enabled;
        this.enrolledAt = enrolledAt;
        this.unusedRecoveryCodes = unusedRecoveryCodes;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(Instant enrolledAt) { this.enrolledAt = enrolledAt; }

    public Integer getUnusedRecoveryCodes() { return unusedRecoveryCodes; }
    public void setUnusedRecoveryCodes(Integer unusedRecoveryCodes) { this.unusedRecoveryCodes = unusedRecoveryCodes; }
}
