package com.buyology.ecommerce.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.buyology.ecommerce.auth.dto.OtpVerifyRequest;
import com.buyology.ecommerce.auth.dto.SignInRequest;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.auth.dto.SignUpRequest;
import com.buyology.ecommerce.auth.dto.GoogleOAuthRequest;
import com.buyology.ecommerce.auth.service.AuthService;
import com.buyology.ecommerce.auth.service.GoogleOAuthService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.user.domain.Users;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Authentication APIs for users")
public class AuthController {

    private final AuthService authService;
    private final GoogleOAuthService googleOAuthService;

    public AuthController(AuthService authService, GoogleOAuthService googleOAuthService) {
        this.authService = authService;
        this.googleOAuthService = googleOAuthService;
    }

    /**
     * Step 1 — Submit email + password.
     * Validates input, then sends a 6-digit OTP to the provided email.
     * No account is created yet.
     */
    @Operation(summary = "Initiate registration", description = "Validates credentials and sends an OTP to the user's email")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<String>> signup(@RequestBody SignUpRequest request) {
        return authService.signup(request);
    }

    /**
     * Step 2 — Submit the OTP received by email.
     * If valid, creates the user account and returns JWT tokens.
     */
    @Operation(summary = "Verify OTP", description = "Verifies the OTP sent to the user's email and completes registration")
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<SignInResponse>> verifyOtp(@RequestBody OtpVerifyRequest request) {
        return authService.verifyOtp(request);
    }

    @Operation(summary = "Sign in", description = "Authenticate with email and password")
    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<SignInResponse>> signin(@RequestBody SignInRequest request) {
        return authService.signin(request);
    }

    @PostMapping("/google/callback")
    public ResponseEntity<ApiResponse<Users>> googleCallback(@RequestBody GoogleOAuthRequest request) {
        String code = request.getCode();
        if (code == null || code.isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Authorization code is required");
        }
        try {
            Users user = googleOAuthService.processGoogleOAuth(code);
            return ApiResponse.success(user, "Logged in successfully.");
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong during login");
        }
    }
}
