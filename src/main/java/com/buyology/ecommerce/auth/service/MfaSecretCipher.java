package com.buyology.ecommerce.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts/decrypts TOTP shared secrets at rest with AES-256-GCM.
 *
 * The secret cannot be hashed (the server must reproduce one-time codes), so it
 * is encrypted instead. The 256-bit AES key is derived (SHA-256) from
 * {@code mfa.secret-encryption-key}; if that is not configured the key falls back
 * to {@code jwt.secret} so dev environments work without extra setup. In prod,
 * set a dedicated MFA_SECRET_ENCRYPTION_KEY so rotating the JWT secret does not
 * invalidate every enrolled authenticator.
 *
 * Wire format (base64): [12-byte IV][GCM ciphertext+tag].
 */
@Component
public class MfaSecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;            // 96-bit nonce (GCM standard)
    private static final int GCM_TAG_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec keySpec;

    public MfaSecretCipher(
            @Value("${mfa.secret-encryption-key:}") String mfaKey,
            @Value("${jwt.secret:}") String jwtSecret) {
        String keyMaterial = (mfaKey != null && !mfaKey.isBlank()) ? mfaKey : jwtSecret;
        if (keyMaterial == null || keyMaterial.isBlank()) {
            throw new IllegalStateException(
                    "MFA secret encryption key is not configured. Set MFA_SECRET_ENCRYPTION_KEY "
                    + "(or JWT_SECRET as a fallback).");
        }
        this.keySpec = new SecretKeySpec(sha256(keyMaterial), "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt MFA secret", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt MFA secret", e);
        }
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
