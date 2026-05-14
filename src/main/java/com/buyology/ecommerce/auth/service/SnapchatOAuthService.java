package com.buyology.ecommerce.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.dto.SnapchatOAuthRequest;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.infrastructure.config.SnapchatProperties;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;

/**
 * Snapchat Login Kit (OAuth2 / PKCE).
 *
 * Snapchat does not provide an email. We only get an opaque external ID
 * plus a display name, so we treat first-time logins as new users without
 * email and rely on the user to add one later from their profile.
 *
 * Docs: https://developers.snap.com/api/docs/login-kit
 */
@Service
public class SnapchatOAuthService {

    private static final Logger log = LoggerFactory.getLogger(SnapchatOAuthService.class);
    private static final String TOKEN_URL = "https://accounts.snapchat.com/login/oauth2/access_token";
    private static final String ME_URL = "https://kit.snapchat.com/v1/me";

    private final SnapchatProperties snapchatProperties;
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;

    public SnapchatOAuthService(
            SnapchatProperties snapchatProperties,
            RestTemplate restTemplate,
            UserRepository userRepository,
            AuthCredentialRepository authCredentialRepository) {
        this.snapchatProperties = snapchatProperties;
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
    }

    @Transactional
    public AuthCredentials processSnapchatOAuth(SnapchatOAuthRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("Authorization code is required");
        }
        if (request.getCodeVerifier() == null || request.getCodeVerifier().isBlank()) {
            throw new IllegalArgumentException("PKCE code_verifier is required");
        }

        String redirectUri = (request.getRedirectUri() != null && !request.getRedirectUri().isBlank())
                ? request.getRedirectUri()
                : snapchatProperties.getRedirectUri();

        Map<String, Object> tokenResp = exchangeCodeForToken(request.getCode(), request.getCodeVerifier(), redirectUri);
        String accessToken = (String) tokenResp.get("access_token");
        String refreshToken = (String) tokenResp.get("refresh_token");
        if (accessToken == null) {
            throw new RuntimeException("Snapchat returned no access_token");
        }

        Map<String, Object> me = fetchMe(accessToken);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) me.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> meObj = data == null ? null : (Map<String, Object>) data.get("me");
        if (meObj == null) {
            throw new RuntimeException("Snapchat /me returned no profile");
        }
        String externalId = (String) meObj.get("externalId");
        String displayName = (String) meObj.get("displayName");
        if (externalId == null) {
            throw new RuntimeException("Snapchat external_id is missing");
        }

        Optional<AuthCredentials> existing = authCredentialRepository
                .findByProviderAndProviderUserId("SNAPCHAT", externalId);

        if (existing.isPresent()) {
            AuthCredentials cred = existing.get();
            Users existingUser = userRepository.findById(cred.getUserId())
                    .orElseThrow(() -> new RuntimeException("User linked to credentials not found"));
            if ("SUSPENDED".equals(existingUser.getStatus())) {
                throw new IllegalArgumentException("Your account has been suspended. Please contact support.");
            }
            cred.setAccessToken(accessToken);
            cred.setRefreshToken(refreshToken);
            return authCredentialRepository.save(cred);
        }

        Users user = new Users();
        if (displayName != null) {
            String[] parts = displayName.trim().split("\\s+", 2);
            user.setFirstName(parts[0]);
            if (parts.length > 1) user.setLastName(parts[1]);
        }
        user.setUserType(Users.UserType.CUSTOMER);
        user = userRepository.save(user);

        AuthCredentials cred = new AuthCredentials();
        cred.setUserId(user.getId());
        cred.setProvider("SNAPCHAT");
        cred.setProviderUserId(externalId);
        cred.setAccessToken(accessToken);
        cred.setRefreshToken(refreshToken);
        return authCredentialRepository.save(cred);
    }

    private Map<String, Object> exchangeCodeForToken(String code, String codeVerifier, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String basic = Base64.getEncoder().encodeToString(
                (snapchatProperties.getClientId() + ":" + snapchatProperties.getClientSecret())
                        .getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + basic);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("code_verifier", codeVerifier);
        form.add("client_id", snapchatProperties.getClientId());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(TOKEN_URL, entity, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = resp.getBody() == null ? Map.of() : resp.getBody();
            return body;
        } catch (HttpClientErrorException e) {
            log.error("Snapchat token exchange failed: {}", e.getResponseBodyAsString());
            throw new IllegalArgumentException("Snapchat OAuth failed: " + e.getResponseBodyAsString());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchMe(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        String query = "{\"query\":\"{me{externalId displayName}}\"}";
        HttpEntity<String> entity = new HttpEntity<>(query, headers);
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(ME_URL, HttpMethod.POST, entity, Map.class);
            return resp.getBody() == null ? Map.of() : resp.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Snapchat /me failed: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch user info from Snapchat");
        }
    }
}
