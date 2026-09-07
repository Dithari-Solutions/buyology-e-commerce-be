package com.buyology.ecommerce.verification.service;

import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.infrastructure.config.OtpProperties;
import com.buyology.ecommerce.verification.domain.ContactVerification;
import com.buyology.ecommerce.verification.domain.ContactVerification.Channel;
import com.buyology.ecommerce.verification.repository.ContactVerificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Reusable contact-verification service shared by the supplier, B2B membership and
 * profile flows.
 *
 * <ul>
 *   <li><b>EMAIL</b> — self-rolled 6-digit OTP delivered via SendGrid and checked locally.</li>
 *   <li><b>PHONE</b> — delegated to Twilio Verify; we only persist a row once the
 *       phone is verified, as short-lived "proof" for the subsequent submit.</li>
 * </ul>
 */
@Service
public class ContactVerificationService {

    private static final Logger log = LoggerFactory.getLogger(ContactVerificationService.class);

    private final ContactVerificationRepository repo;
    private final TwilioVerifyService twilioVerify;
    private final EmailService emailService;
    private final OtpProperties otpProperties;
    private final PhoneVerificationGuard guard;
    private final SecureRandom secureRandom = new SecureRandom();

    public ContactVerificationService(ContactVerificationRepository repo,
                                      TwilioVerifyService twilioVerify,
                                      EmailService emailService,
                                      OtpProperties otpProperties,
                                      PhoneVerificationGuard guard) {
        this.repo = repo;
        this.twilioVerify = twilioVerify;
        this.emailService = emailService;
        this.otpProperties = otpProperties;
        this.guard = guard;
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    @Transactional
    public void startEmail(String emailRaw) {
        String email = normalizeEmail(emailRaw);

        repo.findTopByChannelAndTargetOrderByCreatedAtDesc(Channel.EMAIL, email).ifPresent(prev -> {
            if (!prev.isVerified()) {
                Instant base = prev.getCreatedAt() != null ? prev.getCreatedAt() : Instant.now();
                long since = ChronoUnit.SECONDS.between(base, Instant.now());
                if (since < otpProperties.getResendCooldownSeconds()) {
                    throw new IllegalStateException("Please wait "
                            + (otpProperties.getResendCooldownSeconds() - since)
                            + " seconds before requesting a new code.");
                }
            }
        });

        // Keep a single active code per target.
        repo.deleteByChannelAndTarget(Channel.EMAIL, email);

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        ContactVerification v = new ContactVerification();
        v.setChannel(Channel.EMAIL);
        v.setTarget(email);
        v.setOtpCode(code);
        v.setExpiresAt(Instant.now().plus(otpProperties.getExpiryMinutes(), ChronoUnit.MINUTES));
        repo.save(v);

        emailService.sendOtpEmail(email, code);
    }

    @Transactional
    public boolean checkEmail(String emailRaw, String code) {
        String email = normalizeEmail(emailRaw);
        ContactVerification v = repo.findTopByChannelAndTargetOrderByCreatedAtDesc(Channel.EMAIL, email)
                .orElse(null);

        if (v == null || v.getOtpCode() == null) {
            throw new IllegalStateException("No verification code found. Please request a new one.");
        }
        if (v.isVerified()) {
            return true;
        }
        if (v.isExpired()) {
            throw new IllegalStateException("Code has expired. Please request a new one.");
        }
        if (v.isMaxAttemptsReached()) {
            throw new IllegalStateException("Too many incorrect attempts. Please request a new code.");
        }
        if (!v.getOtpCode().equals(code)) {
            v.setAttempts(v.getAttempts() + 1);
            repo.save(v);
            return false;
        }

        v.setVerified(true);
        v.setVerifiedAt(Instant.now());
        v.setOtpCode(null);
        repo.save(v);
        return true;
    }

    // ── Phone (Twilio Verify) ──────────────────────────────────────────────────

    /**
     * Sends a verification code to a phone number for an anonymous caller.
     *
     * <p>This is the most exposed money-spending path in the application: POST /api/verify/phone/start
     * is permitAll, so anyone on the internet can reach it without an account, and it was matched by
     * none of the rate limiter's credential patterns. It is the shape the SMS-pumping attack needed,
     * and it survived the first round of fixes because those covered the two callers that had a user
     * id to point at.
     *
     * <p>Having no account is exactly why it needs the guard most, so the quota subject is the number
     * itself: the per-subject daily cap then acts as a second, stricter ceiling on top of the hourly
     * per-number one. The country allow-list does the heavy lifting either way.
     */
    @Transactional
    public void startPhone(String phoneRaw) {
        String phone = guard.check("public-verify:" + normalizePhone(phoneRaw), phoneRaw);
        twilioVerify.startVerification(phone);
    }

    @Transactional
    public boolean checkPhone(String phoneRaw, String code) {
        String phone = normalizePhone(phoneRaw);
        boolean approved = twilioVerify.checkVerification(phone, code);
        if (approved) {
            repo.deleteByChannelAndTarget(Channel.PHONE, phone);
            ContactVerification v = new ContactVerification();
            v.setChannel(Channel.PHONE);
            v.setTarget(phone);
            v.setVerified(true);
            v.setVerifiedAt(Instant.now());
            repo.save(v);
        }
        return approved;
    }

    // ── Gates used by submit flows ─────────────────────────────────────────────

    /** True when the target has a verified record within the configured validity window. */
    public boolean isVerified(Channel channel, String targetRaw) {
        String target = normalize(channel, targetRaw);
        return repo.findTopByChannelAndTargetOrderByCreatedAtDesc(channel, target)
                .filter(ContactVerification::isVerified)
                .filter(v -> v.getVerifiedAt() != null
                        && ChronoUnit.MINUTES.between(v.getVerifiedAt(), Instant.now())
                            <= otpProperties.getVerificationValidityMinutes())
                .isPresent();
    }

    public void requireVerified(Channel channel, String targetRaw, String label) {
        if (!isVerified(channel, targetRaw)) {
            throw new IllegalStateException("Please verify your " + label + " before submitting.");
        }
    }

    /** Burns the verification so the same proof can't be reused for another submit. */
    @Transactional
    public void consume(Channel channel, String targetRaw) {
        repo.deleteByChannelAndTarget(channel, normalize(channel, targetRaw));
    }

    // ── Housekeeping ───────────────────────────────────────────────────────────

    @Scheduled(cron = "0 0 * * * *") // hourly
    @Transactional
    public void cleanupExpired() {
        try {
            repo.deleteExpiredUnverified(Instant.now());
        } catch (Exception e) {
            log.warn("Contact-verification cleanup failed: {}", e.getMessage());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private String normalize(Channel channel, String raw) {
        return channel == Channel.EMAIL ? normalizeEmail(raw) : normalizePhone(raw);
    }

    private String normalizeEmail(String email) {
        if (email == null) throw new IllegalArgumentException("Email is required");
        return email.trim().toLowerCase();
    }

    private String normalizePhone(String phone) {
        if (phone == null) throw new IllegalArgumentException("Phone number is required");
        return phone.trim();
    }
}
