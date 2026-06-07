package com.buyology.ecommerce.verification.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.verification.dto.VerifyEmailRequest;
import com.buyology.ecommerce.verification.dto.VerifyPhoneRequest;
import com.buyology.ecommerce.verification.service.ContactVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public contact-verification endpoints shared by the supplier apply, B2B membership
 * apply and (phone) profile flows.
 *
 * <p>Flow: client calls {@code .../start} to receive a code, then {@code .../check}
 * with the code. A successful check marks the contact verified for a short window;
 * the submit endpoints then require both contacts verified.</p>
 */
@RestController
@RequestMapping("/api/verify")
public class ContactVerificationController {

    private final ContactVerificationService verificationService;

    public ContactVerificationController(ContactVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    // ── Email (SendGrid) ───────────────────────────────────────────────────────

    @PostMapping("/email/start")
    public ResponseEntity<ApiResponse<String>> startEmail(@Valid @RequestBody VerifyEmailRequest req) {
        verificationService.startEmail(req.getEmail());
        return ApiResponse.success("sent", "Verification code sent to your email.");
    }

    @PostMapping("/email/check")
    public ResponseEntity<ApiResponse<String>> checkEmail(@Valid @RequestBody VerifyEmailRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Verification code is required.");
        }
        boolean ok = verificationService.checkEmail(req.getEmail(), req.getCode());
        return ok
                ? ApiResponse.success("verified", "Email verified successfully.")
                : ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid verification code.");
    }

    // ── Phone (Twilio Verify) ──────────────────────────────────────────────────

    @PostMapping("/phone/start")
    public ResponseEntity<ApiResponse<String>> startPhone(@Valid @RequestBody VerifyPhoneRequest req) {
        verificationService.startPhone(req.getPhoneNumber());
        return ApiResponse.success("sent", "Verification code sent via SMS.");
    }

    @PostMapping("/phone/check")
    public ResponseEntity<ApiResponse<String>> checkPhone(@Valid @RequestBody VerifyPhoneRequest req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Verification code is required.");
        }
        boolean ok = verificationService.checkPhone(req.getPhoneNumber(), req.getCode());
        return ok
                ? ApiResponse.success("verified", "Phone number verified successfully.")
                : ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid verification code.");
    }
}
