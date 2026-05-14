package com.buyology.ecommerce.auth.service;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import com.buyology.ecommerce.user.domain.Users;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.dto.GoogleOAuthRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.web.client.HttpClientErrorException;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;

@Service
public class GoogleOAuthService {

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    @Value("${google.redirect-uri}")
    private String redirectUri;

    @Value("${google.allowed-audiences:}")
    private String allowedAudiencesCsv;

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final ObjectMapper objectMapper;

    public GoogleOAuthService(
            RestTemplate restTemplate,
            UserRepository userRepository,
            AuthCredentialRepository authCredentialRepository,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Native flow: client supplies an ID token from the Google Sign-In SDK.
     * We verify it via Google's tokeninfo endpoint and accept only when the
     * audience matches our web client-id or one of the configured native audiences.
     */
    @Transactional
    public AuthCredentials processGoogleNativeIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("idToken is required");
        }
        Map<String, Object> claims;
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(
                    "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = resp.getBody() == null ? Map.of() : resp.getBody();
            claims = body;
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("Google idToken validation failed: " + e.getResponseBodyAsString());
        }

        String aud = (String) claims.get("aud");
        Set<String> allowed = new HashSet<>();
        allowed.add(clientId);
        if (allowedAudiencesCsv != null && !allowedAudiencesCsv.isBlank()) {
            allowed.addAll(Arrays.stream(allowedAudiencesCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        }
        if (aud == null || !allowed.contains(aud)) {
            throw new IllegalArgumentException("Google idToken audience mismatch");
        }

        String googleId = (String) claims.get("sub");
        String email = (String) claims.get("email");
        String firstName = (String) claims.get("given_name");
        String lastName = (String) claims.get("family_name");
        if (googleId == null) {
            throw new RuntimeException("Google user ID is missing");
        }
        return upsertCredentials(googleId, email, firstName, lastName, idToken, null);
    }

    @Transactional
    public AuthCredentials processGoogleOAuth(String code) {
        return processGoogleOAuth(code, null);
    }

    @Transactional
    public AuthCredentials processGoogleOAuth(String code, String redirectUriOverride) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Authorization code cannot be null or empty");
        }
        String effectiveRedirect = (redirectUriOverride != null && !redirectUriOverride.isBlank())
                ? redirectUriOverride
                : redirectUri;

        // 1️⃣ Exchange code for tokens
        Map<String, Object> tokenResponse;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("code", code);
            map.add("client_id", clientId);
            map.add("client_secret", clientSecret);
            map.add("redirect_uri", effectiveRedirect);
            map.add("grant_type", "authorization_code");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://oauth2.googleapis.com/token", request, Map.class);

            tokenResponse = response.getBody();
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            throw new IllegalArgumentException("Google OAuth failed: " + body);
        }

        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = (String) tokenResponse.get("refresh_token");

        // 2️⃣ Get user info from Google
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(accessToken);
        HttpEntity<String> userRequest = new HttpEntity<>(userHeaders);

        Map<String, Object> userInfo;
        try {
            ResponseEntity<Map> userInfoResp = restTemplate.exchange(
                    "https://www.googleapis.com/oauth2/v2/userinfo",
                    HttpMethod.GET,
                    userRequest,
                    Map.class);
            userInfo = userInfoResp.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch user info from Google", e);
        }

        String email = (String) userInfo.get("email");
        String googleId = (String) userInfo.get("id");
        String firstName = (String) userInfo.get("given_name");
        String lastName = (String) userInfo.get("family_name");

        if (googleId == null) {
            throw new RuntimeException("Google user ID is missing");
        }
        return upsertCredentials(googleId, email, firstName, lastName, accessToken, refreshToken);
    }

    private AuthCredentials upsertCredentials(String googleId, String email, String firstName,
                                              String lastName, String accessToken, String refreshToken) {
        Optional<AuthCredentials> existingCred = authCredentialRepository
                .findByProviderAndProviderUserId("GOOGLE", googleId);

        if (existingCred.isPresent()) {
            AuthCredentials cred = existingCred.get();
            Users existingUser = userRepository.findById(cred.getUserId())
                    .orElseThrow(() -> new RuntimeException("User linked to credentials not found"));
            if ("SUSPENDED".equals(existingUser.getStatus())) {
                throw new IllegalArgumentException("Your account has been suspended. Please contact support.");
            }
            cred.setAccessToken(accessToken);
            if (refreshToken != null) cred.setRefreshToken(refreshToken);
            return authCredentialRepository.save(cred);
        }

        Users user = new Users();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUserType(Users.UserType.CUSTOMER);
        user = userRepository.save(user);

        AuthCredentials cred = new AuthCredentials();
        cred.setUserId(user.getId());
        cred.setProvider("GOOGLE");
        cred.setProviderUserId(googleId);
        cred.setEmail(email);
        cred.setAccessToken(accessToken);
        cred.setRefreshToken(refreshToken);
        return authCredentialRepository.save(cred);
    }

}
