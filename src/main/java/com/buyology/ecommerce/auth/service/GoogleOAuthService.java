package com.buyology.ecommerce.auth.service;

import java.util.Map;
import java.util.Optional;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import com.buyology.ecommerce.user.domain.Users;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import com.buyology.ecommerce.auth.domain.AuthCredentials;
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

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;

    public GoogleOAuthService(
            RestTemplate restTemplate,
            UserRepository userRepository,
            AuthCredentialRepository authCredentialRepository) {
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
    }

    @Transactional
    public Users processGoogleOAuth(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Authorization code cannot be null or empty");
        }

        // 1️⃣ Exchange code for tokens
        Map<String, Object> tokenResponse;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("code", code);
            map.add("client_id", clientId);
            map.add("client_secret", clientSecret);
            map.add("redirect_uri", redirectUri);
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

        // 3️⃣ Check if credentials already exist
        Optional<AuthCredentials> existingCred = authCredentialRepository
                .findByProviderAndProviderUserId("GOOGLE", googleId);

        if (existingCred.isPresent()) {
            AuthCredentials cred = existingCred.get();

            Users existingUser = userRepository.findById(cred.getUserId())
                    .orElseThrow(() -> new RuntimeException("User linked to credentials not found"));

            if ("SUSPENDED".equals(existingUser.getStatus())) {
                throw new IllegalArgumentException("Your account has been suspended. Please contact support.");
            }

            // Update tokens in case they changed
            cred.setAccessToken(accessToken);
            cred.setRefreshToken(refreshToken);
            authCredentialRepository.save(cred);

            return existingUser;
        }

        // 4️⃣ If not exists, create new Users entity
        Users user = new Users();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user = userRepository.save(user);

        // 5️⃣ Create AuthCredentials linked to Users
        AuthCredentials cred = new AuthCredentials();
        cred.setUserId(user.getId());
        cred.setProvider("GOOGLE");
        cred.setProviderUserId(googleId);
        cred.setEmail(email);
        cred.setAccessToken(accessToken);
        cred.setRefreshToken(refreshToken);
        authCredentialRepository.save(cred);

        return user;
    }

}
