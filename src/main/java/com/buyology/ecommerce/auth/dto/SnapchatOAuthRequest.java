package com.buyology.ecommerce.auth.dto;

/**
 * Snapchat Login Kit callback payload (PKCE).
 * The client MUST send the same {@code codeVerifier} that produced the
 * {@code code_challenge} sent to the /accounts/oauth2/auth endpoint, plus
 * the matching {@code redirectUri}.
 */
public class SnapchatOAuthRequest {
    private String code;
    private String codeVerifier;
    private String redirectUri;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getCodeVerifier() { return codeVerifier; }
    public void setCodeVerifier(String codeVerifier) { this.codeVerifier = codeVerifier; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
}
