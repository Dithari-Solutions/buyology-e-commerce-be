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

import io.jsonwebtoken.Jwts;

@Service
public class AppleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(AppleOAuthService.class);

    private final AppleProperties appleProperties;
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final AppleIdentityVerifier verifier;

    public AppleOAuthService(
            AppleProperties appleProperties,
            RestTemplate restTemplate,
            UserRepository userRepository,
            AuthCredentialRepository authCredentialRepository,
            AppleIdentityVerifier verifier) {
        this.appleProperties = appleProperties;
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.verifier = verifier;
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
            // The client's own redirect URI wins when supplied (two storefronts, one backend);
            // the server-configured value is the fallback for clients that predate the field.
            // Apple validates the value against the Service ID's registered Return URLs during
            // the exchange, so an unregistered URI fails there rather than opening a redirect.
            String redirectUri = authRequest.getRedirectUri() != null && !authRequest.getRedirectUri().isBlank()
                    ? authRequest.getRedirectUri()
                    : appleProperties.getRedirectUri();
            if (redirectUri != null && !redirectUri.isEmpty()) {
                map.add("redirect_uri", redirectUri);
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://appleid.apple.com/auth/token", request, Map.class);

            tokenResponse = response.getBody();
        } catch (HttpClientErrorException e) {
            log.warn("Apple token exchange rejected: {}", e.getStatusCode());
            throw new IllegalArgumentException("Apple login could not be verified. Please try again.");
        }

        if (tokenResponse == null) throw new IllegalArgumentException("Apple returned no tokens");
        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = (String) tokenResponse.get("refresh_token");
        String idToken = (String) tokenResponse.get("id_token");

        var claims = verifier.verify(idToken, appleProperties.getClientId(), authRequest.getNonce());
        String appleId = claims.getSubject();
        String email = verifiedEmail(claims);

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

    private AuthCredentials processNativeIdentityToken(AppleOAuthRequest authRequest) {
        String idToken = authRequest.getIdentityToken();
        var claims = verifier.verify(idToken, appleProperties.getIosClientId(), authRequest.getNonce());
        String appleId = claims.getSubject();
        String email = verifiedEmail(claims);

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

    public String createChallenge(String platform) { return verifier.createChallenge(platform); }

    private String verifiedEmail(io.jsonwebtoken.Claims claims) {
        Object verified = claims.get("email_verified");
        String email = claims.get("email", String.class);
        if (email != null && !Boolean.TRUE.equals(verified) && !"true".equals(verified))
            throw new IllegalArgumentException("Apple email is not verified");
        return email;
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
        String cleaned = keyContent.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        
        byte[] keyBytes = Base64.getDecoder().decode(cleaned);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePrivate(spec);
    }
}
