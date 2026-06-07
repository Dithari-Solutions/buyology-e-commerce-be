package com.buyology.ecommerce.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.util.Utils;

/**
 * Thin wrapper around the TOTP (RFC 6238) primitives compatible with Google
 * Authenticator, Authy, 1Password, etc. Standard 6-digit / 30-second / SHA-1
 * codes. The login verifier tolerates ±1 time step to absorb clock skew.
 */
@Service
public class TotpService {

    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    private static final HashingAlgorithm ALGORITHM = HashingAlgorithm.SHA1;

    private final String issuer;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier;

    public TotpService(@Value("${mfa.issuer:Buyology Admin}") String issuer) {
        this.issuer = issuer;
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        // Accept the current code plus one step on either side (clock drift tolerance).
        verifier.setAllowedTimePeriodDiscrepancy(1);
        this.codeVerifier = verifier;
    }

    /** Generate a fresh base32 TOTP secret for a new enrollment. */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /**
     * Renders a PNG QR code (otpauth:// URI) as a base64 {@code data:} URI that a
     * browser can place straight into an {@code <img src>}.
     */
    public String generateQrDataUri(String secret, String accountLabel) {
        QrData data = new QrData.Builder()
                .label(accountLabel)
                .secret(secret)
                .issuer(issuer)
                .algorithm(ALGORITHM)
                .digits(DIGITS)
                .period(PERIOD_SECONDS)
                .build();
        try {
            byte[] image = qrGenerator.generate(data);
            return Utils.getDataUriForImage(image, qrGenerator.getImageMimeType());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate TOTP QR code", e);
        }
    }

    /** True if {@code code} is a currently-valid 6-digit TOTP for {@code secret}. */
    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null) return false;
        return codeVerifier.isValidCode(secret, code.trim());
    }

    public String getIssuer() {
        return issuer;
    }
}
