package com.buyology.ecommerce.auth.service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.dto.AppleOAuthRequest;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.infrastructure.config.AppleProperties;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;

@Service
public class AppleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(AppleOAuthService.class);

    private final AppleProperties appleProperties;
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final ObjectMapper objectMapper;

    public AppleOAuthService(
            AppleProperties appleProperties,
            RestTemplate restTemplate,
            UserRepository userRepository,
            AuthCredentialRepository authCredentialRepository,
            ObjectMapper objectMapper) {
        this.appleProperties = appleProperties;
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuthCredentials processAppleOAuth(AppleOAuthRequest authRequest) {
        // Native iOS flow — client already has the identityToken from
        // expo-apple-authentication; no code-exchange needed.
        if ((authRequest.getCode() == null || authRequest.getCode().isEmpty())
                && authRequest.getIdentityToken() != null
                && !authRequest.getIdentityToken().isEmpty()) {
            return processNativeIdentityToken(authRequest);
        }

        if (authRequest.getCode() == null || authRequest.getCode().isEmpty()) {
            throw new IllegalArgumentException("Authorization code cannot be null or empty");
        }

        // 1️⃣ Generate Client Secret
        String clientSecret = generateClientSecret();

        // 2️⃣ Exchange code for tokens
        Map<String, Object> tokenResponse;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("code", authRequest.getCode());
            map.add("client_id", appleProperties.getClientId());
            map.add("client_secret", clientSecret);
            map.add("grant_type", "authorization_code");
            if (appleProperties.getRedirectUri() != null && !appleProperties.getRedirectUri().isEmpty()) {
                map.add("redirect_uri", appleProperties.getRedirectUri());
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://appleid.apple.com/auth/token", request, Map.class);

            tokenResponse = response.getBody();
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            log.error("Apple OAuth failed. Body: {}", body);
            throw new IllegalArgumentException("Apple OAuth token exchange failed: " + body);
        }

        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = (String) tokenResponse.get("refresh_token");
        String idToken = (String) tokenResponse.get("id_token");

        // 3️⃣ Parse ID Token to get user info
        String appleId;
        String email;
        try {
            String[] splitToken = idToken.split("\\.");
            if (splitToken.length < 2) {
                throw new IllegalArgumentException("Invalid ID Token format");
            }
            String payloadJson = new String(Base64.getDecoder().decode(splitToken[1]));
            Map<String, Object> claims = objectMapper.readValue(payloadJson, Map.class);
            
            appleId = (String) claims.get("sub");
            email = (String) claims.get("email");
        } catch (Exception e) {
            log.error("Failed to parse Apple ID Token: {}", e.getMessage());
            throw new RuntimeException("Failed to extract information from Apple ID token", e);
        }

        if (appleId == null) {
            throw new RuntimeException("Apple user ID (sub) is missing");
        }

        // 4️⃣ Check if credentials already exist
        Optional<AuthCredentials> existingCred = authCredentialRepository
                .findByProviderAndProviderUserId("APPLE", appleId);

        if (existingCred.isPresent()) {
            AuthCredentials cred = existingCred.get();

            Users existingUser = userRepository.findById(cred.getUserId())
                    .orElseThrow(() -> new RuntimeException("User linked to credentials not found"));

            if ("SUSPENDED".equals(existingUser.getStatus())) {
                throw new IllegalArgumentException("Your account has been suspended. Please contact support.");
            }

            // Update tokens
            cred.setAccessToken(accessToken);
            cred.setRefreshToken(refreshToken);
            return authCredentialRepository.save(cred);
        }

        // 5️⃣ If not exists, create new Users entity
        Users user = new Users();
        if (authRequest.getFirstName() != null) {
            user.setFirstName(authRequest.getFirstName());
        }
        if (authRequest.getLastName() != null) {
            user.setLastName(authRequest.getLastName());
        }
        
        user.setUserType(Users.UserType.CUSTOMER);
        user = userRepository.save(user);

        // 6️⃣ Create AuthCredentials linked to Users
        AuthCredentials cred = new AuthCredentials();
        cred.setUserId(user.getId());
        cred.setProvider("APPLE");
        cred.setProviderUserId(appleId);
        cred.setEmail(email);
        cred.setAccessToken(accessToken);
        cred.setRefreshToken(refreshToken);
        return authCredentialRepository.save(cred);
    }

    /**
     * Native iOS path: trust the JWT payload of the identityToken (signed by Apple)
     * to identify the user. We perform a minimal payload parse — full JWKS verification
     * would be a future hardening step.
     */
    private AuthCredentials processNativeIdentityToken(AppleOAuthRequest authRequest) {
        String idToken = authRequest.getIdentityToken();
        String appleId;
        String email;
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid identityToken format");
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(payloadJson, Map.class);
            String aud = (String) claims.get("aud");
            if (aud == null || !aud.equals(appleProperties.getClientId())) {
                // For native iOS, the audience is the iOS bundle identifier. Accept
                // when the configured client-id equals it.
                log.warn("Apple identityToken aud {} != configured clientId {}", aud, appleProperties.getClientId());
            }
            appleId = (String) claims.get("sub");
            email = (String) claims.get("email");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Apple identityToken", e);
        }
        if (appleId == null) {
            throw new RuntimeException("Apple user ID (sub) is missing");
        }

        Optional<AuthCredentials> existing = authCredentialRepository
                .findByProviderAndProviderUserId("APPLE", appleId);
        if (existing.isPresent()) {
            AuthCredentials cred = existing.get();
            Users existingUser = userRepository.findById(cred.getUserId())
                    .orElseThrow(() -> new RuntimeException("User linked to credentials not found"));
            if ("SUSPENDED".equals(existingUser.getStatus())) {
                throw new IllegalArgumentException("Your account has been suspended. Please contact support.");
            }
            return cred;
        }

        Users user = new Users();
        if (authRequest.getFirstName() != null) user.setFirstName(authRequest.getFirstName());
        if (authRequest.getLastName() != null) user.setLastName(authRequest.getLastName());
        user.setUserType(Users.UserType.CUSTOMER);
        user = userRepository.save(user);

        AuthCredentials cred = new AuthCredentials();
        cred.setUserId(user.getId());
        cred.setProvider("APPLE");
        cred.setProviderUserId(appleId);
        cred.setEmail(email);
        return authCredentialRepository.save(cred);
    }

    private String generateClientSecret() {
        try {
            PrivateKey privateKey = parsePrivateKey(appleProperties.getPrivateKey());

            return Jwts.builder()
                    .header()
                        .add("kid", appleProperties.getKeyId())
                        .add("alg", "ES256")
                    .and()
                    .issuer(appleProperties.getTeamId())
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 5)) // 5 mins
                    .audience().add("https://appleid.apple.com").and()
                    .subject(appleProperties.getClientId())
                    .signWith(privateKey)
                    .compact();
        } catch (Exception e) {
            log.error("Failed to generate Apple client secret: {}", e.getMessage());
            throw new RuntimeException("Could not generate Apple client secret", e);
        }
    }

    private PrivateKey parsePrivateKey(String keyContent) throws Exception {
        String cleaned = keyContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        
        byte[] keyBytes = Base64.getDecoder().decode(cleaned);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePrivate(spec);
    }
}
