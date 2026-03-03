package com.buyology.ecommerce.auth.dto;

public class SignInResponse {

    private String accessToken;
    private long expiresIn; // in seconds

    public SignInResponse() {
    }

    public SignInResponse(String accessToken, long expiresIn) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
    }

    // ------------------------
    // Getters & Setters
    // ------------------------
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }
}
