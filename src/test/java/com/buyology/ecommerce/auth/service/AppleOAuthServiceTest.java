package com.buyology.ecommerce.auth.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.dto.AppleOAuthRequest;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.infrastructure.config.AppleProperties;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AppleOAuthServiceTest {
    private AppleIdentityVerifier verifier;
    private UserRepository users;
    private AuthCredentialRepository credentials;
    private AppleOAuthService service;
    private AppleOAuthRequest request;
    private AppleProperties config;
    private RestTemplate http;
    @BeforeEach void setup() {
        config = new AppleProperties();
        config.setClientId("website-service");
        config.setIosClientId("ios-bundle");
        verifier = mock(AppleIdentityVerifier.class);
        users = mock(UserRepository.class);
        credentials = mock(AuthCredentialRepository.class);
        http = mock(RestTemplate.class);
        service = new AppleOAuthService(config, http, users, credentials, verifier);
        request = new AppleOAuthRequest();
        request.setIdentityToken("token");
        request.setNonce("nonce");
    }
    private void valid() {
        when(verifier.verify("token", "ios-bundle", "nonce")).thenReturn(Jwts.claims()
                .subject("apple-user").add("email", "hidden@privaterelay.appleid.com")
                .add("email_verified", "true").build());
    }
    @Test void rejectsInvalidNativeTokenBeforeAccountLookup() {
        when(verifier.verify(any(), any(), any())).thenThrow(new IllegalArgumentException("invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.processAppleOAuth(request));
        verifyNoInteractions(users, credentials);
    }
    @Test void createsCustomerUsingVerifiedIdentityAndFirstConsentNames() {
        valid();
        request.setFirstName("First");
        when(credentials.findByProviderAndProviderUserId("APPLE", "apple-user")).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(call -> { Users user = call.getArgument(0); user.setId(UUID.randomUUID()); return user; });
        when(credentials.save(any())).thenAnswer(call -> call.getArgument(0));
        AuthCredentials result = service.processAppleOAuth(request);
        assertEquals("APPLE", result.getProvider());
        assertEquals("apple-user", result.getProviderUserId());
        assertEquals("hidden@privaterelay.appleid.com", result.getEmail());
        verify(users).save(argThat(user -> "First".equals(user.getFirstName())));
    }
    @Test void returningCustomerKeepsExistingNamesAndCredentials() {
        valid();
        AuthCredentials existing = new AuthCredentials();
        existing.setUserId(UUID.randomUUID());
        Users user = new Users();
        user.setFirstName("Original");
        when(credentials.findByProviderAndProviderUserId("APPLE", "apple-user")).thenReturn(Optional.of(existing));
        when(users.findById(existing.getUserId())).thenReturn(Optional.of(user));
        assertSame(existing, service.processAppleOAuth(request));
        assertEquals("Original", user.getFirstName());
        verify(users, never()).save(any());
    }
    @Test void suspendedCustomerCannotLoginWithApple() {
        valid();
        AuthCredentials existing = new AuthCredentials();
        existing.setUserId(UUID.randomUUID());
        Users user = new Users();
        user.setStatus("SUSPENDED");
        when(credentials.findByProviderAndProviderUserId("APPLE", "apple-user")).thenReturn(Optional.of(existing));
        when(users.findById(existing.getUserId())).thenReturn(Optional.of(user));
        assertThrows(IllegalArgumentException.class, () -> service.processAppleOAuth(request));
    }
    @Test void webExchangesCodeAndValidatesServicesIdWithServerNonce() throws Exception {
        java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        config.setPrivateKey("-----BEGIN PRIVATE KEY-----\\n"
                + java.util.Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\\n-----END PRIVATE KEY-----");
        config.setTeamId("team");
        config.setKeyId("key");
        request.setCode("authorization-code");
        request.setRedirectUri("https://buyology.online");
        when(http.postForEntity(eq("https://appleid.apple.com/auth/token"), any(), eq(java.util.Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(java.util.Map.of(
                        "id_token", "verified-web-token", "access_token", "apple-access", "refresh_token", "apple-refresh")));
        when(verifier.verify("verified-web-token", "website-service", "nonce"))
                .thenReturn(Jwts.claims().subject("apple-user").build());
        AuthCredentials existing = new AuthCredentials();
        existing.setUserId(UUID.randomUUID());
        when(credentials.findByProviderAndProviderUserId("APPLE", "apple-user")).thenReturn(Optional.of(existing));
        when(users.findById(existing.getUserId())).thenReturn(Optional.of(new Users()));
        when(credentials.save(existing)).thenReturn(existing);
        assertSame(existing, service.processAppleOAuth(request));
        verify(verifier).verify("verified-web-token", "website-service", "nonce");
        assertEquals("apple-refresh", existing.getRefreshToken());
    }

}
