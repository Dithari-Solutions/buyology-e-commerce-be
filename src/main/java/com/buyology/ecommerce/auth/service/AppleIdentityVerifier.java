package com.buyology.ecommerce.auth.service;

import com.buyology.ecommerce.infrastructure.config.AppleProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Only Apple's fixed key endpoint is trusted. Challenges are shared across backend nodes. */
@Service
public class AppleIdentityVerifier {
    private static final String ISSUER = "https://appleid.apple.com";
    private static final String PREFIX = "auth:apple:nonce:";
    private final RestTemplate http;
    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;
    private final AppleProperties config;
    private final SecureRandom random = new SecureRandom();
    private Map<String, PublicKey> keys = Map.of();
    private long loadedAt;

    public AppleIdentityVerifier(RestTemplate http, ObjectMapper mapper,
                                 StringRedisTemplate redis, AppleProperties config) {
        this.http = http;
        this.mapper = mapper;
        this.redis = redis;
        this.config = config;
    }

    public String createChallenge(String platform) {
        String audience = switch (platform == null ? "" : platform) {
            case "web" -> config.getClientId();
            case "ios" -> config.getIosClientId();
            default -> throw new IllegalArgumentException("Unsupported Apple login platform");
        };
        if (audience == null || audience.isBlank())
            throw new IllegalArgumentException("Apple login is not configured for this platform");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(PREFIX + nonce, audience, Duration.ofMinutes(5));
        return nonce;
    }

    public Claims verify(String token, String audience, String nonce) {
        if (audience == null || audience.isBlank() || token == null || token.length() > 16384
                || nonce == null || !nonce.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException("Invalid Apple login request");
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException();
            Map<?, ?> header = mapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
            if (!"RS256".equals(header.get("alg"))) throw new IllegalArgumentException();
            PublicKey key = keyFor((String) header.get("kid"));
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer(ISSUER)
                    .requireAudience(audience).require("nonce", nonce).build()
                    .parseSignedClaims(token).getPayload();
            if (claims.getExpiration() == null || claims.getIssuedAt() == null
                    || claims.getIssuedAt().getTime() > System.currentTimeMillis() + 30000
                    || claims.getSubject() == null || claims.getSubject().isBlank())
                throw new IllegalArgumentException();
            // GETDEL is atomic: a valid token cannot log in twice, including across nodes.
            if (!audience.equals(redis.opsForValue().getAndDelete(PREFIX + nonce)))
                throw new IllegalArgumentException();
            return claims;
        } catch (Exception e) {
            throw new IllegalArgumentException("Apple login could not be verified. Please try again.");
        }
    }

    private synchronized PublicKey keyFor(String kid) throws Exception {
        if (kid == null || kid.length() > 128) throw new IllegalArgumentException();
        long now = System.currentTimeMillis();
        // Refresh on rotation; throttle unknown IDs to avoid turning requests into outbound traffic.
        if (loadedAt == 0 || now - loadedAt > Duration.ofHours(1).toMillis()
                || (!keys.containsKey(kid) && now - loadedAt > 30000)) {
            Map<?, ?> response = http.getForObject(ISSUER + "/auth/keys", Map.class);
            Map<String, PublicKey> updated = new HashMap<>();
            for (Object entry : (List<?>) response.get("keys")) {
                Map<?, ?> jwk = (Map<?, ?>) entry;
                if (!"RSA".equals(jwk.get("kty")) || !"RS256".equals(jwk.get("alg"))) continue;
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode((String) jwk.get("n")));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode((String) jwk.get("e")));
                updated.put((String) jwk.get("kid"), KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(n, e)));
            }
            keys = Map.copyOf(updated);
            loadedAt = now;
        }
        PublicKey key = keys.get(kid);
        if (key == null) throw new IllegalArgumentException();
        return key;
    }
}
