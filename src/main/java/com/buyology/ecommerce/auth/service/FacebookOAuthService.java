package com.buyology.ecommerce.auth.service;

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
import org.springframework.web.util.UriComponentsBuilder;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.dto.FacebookOAuthRequest;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.infrastructure.config.FacebookProperties;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;

/**
 * Facebook Login OAuth2 handler.
 *
 * Supports two client flows:
 *   1. Web — client posts an authorization {@code code} from /v19.0/dialog/oauth.
 *   2. Native (mobile FB SDK) — client posts an {@code accessToken} obtained
 *      from the LoginManager. We trust this only after re-validating it
 *      against the FB Graph debug_token endpoint (verifies app_id match).
 */
@Service
public class FacebookOAuthService {

    private static final Logger log = LoggerFactory.getLogger(FacebookOAuthService.class);
    private static final String GRAPH_BASE = "https://graph.facebook.com/v19.0";

    private final FacebookProperties facebookProperties;
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;

    public FacebookOAuthService(
            FacebookProperties facebookProperties,
            RestTemplate restTemplate,
            UserRepository userRepository,
            AuthCredentialRepository authCredentialRepository) {
        this.facebookProperties = facebookProperties;
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
    }

    @Transactional
    public AuthCredentials processFacebookOAuth(FacebookOAuthRequest request) {
        String accessToken = (request.getAccessToken() != null && !request.getAccessToken().isBlank())
                ? validateNativeAccessToken(request.getAccessToken())
                : exchangeCodeForToken(request);

        Map<String, Object> userInfo = fetchUserInfo(accessToken);
        String facebookId = (String) userInfo.get("id");
        String email = (String) userInfo.get("email");
        String firstName = (String) userInfo.get("first_name");
        String lastName = (String) userInfo.get("last_name");

        if (facebookId == null) {
            throw new RuntimeException("Facebook user ID is missing");
        }

        Optional<AuthCredentials> existing = authCredentialRepository
                .findByProviderAndProviderUserId("FACEBOOK", facebookId);

        if (existing.isPresent()) {
            AuthCredentials cred = existing.get();
            Users existingUser = userRepository.findById(cred.getUserId())
                    .orElseThrow(() -> new RuntimeException("User linked to credentials not found"));
            if ("SUSPENDED".equals(existingUser.getStatus())) {
                throw new IllegalArgumentException("Your account has been suspended. Please contact support.");
            }
            cred.setAccessToken(accessToken);
            return authCredentialRepository.save(cred);
        }

        Users user = new Users();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUserType(Users.UserType.CUSTOMER);
        user = userRepository.save(user);

        AuthCredentials cred = new AuthCredentials();
        cred.setUserId(user.getId());
        cred.setProvider("FACEBOOK");
        cred.setProviderUserId(facebookId);
        cred.setEmail(email);
        cred.setAccessToken(accessToken);
        return authCredentialRepository.save(cred);
    }

    private String exchangeCodeForToken(FacebookOAuthRequest request) {
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("Authorization code or accessToken is required");
        }
        String redirectUri = (request.getRedirectUri() != null && !request.getRedirectUri().isBlank())
                ? request.getRedirectUri()
                : facebookProperties.getRedirectUri();

        String url = UriComponentsBuilder.fromHttpUrl(GRAPH_BASE + "/oauth/access_token")
                .queryParam("client_id", facebookProperties.getAppId())
                .queryParam("client_secret", facebookProperties.getAppSecret())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code", request.getCode())
                .toUriString();

        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            Map body = resp.getBody();
            if (body == null || body.get("access_token") == null) {
                throw new RuntimeException("Facebook returned empty token response");
            }
            return (String) body.get("access_token");
        } catch (HttpClientErrorException e) {
            log.error("Facebook token exchange failed: {}", e.getResponseBodyAsString());
            throw new IllegalArgumentException("Facebook OAuth failed: " + e.getResponseBodyAsString());
        }
    }

    /**
     * Validates a client-supplied access token via the Graph debug_token endpoint.
     * Confirms it was issued for OUR app, otherwise rejects.
     */
    @SuppressWarnings("unchecked")
    private String validateNativeAccessToken(String accessToken) {
        String appAccessToken = facebookProperties.getAppId() + "|" + facebookProperties.getAppSecret();
        String url = UriComponentsBuilder.fromHttpUrl(GRAPH_BASE + "/debug_token")
                .queryParam("input_token", accessToken)
                .queryParam("access_token", appAccessToken)
                .toUriString();
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = resp.getBody() == null ? Map.of() : resp.getBody();
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data == null) {
                throw new IllegalArgumentException("Facebook debug_token returned no data");
            }
            Object isValid = data.get("is_valid");
            Object appId = data.get("app_id");
            if (!Boolean.TRUE.equals(isValid)) {
                throw new IllegalArgumentException("Facebook access token is not valid");
            }
            if (appId == null || !facebookProperties.getAppId().equals(String.valueOf(appId))) {
                throw new IllegalArgumentException("Facebook access token was issued for a different app");
            }
            return accessToken;
        } catch (HttpClientErrorException e) {
            log.error("Facebook debug_token failed: {}", e.getResponseBodyAsString());
            throw new IllegalArgumentException("Facebook token validation failed");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = UriComponentsBuilder.fromHttpUrl(GRAPH_BASE + "/me")
                .queryParam("fields", "id,email,first_name,last_name")
                .toUriString();
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return resp.getBody() == null ? Map.of() : resp.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Facebook /me failed: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Failed to fetch user info from Facebook");
        }
    }
}
