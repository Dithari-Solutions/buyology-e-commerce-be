package com.buyology.ecommerce.auth.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.domain.MfaCredential;
import com.buyology.ecommerce.auth.domain.MfaRecoveryCode;
import com.buyology.ecommerce.auth.dto.MfaEnrollStartResponse;
import com.buyology.ecommerce.auth.dto.MfaStatusResponse;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.auth.repository.MfaCredentialRepository;
import com.buyology.ecommerce.auth.repository.MfaRecoveryCodeRepository;
import com.buyology.ecommerce.common.audit.AuditService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.utils.SecurityUtils;

/**
 * Orchestrates Google Authenticator (TOTP) two-factor auth for privileged
 * (admin/supplier) accounts.
 *
 * Login is gated in {@code AuthService.buildSigninResponse}: a privileged account
 * that has not enrolled gets an ENROLL challenge; one that has gets a LOGIN
 * challenge. The matching ticket is then redeemed here via
 * {@link #startEnrollment}/{@link #confirmEnrollment} or {@link #verifyLogin}.
 */
@Service
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_LENGTH = 10; // chars, displayed as 5-5
    // Crockford-style alphabet: no 0/O/1/I/L ambiguity.
    private static final char[] RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MfaCredentialRepository mfaCredentialRepository;
    private final MfaRecoveryCodeRepository mfaRecoveryCodeRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final MfaSecretCipher cipher;
    private final TotpService totpService;
    private final MfaTicketService ticketService;
    private final AuditService auditService;

    public MfaService(
            MfaCredentialRepository mfaCredentialRepository,
            MfaRecoveryCodeRepository mfaRecoveryCodeRepository,
            AuthCredentialRepository authCredentialRepository,
            MfaSecretCipher cipher,
            TotpService totpService,
            MfaTicketService ticketService,
            AuditService auditService) {
        this.mfaCredentialRepository = mfaCredentialRepository;
        this.mfaRecoveryCodeRepository = mfaRecoveryCodeRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.cipher = cipher;
        this.totpService = totpService;
        this.ticketService = ticketService;
        this.auditService = auditService;
    }

    /** Outcome of a code verification that, on success, lets the caller issue tokens. */
    public record MfaResult(boolean success, HttpStatus errorStatus, String errorMessage,
                            UUID authCredentialId, List<String> recoveryCodes) {
        static MfaResult ok(UUID authCredentialId, List<String> recoveryCodes) {
            return new MfaResult(true, null, null, authCredentialId, recoveryCodes);
        }
        static MfaResult error(HttpStatus status, String message) {
            return new MfaResult(false, status, message, null, null);
        }
    }

    public boolean isEnabled(UUID userId) {
        return mfaCredentialRepository.findByUserId(userId)
                .map(c -> Boolean.TRUE.equals(c.getEnabled()))
                .orElse(false);
    }

    /**
     * Build the MFA challenge for a privileged account whose password was just
     * verified. Issues a single-use ticket and tells the client whether to enroll
     * or to verify an existing authenticator.
     */
    public SignInResponse buildChallenge(AuthCredentials credentials) {
        boolean enabled = isEnabled(credentials.getUserId());
        MfaTicketService.Purpose purpose = enabled
                ? MfaTicketService.Purpose.LOGIN
                : MfaTicketService.Purpose.ENROLL;
        String ticket = ticketService.issue(purpose, credentials.getId());
        return SignInResponse.mfaChallenge(!enabled, ticket);
    }

    // ── Enrollment ────────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<MfaEnrollStartResponse>> startEnrollment(String mfaToken) {
        MfaTicketService.Ticket ticket = ticketService.resolve(mfaToken, MfaTicketService.Purpose.ENROLL);
        if (ticket == null) {
            return ApiResponse.failure(HttpStatus.UNAUTHORIZED,
                    "Your enrollment session has expired. Please sign in again.");
        }

        AuthCredentials credentials = authCredentialRepository.findById(ticket.authCredentialId()).orElse(null);
        if (credentials == null) {
            return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Account not found");
        }

        // Generate a fresh secret each time start is called; it stays unconfirmed
        // (enabled=false) until a valid code is submitted.
        String secret = totpService.generateSecret();
        MfaCredential mfa = mfaCredentialRepository.findByUserId(credentials.getUserId())
                .orElseGet(MfaCredential::new);
        mfa.setUserId(credentials.getUserId());
        mfa.setSecretEncrypted(cipher.encrypt(secret));
        mfa.setEnabled(false);
        mfaCredentialRepository.save(mfa);

        String account = credentials.getEmail() != null ? credentials.getEmail() : credentials.getUserId().toString();
        String qr = totpService.generateQrDataUri(secret, account);

        return ApiResponse.success(
                new MfaEnrollStartResponse(qr, secret, totpService.getIssuer(), account),
                "Scan the QR code with Google Authenticator, then enter the 6-digit code to confirm.");
    }

    @Transactional
    public MfaResult confirmEnrollment(String mfaToken, String code) {
        MfaTicketService.Ticket ticket = ticketService.resolve(mfaToken, MfaTicketService.Purpose.ENROLL);
        if (ticket == null) {
            return MfaResult.error(HttpStatus.UNAUTHORIZED,
                    "Your enrollment session has expired. Please sign in again.");
        }

        AuthCredentials credentials = authCredentialRepository.findById(ticket.authCredentialId()).orElse(null);
        if (credentials == null) {
            return MfaResult.error(HttpStatus.UNAUTHORIZED, "Account not found");
        }

        MfaCredential mfa = mfaCredentialRepository.findByUserId(credentials.getUserId()).orElse(null);
        if (mfa == null) {
            return MfaResult.error(HttpStatus.BAD_REQUEST,
                    "Enrollment has not been started. Please restart setup.");
        }

        String secret = cipher.decrypt(mfa.getSecretEncrypted());
        if (!totpService.verifyCode(secret, code)) {
            int remaining = ticketService.recordFailedAttempt(mfaToken);
            return MfaResult.error(HttpStatus.UNAUTHORIZED, invalidCodeMessage(remaining));
        }

        mfa.setEnabled(true);
        mfa.setConfirmedAt(Instant.now());
        mfaCredentialRepository.save(mfa);

        List<String> recoveryCodes = regenerateCodes(credentials.getUserId());
        ticketService.invalidate(mfaToken);
        auditService.logSuccess("MFA_ENROLL", "Users", credentials.getUserId().toString());
        log.info("2FA enrolled for user {}", credentials.getUserId());

        return MfaResult.ok(credentials.getId(), recoveryCodes);
    }

    // ── Login verification ──────────────────────────────────────────────────

    @Transactional
    public MfaResult verifyLogin(String mfaToken, String code) {
        MfaTicketService.Ticket ticket = ticketService.resolve(mfaToken, MfaTicketService.Purpose.LOGIN);
        if (ticket == null) {
            return MfaResult.error(HttpStatus.UNAUTHORIZED,
                    "Your sign-in session has expired. Please sign in again.");
        }

        AuthCredentials credentials = authCredentialRepository.findById(ticket.authCredentialId()).orElse(null);
        if (credentials == null) {
            return MfaResult.error(HttpStatus.UNAUTHORIZED, "Account not found");
        }

        MfaCredential mfa = mfaCredentialRepository.findByUserId(credentials.getUserId()).orElse(null);
        if (mfa == null || !Boolean.TRUE.equals(mfa.getEnabled())) {
            return MfaResult.error(HttpStatus.BAD_REQUEST, "Two-factor authentication is not set up for this account.");
        }

        String normalized = code == null ? "" : code.trim().replace("-", "").replace(" ", "").toUpperCase();

        boolean ok;
        if (normalized.matches("\\d{6}")) {
            ok = totpService.verifyCode(cipher.decrypt(mfa.getSecretEncrypted()), normalized);
        } else {
            ok = consumeRecoveryCode(credentials.getUserId(), normalized);
        }

        if (!ok) {
            int remaining = ticketService.recordFailedAttempt(mfaToken);
            auditService.logFailure("MFA_VERIFY", "Users", credentials.getUserId().toString(), "invalid_code");
            return MfaResult.error(HttpStatus.UNAUTHORIZED, invalidCodeMessage(remaining));
        }

        ticketService.invalidate(mfaToken);
        auditService.logSuccess("MFA_VERIFY", "Users", credentials.getUserId().toString());
        return MfaResult.ok(credentials.getId(), null);
    }

    // ── Settings (authenticated user) ─────────────────────────────────────────

    public ResponseEntity<ApiResponse<MfaStatusResponse>> status(UUID userId) {
        Optional<MfaCredential> mfa = mfaCredentialRepository.findByUserId(userId);
        boolean enabled = mfa.map(c -> Boolean.TRUE.equals(c.getEnabled())).orElse(false);
        Instant enrolledAt = mfa.map(MfaCredential::getConfirmedAt).orElse(null);
        Integer unused = null;
        if (enabled) {
            unused = (int) mfaRecoveryCodeRepository.findByUserId(userId).stream()
                    .filter(c -> !Boolean.TRUE.equals(c.getUsed())).count();
        }
        return ApiResponse.success(new MfaStatusResponse(enabled, enrolledAt, unused), "OK");
    }

    /** Regenerate recovery codes for the authenticated user; requires a valid current TOTP code. */
    @Transactional
    public ResponseEntity<ApiResponse<List<String>>> regenerateRecoveryCodes(UUID userId, String code) {
        MfaCredential mfa = mfaCredentialRepository.findByUserId(userId).orElse(null);
        if (mfa == null || !Boolean.TRUE.equals(mfa.getEnabled())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Two-factor authentication is not enabled.");
        }
        if (!totpService.verifyCode(cipher.decrypt(mfa.getSecretEncrypted()), code)) {
            return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid authenticator code.");
        }
        List<String> codes = regenerateCodes(userId);
        auditService.logSuccess("MFA_RECOVERY_REGEN", "Users", userId.toString());
        return ApiResponse.success(codes, "New recovery codes generated. Save them somewhere safe.");
    }

    /** SUPERADMIN-only: wipe a user's 2FA so they must re-enroll on next login (lost-device recovery). */
    @Transactional
    public ResponseEntity<ApiResponse<String>> resetForUser(UUID targetUserId) {
        mfaRecoveryCodeRepository.deleteByUserId(targetUserId);
        int removed = mfaCredentialRepository.deleteByUserId(targetUserId);
        auditService.log("MFA_RESET", "Users", targetUserId.toString(), "SUCCESS",
                "reset_by=" + safeCurrentUser());
        log.warn("2FA reset for user {} by {}", targetUserId, safeCurrentUser());
        return ApiResponse.success(null,
                removed > 0 ? "Two-factor authentication has been reset. The user must re-enroll on next sign-in."
                            : "This user did not have two-factor authentication configured.");
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private List<String> regenerateCodes(UUID userId) {
        mfaRecoveryCodeRepository.deleteByUserId(userId);
        List<String> plaintext = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<MfaRecoveryCode> entities = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String raw = randomRecoveryCode();
            plaintext.add(formatRecoveryCode(raw));
            entities.add(new MfaRecoveryCode(userId, SecurityUtils.sha256Hex(raw)));
        }
        mfaRecoveryCodeRepository.saveAll(entities);
        return plaintext;
    }

    private boolean consumeRecoveryCode(UUID userId, String normalized) {
        String hash = SecurityUtils.sha256Hex(normalized);
        Optional<MfaRecoveryCode> match =
                mfaRecoveryCodeRepository.findByUserIdAndCodeHashAndUsedFalse(userId, hash);
        if (match.isEmpty()) return false;
        MfaRecoveryCode rc = match.get();
        rc.setUsed(true);
        rc.setUsedAt(Instant.now());
        mfaRecoveryCodeRepository.save(rc);
        return true;
    }

    private static String randomRecoveryCode() {
        StringBuilder sb = new StringBuilder(RECOVERY_CODE_LENGTH);
        for (int i = 0; i < RECOVERY_CODE_LENGTH; i++) {
            sb.append(RECOVERY_ALPHABET[SECURE_RANDOM.nextInt(RECOVERY_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private static String formatRecoveryCode(String raw) {
        int mid = raw.length() / 2;
        return raw.substring(0, mid) + "-" + raw.substring(mid);
    }

    private static String invalidCodeMessage(int remaining) {
        if (remaining <= 0) {
            return "Too many incorrect codes. Please sign in again.";
        }
        return "Invalid code. " + remaining + " attempt(s) remaining.";
    }

    private static String safeCurrentUser() {
        UUID id = SecurityUtils.currentUserIdOrNull();
        return id == null ? "system" : id.toString();
    }
}
