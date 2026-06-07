package com.buyology.ecommerce.auth.dto;

/**
 * Data the client needs to display the enrollment screen: a scannable QR image
 * plus the base32 secret for manual entry.
 */
public class MfaEnrollStartResponse {

    private String qrDataUri;   // data:image/png;base64,... — drop straight into <img src>
    private String secret;      // base32, for manual key entry
    private String issuer;
    private String account;     // the email shown in the authenticator app

    public MfaEnrollStartResponse() {}

    public MfaEnrollStartResponse(String qrDataUri, String secret, String issuer, String account) {
        this.qrDataUri = qrDataUri;
        this.secret = secret;
        this.issuer = issuer;
        this.account = account;
    }

    public String getQrDataUri() { return qrDataUri; }
    public void setQrDataUri(String qrDataUri) { this.qrDataUri = qrDataUri; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
}
