package com.buyology.ecommerce.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.domain.EmailOtp;
import com.buyology.ecommerce.auth.domain.EmailOtp.OtpType;
import com.buyology.ecommerce.auth.dto.AdminSignUpRequest;
import com.buyology.ecommerce.auth.dto.ForgotPasswordRequest;
import com.buyology.ecommerce.auth.dto.OtpVerifyRequest;
import com.buyology.ecommerce.auth.dto.ResetPasswordRequest;
import com.buyology.ecommerce.auth.dto.SignInRequest;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.auth.dto.SignUpRequest;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.auth.repository.EmailOtpRepository;
import com.buyology.ecommerce.auth.repository.RefreshTokenRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.common.utils.EmailValidation;
import com.buyology.ecommerce.common.utils.PasswordPolicy;
import com.buyology.ecommerce.common.utils.PasswordUtils;
import com.buyology.ecommerce.infrastructure.config.OtpProperties;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final EmailOtpRepository emailOtpRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailService emailService;
    private final OtpProperties otpProperties;
    private final UserRoleRepository userRoleRepository;
    private final LoginAttemptService loginAttemptService;
    private final com.buyology.ecommerce.common.audit.AuditService auditService;
    private final MfaService mfaService;

    public AuthService(
            TokenService tokenService,
            UserRepository userRepository,
            AuthCredentialRepository authCredentialRepository,
            EmailOtpRepository emailOtpRepository,
            RefreshTokenRepository refreshTokenRepository,
            EmailService emailService,
            OtpProperties otpProperties,
            UserRoleRepository userRoleRepository,
            LoginAttemptService loginAttemptService,
            com.buyology.ecommerce.common.audit.AuditService auditService,
            MfaService mfaService) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.emailOtpRepository = emailOtpRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailService = emailService;
        this.otpProperties = otpProperties;
        this.userRoleRepository = userRoleRepository;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
        this.mfaService = mfaService;
    }

    // ── Step 1: Initiate signup ───────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> signup(SignUpRequest request) {

        if (!EmailValidation.isValid(request.getEmail())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is not valid");
        }

        try {
            PasswordPolicy.validate(request.getPassword());
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        if (!request.getPassword().equals(request.getRepeatedPassword())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }

        boolean emailTaken = !authCredentialRepository
                .findAllByEmailAndProvider(request.getEmail(), "LOCAL").isEmpty();
        if (emailTaken) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        // Rate-limit: scoped to SIGNUP type only so a pending password-reset OTP isn't affected
        Optional<EmailOtp> existingOtp = emailOtpRepository
                .findTopByEmailAndUsedFalseAndTypeOrderByCreatedAtDesc(request.getEmail(), OtpType.SIGNUP);

        if (existingOtp.isPresent()) {
            EmailOtp otp = existingOtp.get();
            Instant cooldownEnds = otp.getCreatedAt().plus(otpProperties.getResendCooldownSeconds(), ChronoUnit.SECONDS);
            if (!otp.isExpired() && Instant.now().isBefore(cooldownEnds)) {
                long secondsLeft = Instant.now().until(cooldownEnds, ChronoUnit.SECONDS);
                return ApiResponse.failure(HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait " + secondsLeft + " seconds before requesting a new OTP");
            }
        }

        emailOtpRepository.invalidateAllForEmailAndType(request.getEmail(), OtpType.SIGNUP);

        String otpCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        EmailOtp newOtp = new EmailOtp();
        newOtp.setType(OtpType.SIGNUP);
        newOtp.setEmail(request.getEmail());
        newOtp.setPasswordHash(PasswordUtils.hashPassword(request.getPassword()));
        newOtp.setOtpCode(otpCode);
        newOtp.setExpiresAt(Instant.now().plus(otpProperties.getExpiryMinutes(), ChronoUnit.MINUTES));
        emailOtpRepository.save(newOtp);

        try {
            emailService.sendOtpEmail(request.getEmail(), otpCode);
        } catch (RuntimeException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("Email delivery failed for {}: {}", request.getEmail(), e.getMessage());
            return ApiResponse.failure(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not deliver the verification email. Please try again later.");
        }

        return ApiResponse.success("OTP sent to " + request.getEmail(),
                "Verification code sent. Please check your inbox.");
    }

    // ── Step 2: Verify OTP — create account, return tokens ───────────────────

    @Transactional
    public ResponseEntity<ApiResponse<SignInResponse>> verifyOtp(
            OtpVerifyRequest request, HttpServletRequest httpRequest) {

        if (!EmailValidation.isValid(request.getEmail())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is not valid");
        }

        if (request.getOtpCode() == null || request.getOtpCode().isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "OTP code is required");
        }

        Optional<EmailOtp> otpRecord = emailOtpRepository
                .findTopByEmailAndUsedFalseAndTypeOrderByCreatedAtDesc(request.getEmail(), OtpType.SIGNUP);

        if (otpRecord.isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "No pending verification found. Please sign up again.");
        }

        EmailOtp otp = otpRecord.get();

        if (otp.isExpired()) {
            otp.setUsed(true);
            emailOtpRepository.save(otp);
            return ApiResponse.failure(HttpStatus.GONE,
                    "OTP has expired. Please sign up again to receive a new code.");
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

        otp.setUsed(true);
        emailOtpRepository.save(otp);

        Users newUser = new Users();
        newUser.setUserType(Users.UserType.CUSTOMER);
        newUser.setIsGuest(false);
        newUser.setStatus("ACTIVE");
        newUser.setRegistrationIp(extractClientIp(httpRequest));
        newUser.setRegistrationDevice(extractDeviceInfo(httpRequest));
        userRepository.save(newUser);

        AuthCredentials credentials = new AuthCredentials();
        credentials.setUserId(newUser.getId());
        credentials.setEmail(otp.getEmail());
        credentials.setPasswordHash(otp.getPasswordHash());
        credentials.setProvider("LOCAL");
        credentials.setIsActive(true);
        authCredentialRepository.save(credentials);

        emailService.sendRegistrationSuccessEmail(otp.getEmail());

        return buildSigninResponse(credentials, httpRequest);
    }

    // ── Admin Signup ──────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> adminSignup(AdminSignUpRequest request) {

        if (request.getFirstName() == null || request.getFirstName().isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "First name is required");
        }

        if (request.getLastName() == null || request.getLastName().isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Last name is required");
        }

        if (!EmailValidation.isValid(request.getEmail())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is not valid");
        }

        try {
            PasswordPolicy.validate(request.getPassword());
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        if (!request.getPassword().equals(request.getRepeatedPassword())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }

        boolean emailTaken = !authCredentialRepository
                .findAllByEmailAndProvider(request.getEmail(), "LOCAL").isEmpty();
        if (emailTaken) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        Optional<EmailOtp> existingOtp = emailOtpRepository
                .findTopByEmailAndUsedFalseAndTypeOrderByCreatedAtDesc(request.getEmail(), OtpType.SIGNUP);

        if (existingOtp.isPresent()) {
            EmailOtp otp = existingOtp.get();
            Instant cooldownEnds = otp.getCreatedAt().plus(otpProperties.getResendCooldownSeconds(), ChronoUnit.SECONDS);
            if (!otp.isExpired() && Instant.now().isBefore(cooldownEnds)) {
                long secondsLeft = Instant.now().until(cooldownEnds, ChronoUnit.SECONDS);
                return ApiResponse.failure(HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait " + secondsLeft + " seconds before requesting a new OTP");
            }
        }

        emailOtpRepository.invalidateAllForEmailAndType(request.getEmail(), OtpType.SIGNUP);

        String otpCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        EmailOtp newOtp = new EmailOtp();
        newOtp.setType(OtpType.SIGNUP);
        newOtp.setEmail(request.getEmail());
        newOtp.setPasswordHash(PasswordUtils.hashPassword(request.getPassword()));
        newOtp.setFirstName(request.getFirstName());
        newOtp.setLastName(request.getLastName());
        newOtp.setOtpCode(otpCode);
        newOtp.setExpiresAt(Instant.now().plus(otpProperties.getExpiryMinutes(), ChronoUnit.MINUTES));
        emailOtpRepository.save(newOtp);

        try {
            emailService.sendOtpEmail(request.getEmail(), otpCode);
        } catch (RuntimeException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("Email delivery failed for {}: {}", request.getEmail(), e.getMessage());
            return ApiResponse.failure(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not deliver the verification email. Please try again later.");
        }

        return ApiResponse.success("OTP sent to " + request.getEmail(),
                "Verification code sent. Please check your inbox.");
    }

    @Transactional
    public ResponseEntity<ApiResponse<SignInResponse>> adminVerifyOtp(
            OtpVerifyRequest request, HttpServletRequest httpRequest) {

        if (!EmailValidation.isValid(request.getEmail())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is not valid");
        }

        if (request.getOtpCode() == null || request.getOtpCode().isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "OTP code is required");
        }

        Optional<EmailOtp> otpRecord = emailOtpRepository
                .findTopByEmailAndUsedFalseAndTypeOrderByCreatedAtDesc(request.getEmail(), OtpType.SIGNUP);

        if (otpRecord.isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "No pending verification found. Please sign up again.");
        }

        EmailOtp otp = otpRecord.get();

        if (otp.isExpired()) {
            otp.setUsed(true);
            emailOtpRepository.save(otp);
            return ApiResponse.failure(HttpStatus.GONE,
                    "OTP has expired. Please sign up again to receive a new code.");
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

        otp.setUsed(true);
        emailOtpRepository.save(otp);

        Users newUser = new Users();
        newUser.setFirstName(otp.getFirstName());
        newUser.setLastName(otp.getLastName());
        newUser.setUserType(Users.UserType.ADMIN);
        newUser.setIsGuest(false);
        newUser.setStatus("ACTIVE");
        newUser.setRegistrationIp(extractClientIp(httpRequest));
        newUser.setRegistrationDevice(extractDeviceInfo(httpRequest));
        userRepository.save(newUser);

        AuthCredentials credentials = new AuthCredentials();
        credentials.setUserId(newUser.getId());
        credentials.setEmail(otp.getEmail());
        credentials.setPasswordHash(otp.getPasswordHash());
        credentials.setProvider("LOCAL");
        credentials.setIsActive(true);
        authCredentialRepository.save(credentials);

        emailService.sendRegistrationSuccessEmail(otp.getEmail());

        return buildSigninResponse(credentials, httpRequest);
    }

    // ── Signin ────────────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<SignInResponse>> signin(
            SignInRequest request, HttpServletRequest httpRequest) {
        try {
            if (!EmailValidation.isValid(request.getEmail())) {
                return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is not valid");
            }

            if (loginAttemptService.isLockedOut(request.getEmail())) {
                return ApiResponse.failure(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many failed attempts. Try again in "
                                + loginAttemptService.lockoutDuration().toMinutes() + " minutes.");
            }

            Optional<AuthCredentials> existing = authCredentialRepository
                    .findByEmailAndProvider(request.getEmail(), "LOCAL");
            if (existing.isEmpty()) {
                loginAttemptService.recordFailure(request.getEmail());
                auditService.logFailure("AUTH_LOGIN", "AuthCredentials", request.getEmail(), "invalid_credentials");
                return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }

            AuthCredentials authCredentials = existing.get();

            if (!PasswordUtils.verifyPassword(request.getPassword(), authCredentials.getPasswordHash())) {
                loginAttemptService.recordFailure(request.getEmail());
                auditService.logFailure("AUTH_LOGIN", "AuthCredentials", request.getEmail(), "invalid_credentials");
                return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }

            var user = userRepository.findById(authCredentials.getUserId()).orElse(null);
            if (user != null && "SUSPENDED".equals(user.getStatus())) {
                return ApiResponse.failure(HttpStatus.FORBIDDEN,
                        "Your account has been suspended. Please contact support.");
            }

            loginAttemptService.recordSuccess(request.getEmail());
            auditService.logSuccess("AUTH_LOGIN", "AuthCredentials", authCredentials.getId().toString());
            return buildSigninResponse(authCredentials, httpRequest);

        } catch (Exception e) {
            log.error("Signin failed for {}: {}", request.getEmail(), e.getMessage(), e);
            return ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong during signin");
        }
    }

    // ── Admin signin (rejects non-ADMIN users) ───────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<SignInResponse>> adminSignin(
            SignInRequest request, HttpServletRequest httpRequest) {
        try {
            if (!EmailValidation.isValid(request.getEmail())) {
                return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is not valid");
            }

            if (loginAttemptService.isLockedOut(request.getEmail())) {
                return ApiResponse.failure(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many failed attempts. Try again in "
                                + loginAttemptService.lockoutDuration().toMinutes() + " minutes.");
            }

            Optional<AuthCredentials> existing = authCredentialRepository
                    .findByEmailAndProvider(request.getEmail(), "LOCAL");
            if (existing.isEmpty()) {
                loginAttemptService.recordFailure(request.getEmail());
                auditService.logFailure("AUTH_LOGIN", "AuthCredentials", request.getEmail(), "invalid_credentials");
                return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }

            AuthCredentials authCredentials = existing.get();

            if (!PasswordUtils.verifyPassword(request.getPassword(), authCredentials.getPasswordHash())) {
                loginAttemptService.recordFailure(request.getEmail());
                auditService.logFailure("AUTH_LOGIN", "AuthCredentials", request.getEmail(), "invalid_credentials");
                return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }
            loginAttemptService.recordSuccess(request.getEmail());

            Users user = userRepository.findById(authCredentials.getUserId()).orElse(null);
            if (user == null) {
                return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }

            if (user.getUserType() != Users.UserType.ADMIN) {
                // Vague message — don't reveal that the customer account exists
                return ApiResponse.failure(HttpStatus.FORBIDDEN,
                        "This account is not authorized to access the admin dashboard.");
            }

            // Suppliers must use /auth/supplier/signin; admin endpoint is non-supplier admins only.
            boolean isSupplier = userRoleRepository.findRoleNamesByUserId(user.getId())
                    .stream().anyMatch("SUPPLIER"::equalsIgnoreCase);
            if (isSupplier) {
                return ApiResponse.failure(HttpStatus.FORBIDDEN,
                        "Supplier accounts must sign in via the supplier login.");
            }

            if ("SUSPENDED".equals(user.getStatus())) {
                return ApiResponse.failure(HttpStatus.FORBIDDEN,
                        "Your account has been suspended. Please contact support.");
            }

            return buildSigninResponse(authCredentials, httpRequest);

        } catch (Exception e) {
            log.error("Admin signin failed for {}: {}", request.getEmail(), e.getMessage(), e);
            return ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong during signin");
        }
    }

    // ── Supplier signin (rejects anyone without the SUPPLIER role) ──────────

    @Transactional
    public ResponseEntity<ApiResponse<SignInResponse>> supplierSignin(
            SignInRequest request, HttpServletRequest httpRequest) {
        try {
            if (!EmailValidation.isValid(request.getEmail())) {
                return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is not valid");
            }

            if (loginAttemptService.isLockedOut(request.getEmail())) {
                return ApiResponse.failure(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many failed attempts. Try again in "
                                + loginAttemptService.lockoutDuration().toMinutes() + " minutes.");
            }

            Optional<AuthCredentials> existing = authCredentialRepository
                    .findByEmailAndProvider(request.getEmail(), "LOCAL");
            if (existing.isEmpty()) {
                loginAttemptService.recordFailure(request.getEmail());
                auditService.logFailure("AUTH_LOGIN", "AuthCredentials", request.getEmail(), "invalid_credentials");
                return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }

            AuthCredentials authCredentials = existing.get();

            if (!PasswordUtils.verifyPassword(request.getPassword(), authCredentials.getPasswordHash())) {
                loginAttemptService.recordFailure(request.getEmail());
                auditService.logFailure("AUTH_LOGIN", "AuthCredentials", request.getEmail(), "invalid_credentials");
                return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }
            loginAttemptService.recordSuccess(request.getEmail());

            Users user = userRepository.findById(authCredentials.getUserId()).orElse(null);
            if (user == null) {
                return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }

            boolean isSupplier = userRoleRepository.findRoleNamesByUserId(user.getId())
                    .stream().anyMatch("SUPPLIER"::equalsIgnoreCase);
            if (!isSupplier) {
                return ApiResponse.failure(HttpStatus.FORBIDDEN,
                        "This endpoint is for supplier accounts only.");
            }

            if ("SUSPENDED".equals(user.getStatus())) {
                return ApiResponse.failure(HttpStatus.FORBIDDEN,
                        "Your account has been suspended. Please contact support.");
            }

            return buildSigninResponse(authCredentials, httpRequest);

        } catch (Exception e) {
            log.error("Supplier signin failed for {}: {}", request.getEmail(), e.getMessage(), e);
            return ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong during signin");
        }
    }

    // ── Forgot Password — Step 1: Send reset OTP ─────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> forgotPassword(ForgotPasswordRequest request) {

        if (!EmailValidation.isValid(request.getEmail())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is not valid");
        }

        // Intentionally vague: don't reveal whether the email is registered
        // to prevent user-enumeration attacks.
        Optional<AuthCredentials> existing = authCredentialRepository
                .findByEmailAndProvider(request.getEmail(), "LOCAL");

        if (existing.isEmpty()) {
            // Return the same 200 OK so the caller can't tell if the email exists
            return ApiResponse.success(null,
                    "If this email is registered, a reset code has been sent.");
        }

        // Rate-limit: scoped to PASSWORD_RESET type only
        Optional<EmailOtp> existingOtp = emailOtpRepository
                .findTopByEmailAndUsedFalseAndTypeOrderByCreatedAtDesc(request.getEmail(), OtpType.PASSWORD_RESET);

        if (existingOtp.isPresent()) {
            EmailOtp otp = existingOtp.get();
            Instant cooldownEnds = otp.getCreatedAt().plus(otpProperties.getResendCooldownSeconds(), ChronoUnit.SECONDS);
            if (!otp.isExpired() && Instant.now().isBefore(cooldownEnds)) {
                long secondsLeft = Instant.now().until(cooldownEnds, ChronoUnit.SECONDS);
                return ApiResponse.failure(HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait " + secondsLeft + " seconds before requesting a new reset code");
            }
        }

        // Invalidate any previous unused reset OTPs for this email
        emailOtpRepository.invalidateAllForEmailAndType(request.getEmail(), OtpType.PASSWORD_RESET);

        String otpCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        EmailOtp newOtp = new EmailOtp();
        newOtp.setType(OtpType.PASSWORD_RESET);
        newOtp.setEmail(request.getEmail());
        // passwordHash is not needed for PASSWORD_RESET — entity default ("") satisfies NOT NULL
        newOtp.setOtpCode(otpCode);
        newOtp.setExpiresAt(Instant.now().plus(otpProperties.getExpiryMinutes(), ChronoUnit.MINUTES));
        emailOtpRepository.save(newOtp);

        try {
            emailService.sendPasswordResetOtpEmail(request.getEmail(), otpCode);
        } catch (RuntimeException e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("Password reset email delivery failed for {}: {}", request.getEmail(), e.getMessage());
            return ApiResponse.failure(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not deliver the reset email. Please try again later.");
        }

        return ApiResponse.success(null,
                "If this email is registered, a reset code has been sent.");
    }

    // ── Forgot Password — Step 2: Verify OTP + set new password ──────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> resetPassword(ResetPasswordRequest request) {

        if (!EmailValidation.isValid(request.getEmail())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is not valid");
        }

        if (request.getOtpCode() == null || request.getOtpCode().isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Reset code is required");
        }

        try {
            PasswordPolicy.validate(request.getNewPassword());
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        if (!request.getNewPassword().equals(request.getRepeatPassword())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }

        Optional<EmailOtp> otpRecord = emailOtpRepository
                .findTopByEmailAndUsedFalseAndTypeOrderByCreatedAtDesc(request.getEmail(), OtpType.PASSWORD_RESET);

        if (otpRecord.isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "No pending reset found. Please request a new reset code.");
        }

        EmailOtp otp = otpRecord.get();

        if (otp.isExpired()) {
            otp.setUsed(true);
            emailOtpRepository.save(otp);
            return ApiResponse.failure(HttpStatus.GONE,
                    "Reset code has expired. Please request a new one.");
        }

        if (otp.isMaxAttemptsReached()) {
            otp.setUsed(true);
            emailOtpRepository.save(otp);
            return ApiResponse.failure(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Please request a new reset code.");
        }

        if (!otp.getOtpCode().equals(request.getOtpCode())) {
            otp.setAttempts(otp.getAttempts() + 1);
            emailOtpRepository.save(otp);
            int remaining = 5 - otp.getAttempts();
            return ApiResponse.failure(HttpStatus.UNAUTHORIZED,
                    "Invalid code. " + remaining + " attempt(s) remaining.");
        }

        // OTP is valid — consume it immediately to prevent replay
        otp.setUsed(true);
        emailOtpRepository.save(otp);

        Optional<AuthCredentials> existing = authCredentialRepository
                .findByEmailAndProvider(request.getEmail(), "LOCAL");

        if (existing.isEmpty()) {
            // This path is theoretically unreachable (forgotPassword already checked),
            // but handled defensively.
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Account not found");
        }

        AuthCredentials credentials = existing.get();

        // Update the password
        credentials.setPasswordHash(PasswordUtils.hashPassword(request.getNewPassword()));
        authCredentialRepository.save(credentials);

        // Revoke all active refresh tokens so every device must re-authenticate
        int revoked = refreshTokenRepository.revokeAllActiveByAuthCredential(credentials, Instant.now());
        log.info("Revoked {} active refresh token(s) for {} after password reset", revoked, request.getEmail());

        return ApiResponse.success(null, "Password reset successfully. Please sign in with your new password.");
    }

    // ── Shared token issuance ─────────────────────────────────────────────────

    public ResponseEntity<ApiResponse<SignInResponse>> buildSigninResponse(
            AuthCredentials credentials, HttpServletRequest httpRequest) {
        return buildSigninResponse(credentials, httpRequest, false);
    }

    /**
     * Issues the access token + refresh cookie for a successful authentication.
     *
     * Privileged accounts (admins, suppliers) are gated by two-factor auth: unless
     * {@code mfaSatisfied} is true (i.e. the caller has just verified a TOTP code),
     * this returns an MFA challenge instead of tokens — either "setup required"
     * (mandatory, not yet enrolled) or "verification required" (already enrolled).
     * Non-privileged (customer) accounts are never gated.
     */
    public ResponseEntity<ApiResponse<SignInResponse>> buildSigninResponse(
            AuthCredentials credentials, HttpServletRequest httpRequest, boolean mfaSatisfied) {

        String audience = extractAudience(httpRequest);

        // Privileged accounts (admins, suppliers) may only sign in from the dashboard.
        // ADMIN userType OR any of the {SUPPLIER, ADMIN, SUPERADMIN, CUSTOMER_SUPPORT} roles.
        var userOpt = userRepository.findById(credentials.getUserId());
        boolean isAdminUserType = userOpt.isPresent()
                && userOpt.get().getUserType() == Users.UserType.ADMIN;
        var roleNames = userRoleRepository.findRoleNamesByUserId(credentials.getUserId());
        boolean isPrivilegedRole = roleNames.stream().anyMatch(r ->
                "SUPPLIER".equalsIgnoreCase(r)
                        || "ADMIN".equalsIgnoreCase(r)
                        || "SUPERADMIN".equalsIgnoreCase(r)
                        || "CUSTOMER_SUPPORT".equalsIgnoreCase(r));
        boolean privileged = isAdminUserType || isPrivilegedRole;
        if (privileged && !"dashboard".equals(audience)) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN,
                    "This account may only sign in from the admin dashboard");
        }

        // Mandatory 2FA for privileged accounts. Until the second factor is verified
        // we hand back a short-lived challenge ticket instead of a session.
        if (privileged && !mfaSatisfied) {
            SignInResponse challenge = mfaService.buildChallenge(credentials);
            String message = Boolean.TRUE.equals(challenge.getMfaSetupRequired())
                    ? "Two-factor authentication setup required"
                    : "Two-factor authentication code required";
            return ResponseEntity.ok(new ApiResponse<>(200, message, challenge));
        }

        userRepository.findById(credentials.getUserId()).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);
        });

        String accessToken = tokenService.generateAccessToken(credentials, audience);
        var refreshToken = tokenService.generateRefreshToken(credentials, extractDeviceInfo(httpRequest));
        String cookieHeader = tokenService.buildRefreshTokenCookieString(refreshToken.rawValue());

        SignInResponse body = new SignInResponse(accessToken, tokenService.getAccessTokenExpirySeconds());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieHeader)
                .body(new ApiResponse<>(200, "Signin successful", body));
    }

    private String extractAudience(HttpServletRequest request) {
        if (request == null) return "web";
        String header = request.getHeader("X-Client-Type");
        if (header == null || header.isBlank()) return "web";
        String normalized = header.trim().toLowerCase();
        return switch (normalized) {
            case "dashboard", "web", "mobile" -> normalized;
            default -> "web";
        };
    }

    public String extractDeviceInfo(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) return null;
        return ua.length() > 500 ? ua.substring(0, 500) : ua;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
