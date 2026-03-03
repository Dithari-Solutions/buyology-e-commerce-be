package com.buyology.ecommerce.auth.controller;

import com.buyology.ecommerce.auth.dto.AdminSignUpRequest;
import com.buyology.ecommerce.auth.dto.OtpVerifyRequest;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.auth.service.AuthService;
import com.buyology.ecommerce.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/admin")
@Tag(name = "Admin Authentication", description = "Registration APIs for creating admin accounts")
public class AdminAuthController {

    private final AuthService authService;

    public AdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Step 1 — Submit name, email and password.
     * Validates input, then sends a 6-digit OTP to the provided email.
     * No account is created yet.
     */
    @Operation(
        summary = "Initiate admin registration",
        description = "Validates credentials and sends an OTP to the admin's email. Requires first name, last name, email, password and repeated password."
    )
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<String>> adminSignup(@RequestBody AdminSignUpRequest request) {
        return authService.adminSignup(request);
    }

    /**
     * Step 2 — Submit the OTP received by email.
     * If valid, creates the admin account (UserType=ADMIN) and returns JWT tokens.
     */
    @Operation(
        summary = "Verify OTP and complete admin registration",
        description = "Verifies the OTP sent to the admin's email and creates the account with ADMIN user type"
    )
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<SignInResponse>> adminVerifyOtp(@RequestBody OtpVerifyRequest request) {
        return authService.adminVerifyOtp(request);
    }
}
