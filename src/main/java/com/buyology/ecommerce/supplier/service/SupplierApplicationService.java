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

    public SupplierApplicationService(
            SupplierApplicationRepository applicationRepository,
            SupplierOtpRepository otpRepository,
            EmailService emailService,
            SmsService smsService,
            ContaboObjectService contaboObjectService,
            OtpProperties otpProperties,
            ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.otpRepository = otpRepository;
        this.emailService = emailService;
        this.smsService = smsService;
        this.contaboObjectService = contaboObjectService;
        this.otpProperties = otpProperties;
        this.objectMapper = objectMapper;
    }

    // ── Step 1: Create application + send OTP ────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<UUID>> initiateApplication(SupplierStep1Request request) {

        if (!EmailValidation.isValid(request.getEmail())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email address is not valid");
        }

        boolean alreadyApproved = applicationRepository
                .existsByEmailAndStatusNot(request.getEmail(), ApplicationStatus.REJECTED);
        if (alreadyApproved) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "An application with this email already exists");
        }

        if (request.getPreferredContact() != PreferredContact.EMAIL
                && (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "Phone number is required for WhatsApp or Phone Call contact preference");
        }

        SupplierApplication app = new SupplierApplication();
        app.setFullName(request.getFullName());
        app.setBusinessName(request.getBusinessName());
        app.setSellerType(request.getSellerType());
        app.setCountry(request.getCountry());
        app.setCity(request.getCity());
        app.setEmail(request.getEmail());
        app.setPhoneNumber(request.getPhoneNumber());
        app.setPreferredContact(request.getPreferredContact());
        applicationRepository.save(app);

        sendOtp(app);

        return ApiResponse.success(app.getId(), "OTP sent. Please verify your contact.");
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

        sendOtp(app);
        return ApiResponse.success("sent", "New OTP sent");
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
            try {
                String key = "suppliers/" + applicationId + "/trade-license-"
                        + tradeLicense.getOriginalFilename();
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
    }

    private SupplierApplication findVerifiedApplication(UUID id) {
        return applicationRepository.findByIdAndDeletedAtIsNull(id)
                .filter(a -> Boolean.TRUE.equals(a.getOtpVerified()))
                .orElse(null);
    }
}
