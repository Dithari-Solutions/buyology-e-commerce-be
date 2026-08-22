package com.buyology.ecommerce.auth.dto;

public class AppleOAuthRequest {
    private String code;
    /**
     * The redirect URI the CLIENT used when opening Apple's authorize page. Optional; falls back
     * to the server-configured apple.redirect-uri.
     *
     * <p>Exists because two storefronts now sign in with Apple (buyology.online and
     * v2.buyology.online) and Apple's token exchange requires redirect_uri to EXACTLY match the
     * one used at authorize time — a single server-side value can only ever serve one of them.
     * Mirrors GoogleOAuthRequest. Not an open redirect: Apple only exchanges a code whose
     * registered Return URL matches, so an attacker-supplied URI just fails the exchange.
     */
    private String redirectUri;
    private String identityToken;
    private String firstName;
    private String lastName;

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getIdentityToken() { return identityToken; }
    public void setIdentityToken(String identityToken) { this.identityToken = identityToken; }

    // Getters and setters
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}
