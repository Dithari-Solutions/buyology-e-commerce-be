package com.buyology.ecommerce.supplier.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.common.utils.EmailValidation;
import com.buyology.ecommerce.infrastructure.config.OtpProperties;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.supplier.domain.SupplierApplication;
import com.buyology.ecommerce.supplier.domain.SupplierApplication.ApplicationStatus;
import com.buyology.ecommerce.supplier.domain.SupplierApplication.PreferredContact;
import com.buyology.ecommerce.supplier.domain.SupplierOtp;
import com.buyology.ecommerce.supplier.domain.SupplierOtp.OtpChannel;
import com.buyology.ecommerce.supplier.dto.*;
import com.buyology.ecommerce.supplier.repository.SupplierApplicationRepository;
import com.buyology.ecommerce.supplier.repository.SupplierOtpRepository;
import com.buyology.ecommerce.user.service.SmsService;
import com.buyology.ecommerce.verification.domain.ContactVerification.Channel;
import com.buyology.ecommerce.verification.service.ContactVerificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SupplierApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SupplierApplicationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SupplierApplicationRepository applicationRepository;
    private final SupplierOtpRepository otpRepository;
    private final EmailService emailService;
    private final SmsService smsService;
    private final ContaboObjectService contaboObjectService;
    private final OtpProperties otpProperties;
    private final ObjectMapper objectMapper;
    private final ContactVerificationService verificationService;

    public SupplierApplicationService(
            SupplierApplicationRepository applicationRepository,
            SupplierOtpRepository otpRepository,
            EmailService emailService,
            SmsService smsService,
            ContaboObjectService contaboObjectService,
            OtpProperties otpProperties,
            ObjectMapper objectMapper,
            ContactVerificationService verificationService) {
        this.applicationRepository = applicationRepository;
        this.otpRepository = otpRepository;
        this.emailService = emailService;
        this.smsService = smsService;
        this.contaboObjectService = contaboObjectService;
        this.otpProperties = otpProperties;
        this.objectMapper = objectMapper;
        this.verificationService = verificationService;
    }

    // ── Step 1: Create application + send OTP ────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<UUID>> initiateApplication(SupplierStep1Request request) {

        if (!EmailValidation.isValid(request.getEmail())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email address is not valid");
        }

        String email = request.getEmail().trim().toLowerCase();
        String phone = request.getPhoneNumber() == null ? null : request.getPhoneNumber().trim();

        boolean alreadyExists = applicationRepository
                .existsByEmailAndStatusNot(email, ApplicationStatus.REJECTED);
        if (alreadyExists) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "A supplier application with this email already exists");
        }

        if (phone == null || phone.isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Phone number is required");
        }

        // Both contacts must be verified (email via SendGrid, phone via Twilio Verify)
        // through /api/verify/* before the application can be created.
        if (!verificationService.isVerified(Channel.EMAIL, email)) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "Please verify your email address before continuing.");
        }
        if (!verificationService.isVerified(Channel.PHONE, phone)) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "Please verify your phone number before continuing.");
        }

        SupplierApplication app = new SupplierApplication();
        app.setFullName(request.getFullName());
        app.setBusinessName(request.getBusinessName());
        app.setSellerType(request.getSellerType());
        app.setCountry(request.getCountry());
        app.setCity(request.getCity());
        app.setEmail(email);
        app.setPhoneNumber(phone);
        app.setPreferredContact(request.getPreferredContact());
        app.setOtpVerified(true); // email + phone already verified above
        applicationRepository.save(app);

        // Burn the verification proofs so they can't be reused for another application.
        verificationService.consume(Channel.EMAIL, email);
        verificationService.consume(Channel.PHONE, phone);

        return ApiResponse.success(app.getId(), "Application initiated. Please proceed to step 2.");
    }

    // ── OTP verification ─────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> verifyOtp(SupplierOtpRequest request) {

        SupplierApplication app = applicationRepository.findById(request.getApplicationId())
                .orElseGet(() -> null);
        if (app == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Application not found");
        }

        if (Boolean.TRUE.equals(app.getOtpVerified())) {
            return ApiResponse.success("already_verified", "Contact already verified");
        }

        Optional<SupplierOtp> otpOpt = otpRepository
                .findTopByApplicationIdAndUsedFalseOrderByCreatedAtDesc(app.getId());

        if (otpOpt.isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "No pending OTP found. Please request a new one.");
        }

        SupplierOtp otp = otpOpt.get();

        if (otp.isExpired()) {
            otp.setUsed(true);
            otpRepository.save(otp);
            return ApiResponse.failure(HttpStatus.GONE,
                    "OTP has expired. Please request a new code.");
        }

        if (otp.isMaxAttemptsReached()) {
            otp.setUsed(true);
            otpRepository.save(otp);
            return ApiResponse.failure(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many incorrect attempts. Please request a new OTP.");
        }

        if (!otp.getOtpCode().equals(request.getOtpCode())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            int remaining = 5 - otp.getAttempts();
            return ApiResponse.failure(HttpStatus.UNAUTHORIZED,
                    "Invalid OTP. " + remaining + " attempt(s) remaining.");
        }

        otp.setUsed(true);
        otpRepository.save(otp);
        app.setOtpVerified(true);
        applicationRepository.save(app);

        return ApiResponse.success("verified", "Contact verified successfully");
    }

    // ── OTP resend ───────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> resendOtp(UUID applicationId) {

        SupplierApplication app = applicationRepository.findById(applicationId)
                .orElseGet(() -> null);
        if (app == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Application not found");
        }

        Optional<SupplierOtp> lastOtp = otpRepository
                .findTopByApplicationIdAndUsedFalseOrderByCreatedAtDesc(applicationId);

        if (lastOtp.isPresent()) {
            SupplierOtp otp = lastOtp.get();
            Instant cooldownEnds = otp.getCreatedAt()
                    .plus(otpProperties.getResendCooldownSeconds(), ChronoUnit.SECONDS);
            if (!otp.isExpired() && Instant.now().isBefore(cooldownEnds)) {
                long secondsLeft = Instant.now().until(cooldownEnds, ChronoUnit.SECONDS);
                return ApiResponse.failure(HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait " + secondsLeft + " seconds before requesting a new OTP");
            }
            otp.setUsed(true);
            otpRepository.save(otp);
        }

/*
        sendOtp(app);
        return ApiResponse.success("sent", "New OTP sent");
*/
        return ApiResponse.success("not_needed", "OTP is not required for this process");
    }

    // ── Step 2: Product details ──────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> saveStep2(SupplierStep2Request request) {

        SupplierApplication app = findVerifiedApplication(request.getApplicationId());
        if (app == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND,
                    "Application not found or OTP not verified");
        }

        if (request.getProductCategories() != null) {
            try {
                app.setProductCategories(objectMapper.writeValueAsString(request.getProductCategories()));
            } catch (JsonProcessingException e) {
                app.setProductCategories("[]");
            }
        }
        app.setMainBrands(request.getMainBrands());
        app.setProductCondition(request.getProductCondition());
        app.setInitialListingRange(request.getInitialListingRange());
        applicationRepository.save(app);

        return ApiResponse.success("saved", "Step 2 saved");
    }

    // ── Step 3: Operations + document upload ────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> saveStep3(
            UUID applicationId,
            List<String> sellsElsewhere,
            String canProvideImages,
            String avgDispatchTime,
            String handlesReturns,
            String hasTradeLicense,
            String websiteOrSocialLink,
            MultipartFile tradeLicense) {

        SupplierApplication app = findVerifiedApplication(applicationId);
        if (app == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND,
                    "Application not found or OTP not verified");
        }

        if (sellsElsewhere != null) {
            try {
                app.setSellsElsewhere(objectMapper.writeValueAsString(sellsElsewhere));
            } catch (JsonProcessingException e) {
                app.setSellsElsewhere("[]");
            }
        }

        if (canProvideImages != null) {
            try {
                app.setCanProvideImages(
                        SupplierApplication.CanProvideImages.valueOf(canProvideImages));
            } catch (IllegalArgumentException ignored) {}
        }

        if (avgDispatchTime != null) {
            try {
                app.setAvgDispatchTime(
                        SupplierApplication.AvgDispatchTime.valueOf(avgDispatchTime));
            } catch (IllegalArgumentException ignored) {}
        }

        if (handlesReturns != null) {
            try {
                app.setHandlesReturns(
                        SupplierApplication.HandlesReturns.valueOf(handlesReturns));
            } catch (IllegalArgumentException ignored) {}
        }

        if (hasTradeLicense != null) {
            try {
                app.setHasTradeLicense(
                        SupplierApplication.TradeLicenseStatus.valueOf(hasTradeLicense));
            } catch (IllegalArgumentException ignored) {}
        }

        app.setWebsiteOrSocialLink(websiteOrSocialLink);

        if (tradeLicense != null && !tradeLicense.isEmpty()) {
            // Validate type/size/content and sanitize the filename before it becomes part
            // of the storage key (this endpoint is public — never trust the upload).
            com.buyology.ecommerce.common.utils.FileValidationUtils.validateDocument(tradeLicense);
            try {
                String safeName = com.buyology.ecommerce.common.utils.FileValidationUtils
                        .sanitizeFilename(tradeLicense.getOriginalFilename());
                String key = "suppliers/" + applicationId + "/trade-license-" + safeName;
                String uploadedKey = contaboObjectService.uploadFile(key, tradeLicense);
                app.setTradeLicenseKey(uploadedKey);
            } catch (Exception e) {
                log.warn("Failed to upload trade license for application {}: {}", applicationId, e.getMessage());
                return ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to upload document. Please try again.");
            }
        }

        applicationRepository.save(app);
        return ApiResponse.success("saved", "Step 3 saved");
    }

    // ── Step 4: Final submission ─────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<String>> submitApplication(SupplierSubmitRequest request) {

        SupplierApplication app = findVerifiedApplication(request.getApplicationId());
        if (app == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND,
                    "Application not found or OTP not verified");
        }

        if (!Boolean.TRUE.equals(request.getDeclarationAccepted())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "You must accept the declaration to submit");
        }

        if (request.getWhyBuyology() == null || request.getWhyBuyology().isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Please tell us why you want to sell on Buyology");
        }

        if (app.getStatus() != ApplicationStatus.PENDING) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Application already submitted");
        }

        app.setWhyBuyology(request.getWhyBuyology());
        app.setDeclarationAccepted(true);
        applicationRepository.save(app);

        emailService.sendSupplierApplicationReceivedEmail(
                app.getEmail(),
                app.getFullName(),
                app.getBusinessName() != null ? app.getBusinessName() : app.getFullName());

        return ApiResponse.success("submitted", "Application submitted successfully. We will review and contact you soon.");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void sendOtp(SupplierApplication app) {
        /*
        String otpCode = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));

        SupplierOtp otp = new SupplierOtp();
        otp.setApplicationId(app.getId());
        otp.setOtpCode(otpCode);
        otp.setExpiresAt(Instant.now().plus(otpProperties.getExpiryMinutes(), ChronoUnit.MINUTES));

        boolean usePhone = app.getPreferredContact() != PreferredContact.EMAIL
                && app.getPhoneNumber() != null && !app.getPhoneNumber().isBlank();

        if (usePhone) {
            otp.setChannel(OtpChannel.PHONE);
            otp.setTarget(app.getPhoneNumber());
            otpRepository.save(otp);
            smsService.sendOtp(app.getPhoneNumber(), otpCode);
        } else {
            otp.setChannel(OtpChannel.EMAIL);
            otp.setTarget(app.getEmail());
            otpRepository.save(otp);
            emailService.sendSupplierOtpEmail(app.getEmail(), otpCode);
        }
        */
    }

    private SupplierApplication findVerifiedApplication(UUID id) {
        return applicationRepository.findByIdAndDeletedAtIsNull(id)
                .filter(a -> Boolean.TRUE.equals(a.getOtpVerified()))
                .orElse(null);
    }
}
