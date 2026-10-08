package com.buyology.ecommerce.auth.service;

import com.buyology.ecommerce.infrastructure.config.AppleProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestTemplate;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppleIdentityVerifierTest {
    private final String audience = "com.buyology.ios";
    private final String nonce = "a".repeat(43);
    private RestTemplate http;
    private ValueOperations<String, String> values;
    private AppleIdentityVerifier verifier;
    private KeyPair pair;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        RSAPublicKey key = (RSAPublicKey) pair.getPublic();
        http = mock(RestTemplate.class);
        when(http.getForObject("https://appleid.apple.com/auth/keys", Map.class))
                .thenReturn(Map.of("keys", List.of(Map.of("kid", "apple", "kty", "RSA", "alg", "RS256",
                        "n", encode(key.getModulus().toByteArray()), "e", encode(key.getPublicExponent().toByteArray())))));
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete("auth:apple:nonce:" + nonce)).thenReturn(audience);
        AppleProperties config = new AppleProperties();
        config.setClientId("com.buyology.web");
        config.setIosClientId(audience);
        verifier = new AppleIdentityVerifier(http, new ObjectMapper(), redis, config);
    }

    private String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String token(String issuer, String aud, String tokenNonce, long expires, KeyPair signingKey) {
        return Jwts.builder().header().keyId("apple").and().issuer(issuer).subject("customer-123")
                .audience().add(aud).and().issuedAt(new Date()).expiration(new Date(expires))
                .claim("nonce", tokenNonce).signWith(signingKey.getPrivate(), Jwts.SIG.RS256).compact();
    }
    private String valid() { return token("https://appleid.apple.com", audience, nonce, System.currentTimeMillis() + 60000, pair); }

    @Test void acceptsVerifiedTokenAndConsumesChallenge() {
        assertEquals("customer-123", verifier.verify(valid(), audience, nonce).getSubject());
        verify(values).getAndDelete("auth:apple:nonce:" + nonce);
    }
    @Test void rejectsReplayAndExpiredChallenges() {
        when(values.getAndDelete(anyString())).thenReturn(audience, (String) null);
        String token = valid();
        verifier.verify(token, audience, nonce);
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(token, audience, nonce));
    }
    @Test void rejectsWrongAudience() {
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(valid(), "different-app", nonce));
        verifyNoInteractions(values);
    }
    @Test void rejectsWrongIssuer() {
        String token = token("https://attacker.example", audience, nonce, System.currentTimeMillis() + 60000, pair);
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(token, audience, nonce));
        verifyNoInteractions(values);
    }
    @Test void rejectsWrongNonce() {
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(valid(), audience, "b".repeat(43)));
        verifyNoInteractions(values);
    }
    @Test void rejectsExpiredToken() {
        String token = token("https://appleid.apple.com", audience, nonce, System.currentTimeMillis() - 60000, pair);
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(token, audience, nonce));
        verifyNoInteractions(values);
    }
    @Test void rejectsForgedSignature() throws Exception {
        KeyPair attacker = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String token = token("https://appleid.apple.com", audience, nonce, System.currentTimeMillis() + 60000, attacker);
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(token, audience, nonce));
        verifyNoInteractions(values);
    }
    @Test void rejectsUnsignedToken() {
        String unsigned = encode("{\"alg\":\"none\",\"kid\":\"apple\"}".getBytes()) + "." + encode("{}".getBytes()) + ".";
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(unsigned, audience, nonce));
        verifyNoInteractions(values);
    }
    @Test void challengeHasExpiryAndPlatformBoundAudience() {
        String issued = verifier.createChallenge("ios");
        assertTrue(issued.matches("[A-Za-z0-9_-]{43}"));
        verify(values).set("auth:apple:nonce:" + issued, audience, java.time.Duration.ofMinutes(5));
        assertThrows(IllegalArgumentException.class, () -> verifier.createChallenge("android"));
    }
    @Test void rejectsChallengeIssuedForDifferentPlatform() {
        when(values.getAndDelete(anyString())).thenReturn("com.buyology.web");
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(valid(), audience, nonce));
    }
}
