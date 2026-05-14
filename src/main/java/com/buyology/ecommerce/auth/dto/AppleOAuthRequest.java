package com.buyology.ecommerce.auth.dto;

public class AppleOAuthRequest {
    private String code;
    private String identityToken;
    private String firstName;
    private String lastName;

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
