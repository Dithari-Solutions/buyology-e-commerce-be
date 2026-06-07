package com.buyology.ecommerce.auth.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Sign-in result. On a normal success {@code accessToken} is populated.
 *
 * When a privileged account (admin/supplier) requires two-factor auth, the token
 * fields are left null and instead one of the MFA flags is set together with a
 * short-lived {@code mfaToken}:
 *  - {@code mfaSetupRequired} — the account has not enrolled yet (mandatory): the
 *    client must run the enrollment flow with the ticket.
 *  - {@code mfaRequired} — 2FA is enabled: the client must submit a TOTP code with
 *    the ticket to complete sign-in.
 *
 * {@code recoveryCodes} is populated exactly once, on enrollment confirmation, so
 * the user can save their one-time backup codes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignInResponse {

    private String accessToken;
    private Long expiresIn; // in seconds

    private Boolean mfaRequired;
    private Boolean mfaSetupRequired;
    private String mfaToken;

    private List<String> recoveryCodes;

    public SignInResponse() {
    }

    public SignInResponse(String accessToken, long expiresIn) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
    }

    /** Build an MFA challenge response (no tokens issued yet). */
    public static SignInResponse mfaChallenge(boolean setupRequired, String mfaToken) {
        SignInResponse r = new SignInResponse();
        if (setupRequired) {
            r.mfaSetupRequired = true;
        } else {
            r.mfaRequired = true;
        }
        r.mfaToken = mfaToken;
        return r;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public Boolean getMfaRequired() {
        return mfaRequired;
    }

    public void setMfaRequired(Boolean mfaRequired) {
        this.mfaRequired = mfaRequired;
    }

    public Boolean getMfaSetupRequired() {
        return mfaSetupRequired;
    }

    public void setMfaSetupRequired(Boolean mfaSetupRequired) {
        this.mfaSetupRequired = mfaSetupRequired;
    }

    public String getMfaToken() {
        return mfaToken;
    }

    public void setMfaToken(String mfaToken) {
        this.mfaToken = mfaToken;
    }

    public List<String> getRecoveryCodes() {
        return recoveryCodes;
    }

    public void setRecoveryCodes(List<String> recoveryCodes) {
        this.recoveryCodes = recoveryCodes;
    }
}
