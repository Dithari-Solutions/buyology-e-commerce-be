package com.buyology.ecommerce.auth.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.domain.EmailOtp;
import com.buyology.ecommerce.auth.dto.OtpVerifyRequest;
import com.buyology.ecommerce.auth.dto.SignInRequest;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.auth.dto.SignUpRequest;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.auth.repository.EmailOtpRepository;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.common.utils.EmailValidation;
import com.buyology.ecommerce.common.utils.PasswordUtils;
import com.buyology.ecommerce.infrastructure.config.OtpProperties;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final EmailOtpRepository emailOtpRepository;
    private final EmailService emailService;
    private final OtpProperties otpProperties;

    public AuthService(
            TokenService tokenService,
            UserRepository userRepository,
            AuthCredentialRepository authCredentialRepository,
            EmailOtpRepository emailOtpRepository,
            EmailService emailService,
            OtpProperties otpProperties) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.emailOtpRepository = emailOtpRepository;
        this.emailService = emailService;
        this.otpProperties = otpProperties;
    }

    // ── Step 1: Initiate signup — validate, store OTP, send email ─────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> signup(SignUpRequest request) {

        if (!EmailValidation.isValid(request.getEmail())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is not valid");
        }

        if (request.getPassword() == null || request.getPassword().length() < 8) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
        }

        if (!request.getPassword().equals(request.getRepeatedPassword())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }

        // Reject if the email is already registered
        if (authCredentialRepository.findByEmailAndProvider(request.getEmail(), "LOCAL").isPresent()) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        // Rate-limit: don't allow a new OTP if one was issued within the cooldown window
        Optional<EmailOtp> existingOtp = emailOtpRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(request.getEmail());

        if (existingOtp.isPresent()) {
            EmailOtp otp = existingOtp.get();
            Instant cooldownEnds = otp.getCreatedAt().plus(otpProperties.getResendCooldownSeconds(), ChronoUnit.SECONDS);
            if (!otp.isExpired() && Instant.now().isBefore(cooldownEnds)) {
                long secondsLeft = Instant.now().until(cooldownEnds, ChronoUnit.SECONDS);
                return ApiResponse.failure(HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait " + secondsLeft + " seconds before requesting a new OTP");
            }
        }

        // Invalidate any previous unused OTPs for this email before issuing a new one
        emailOtpRepository.invalidateAllForEmail(request.getEmail());

        // Generate a cryptographically secure 6-digit OTP
        String otpCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));

        EmailOtp newOtp = new EmailOtp();
        newOtp.setEmail(request.getEmail());
        newOtp.setPasswordHash(PasswordUtils.hashPassword(request.getPassword()));
        newOtp.setOtpCode(otpCode);
        newOtp.setExpiresAt(Instant.now().plus(otpProperties.getExpiryMinutes(), ChronoUnit.MINUTES));
        emailOtpRepository.save(newOtp);

        // Send OTP via Twilio SendGrid
        try {
            emailService.sendOtpEmail(request.getEmail(), otpCode);
        } catch (RuntimeException e) {
            // Roll back the OTP record so the user can cleanly retry
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("Email delivery failed for {}: {}", request.getEmail(), e.getMessage());
            return ApiResponse.failure(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not deliver the verification email. Please try again later.");
        }

        return ApiResponse.success(
                "OTP sent to " + request.getEmail(),
                "Verification code sent. Please check your inbox.");
    }

    // ── Step 2: Verify OTP — create account, return JWT tokens ───────────────

    @Transactional
    public ResponseEntity<ApiResponse<SignInResponse>> verifyOtp(OtpVerifyRequest request) {

        if (!EmailValidation.isValid(request.getEmail())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is not valid");
        }

        if (request.getOtpCode() == null || request.getOtpCode().isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "OTP code is required");
        }

        Optional<EmailOtp> otpRecord = emailOtpRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(request.getEmail());

        if (otpRecord.isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "No pending verification found. Please sign up again.");
        }

        EmailOtp otp = otpRecord.get();

        if (otp.isExpired()) {
            otp.setUsed(true);
            emailOtpRepository.save(otp);
            return ApiResponse.failure(HttpStatus.GONE, "OTP has expired. Please sign up again to receive a new code.");
        }

        if (otp.isMaxAttemptsReached()) {
            otp.setUsed(true);
            emailOtpRepository.save(otp);
            return ApiResponse.failure(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Please sign up again.");
        }

        if (!otp.getOtpCode().equals(request.getOtpCode())) {
            otp.setAttempts(otp.getAttempts() + 1);
            emailOtpRepository.save(otp);
            int remaining = 5 - otp.getAttempts();
            return ApiResponse.failure(HttpStatus.UNAUTHORIZED,
                    "Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        // OTP is valid — mark it used immediately to prevent replay attacks
        otp.setUsed(true);
        emailOtpRepository.save(otp);

        // Create the user account
        Users newUser = new Users();
        newUser.setIsGuest(false);
        newUser.setStatus("ACTIVE");
        userRepository.save(newUser);

        AuthCredentials credentials = new AuthCredentials();
        credentials.setUserId(newUser.getId());
        credentials.setEmail(otp.getEmail());
        credentials.setPasswordHash(otp.getPasswordHash());
        credentials.setProvider("LOCAL");
        credentials.setIsActive(true);
        authCredentialRepository.save(credentials);

        // Send welcome email (best-effort — failure does not block the response)
        emailService.sendRegistrationSuccessEmail(otp.getEmail());

        // Issue tokens
        String accessToken = tokenService.generateAccessToken(credentials);
        var refreshToken = tokenService.generateRefreshToken(credentials);

        return ApiResponse.signinSuccess(
                accessToken,
                refreshToken.getToken(),
                tokenService.getAccessTokenExpirySeconds());
    }

    // ── Signin ───────────────────────────────────────────────────────────────

    public ResponseEntity<ApiResponse<SignInResponse>> signin(SignInRequest request) {
        try {
            if (!EmailValidation.isValid(request.getEmail())) {
                return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is not valid");
            }

            Optional<AuthCredentials> existing = authCredentialRepository
                    .findByEmailAndProvider(request.getEmail(), "LOCAL");
            if (existing.isEmpty()) {
                return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }

            AuthCredentials authCredentials = existing.get();

            if (!PasswordUtils.verifyPassword(request.getPassword(), authCredentials.getPasswordHash())) {
                return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }

            String accessToken = tokenService.generateAccessToken(authCredentials);
            var refreshToken = tokenService.generateRefreshToken(authCredentials);

            return ApiResponse.signinSuccess(
                    accessToken,
                    refreshToken.getToken(),
                    tokenService.getAccessTokenExpirySeconds());

        } catch (Exception e) {
            log.error("Signin failed for {}: {}", request.getEmail(), e.getMessage(), e);
            return ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong during signin");
        }
    }
}
