package com.buyology.ecommerce.auth.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.dto.MfaCodeRequest;
import com.buyology.ecommerce.auth.dto.MfaEnrollStartResponse;
import com.buyology.ecommerce.auth.dto.MfaStatusResponse;
import com.buyology.ecommerce.auth.dto.MfaTicketRequest;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.auth.service.AuthService;
import com.buyology.ecommerce.auth.service.MfaService;
import com.buyology.ecommerce.auth.service.MfaService.MfaResult;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.utils.SecurityUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Google Authenticator (TOTP) two-factor endpoints.
 *
 * The enrollment/verify steps are reached with a short-lived MFA ticket issued by
 * the sign-in flow (no full session yet), so they live under the public
 * {@code /auth/**} path. The settings endpoints require a real session and are
 * guarded with method-level security.
 */
@RestController
@RequestMapping("/auth/mfa")
@Tag(name = "Two-Factor Authentication", description = "Google Authenticator (TOTP) enrollment and verification")
public class MfaController {

    private final MfaService mfaService;
    private final AuthService authService;
    private final AuthCredentialRepository authCredentialRepository;

    public MfaController(MfaService mfaService,
                         AuthService authService,
                         AuthCredentialRepository authCredentialRepository) {
        this.mfaService = mfaService;
        this.authService = authService;
        this.authCredentialRepository = authCredentialRepository;
    }

    @Operation(summary = "Begin 2FA enrollment",
            description = "Exchanges the enrollment ticket for a QR code (PNG data URI) and the base32 secret.")
    @PostMapping("/enroll/start")
    public ResponseEntity<ApiResponse<MfaEnrollStartResponse>> startEnrollment(
            @Valid @RequestBody MfaTicketRequest request) {
        return mfaService.startEnrollment(request.getMfaToken());
    }

    @Operation(summary = "Confirm 2FA enrollment and complete sign-in",
            description = "Verifies the first TOTP code. On success enables 2FA, returns the one-time "
                    + "recovery codes, and issues the access token + refresh cookie.")
    @PostMapping("/enroll/confirm")
    public ResponseEntity<ApiResponse<SignInResponse>> confirmEnrollment(
            @Valid @RequestBody MfaCodeRequest request, HttpServletRequest httpRequest) {
        MfaResult result = mfaService.confirmEnrollment(request.getMfaToken(), request.getCode());
        return completeSignin(result, httpRequest);
    }

    @Operation(summary = "Verify 2FA code at sign-in",
            description = "Verifies a TOTP code (or a recovery code) for an enrolled account and issues "
                    + "the access token + refresh cookie.")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<SignInResponse>> verify(
            @Valid @RequestBody MfaCodeRequest request, HttpServletRequest httpRequest) {
        MfaResult result = mfaService.verifyLogin(request.getMfaToken(), request.getCode());
        return completeSignin(result, httpRequest);
    }

    @Operation(summary = "Get the current user's 2FA status")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<MfaStatusResponse>> status() {
        return mfaService.status(SecurityUtils.currentUserId());
    }

    @Operation(summary = "Regenerate recovery codes",
            description = "Requires a valid current authenticator code. Invalidates all previous recovery codes.")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/recovery-codes/regenerate")
    public ResponseEntity<ApiResponse<List<String>>> regenerateRecoveryCodes(
            @Valid @RequestBody MfaCodeRequest request) {
        return mfaService.regenerateRecoveryCodes(SecurityUtils.currentUserId(), request.getCode());
    }

    // ── Shared: turn a verified MFA result into a real session ────────────────

    private ResponseEntity<ApiResponse<SignInResponse>> completeSignin(
            MfaResult result, HttpServletRequest httpRequest) {
        if (!result.success()) {
            return ApiResponse.failure(result.errorStatus(), result.errorMessage());
        }
        AuthCredentials credentials = authCredentialRepository.findById(result.authCredentialId()).orElse(null);
        if (credentials == null) {
            return ApiResponse.failure(org.springframework.http.HttpStatus.UNAUTHORIZED, "Account not found");
        }
        ResponseEntity<ApiResponse<SignInResponse>> response =
                authService.buildSigninResponse(credentials, httpRequest, true);

        // Surface the one-time recovery codes (enrollment only) in the session body.
        if (result.recoveryCodes() != null && response.getBody() != null
                && response.getBody().getData() != null) {
            response.getBody().getData().setRecoveryCodes(result.recoveryCodes());
        }
        return response;
    }
}
