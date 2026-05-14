package com.buyology.ecommerce.auth.dto;

/**
 * Facebook OAuth callback payload.
 * Web flow: send {@code code} (+ optional {@code redirectUri} override).
 * Mobile / native SDK flow: send {@code accessToken} obtained from the FB SDK.
 */
public class FacebookOAuthRequest {
    private String code;
    private String redirectUri;
    private String accessToken;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
}
