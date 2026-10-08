package com.buyology.ecommerce.auth.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class GoogleLoginValidationTest {
    private GoogleOAuthService service;
    private Map<String, Object> claims;
    private AuthCredentialRepository credentials;
    private UserRepository users;
    @BeforeEach void setup() {
        RestTemplate http = mock(RestTemplate.class);
        users = mock(UserRepository.class);
        credentials = mock(AuthCredentialRepository.class);
        service = new GoogleOAuthService(http, users, credentials, new ObjectMapper());
        ReflectionTestUtils.setField(service, "clientId", "legacy-client");
        ReflectionTestUtils.setField(service, "allowedAudiencesCsv", "new-web-client");
        claims = new HashMap<>(Map.of("aud", "new-web-client", "iss", "https://accounts.google.com",
                "exp", String.valueOf(System.currentTimeMillis() / 1000 + 300), "email_verified", "true",
                "sub", "google-user", "email", "customer@example.com"));
        when(http.getForEntity(anyString(), eq(Map.class))).thenReturn(ResponseEntity.ok(claims));
        when(credentials.findByProviderAndProviderUserId("GOOGLE", "google-user")).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(call -> { Users user = call.getArgument(0); user.setId(UUID.randomUUID()); return user; });
        when(credentials.save(any())).thenAnswer(call -> call.getArgument(0));
    }
    @Test void acceptsNewClientWithoutReplacingLegacyClient() {
        assertEquals("GOOGLE", service.processGoogleNativeIdToken("token").getProvider());
        claims.put("aud", "legacy-client");
        assertEquals("google-user", service.processGoogleNativeIdToken("legacy-token").getProviderUserId());
    }
    @Test void longIdentityTokenIsVerifiedWithoutBeingStoredInAccessTokenColumn() {
        AuthCredentials created = service.processGoogleNativeIdToken("jwt".repeat(700));
        assertEquals("google-user", created.getProviderUserId());
        assertNull(created.getAccessToken());
        assertNull(created.getRefreshToken());
    }
    @Test void identityLoginPreservesExistingOAuthTokensAndUser() {
        Users user = new Users();
        user.setId(UUID.randomUUID());
        AuthCredentials existing = new AuthCredentials(user.getId(), "GOOGLE");
        existing.setAccessToken("existing-access-token");
        existing.setRefreshToken("existing-refresh-token");
        when(credentials.findByProviderAndProviderUserId("GOOGLE", "google-user"))
                .thenReturn(Optional.of(existing));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        assertSame(existing, service.processGoogleNativeIdToken("jwt".repeat(700)));
        assertEquals("existing-access-token", existing.getAccessToken());
        assertEquals("existing-refresh-token", existing.getRefreshToken());
        verify(users, never()).save(any());
    }
    @Test void rejectsOtherApplications() { claims.put("aud", "attacker"); reject(); }
    @Test void rejectsWrongIssuer() { claims.put("iss", "https://attacker.example"); reject(); }
    @Test void rejectsExpiredToken() { claims.put("exp", "1"); reject(); }
    @Test void rejectsUnverifiedEmail() { claims.put("email_verified", false); reject(); }
    @Test void rejectsMissingExpiry() { claims.remove("exp"); reject(); }
    private void reject() {
        assertThrows(IllegalArgumentException.class, () -> service.processGoogleNativeIdToken("token"));
        verifyNoInteractions(credentials, users);
    }
}
