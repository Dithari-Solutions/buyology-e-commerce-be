package com.buyology.ecommerce.auth.controller;

import com.buyology.ecommerce.auth.dto.AdminSignUpRequest;
import com.buyology.ecommerce.auth.dto.OtpVerifyRequest;
import com.buyology.ecommerce.auth.dto.SignInRequest;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.auth.service.AuthService;
import com.buyology.ecommerce.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// test ci-cd

@RestController
@RequestMapping("/auth/admin")
@Tag(name = "Admin Authentication", description = "Registration APIs for creating admin accounts")
public class AdminAuthController {

    private final AuthService authService;

    public AdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
        summary = "Initiate admin registration",
        description = "Validates credentials and sends an OTP to the admin's email."
    )
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<String>> adminSignup(@RequestBody AdminSignUpRequest request) {
        return authService.adminSignup(request);
    }

    @Operation(
        summary = "Verify OTP and complete admin registration",
        description = "Verifies the OTP and creates the ADMIN account. " +
                "Access token is returned in the JSON body. " +
                "Refresh token is delivered as an HttpOnly Set-Cookie header " +
                "(Path=/auth/refresh, MaxAge=7d). " +
                "NOTE: Swagger UI cannot display HttpOnly cookies — use curl or Postman to inspect."
    )
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<SignInResponse>> adminVerifyOtp(
            @RequestBody OtpVerifyRequest request,
            HttpServletRequest httpRequest) {
        return authService.adminVerifyOtp(request, httpRequest);
    }

    @Operation(
        summary = "Admin signin",
        description = "Authenticates a user as ADMIN. Returns 403 if the credentials belong to a non-admin account."
    )
    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<SignInResponse>> adminSignin(
            @RequestBody SignInRequest request,
            HttpServletRequest httpRequest) {
        return authService.adminSignin(request, httpRequest);
    }
}
