package com.buyology.ecommerce.auth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.buyology.ecommerce.auth.dto.AppleOAuthRequest;
import com.buyology.ecommerce.auth.dto.ForgotPasswordRequest;
import com.buyology.ecommerce.auth.dto.GoogleOAuthRequest;
import com.buyology.ecommerce.auth.dto.OtpVerifyRequest;
import com.buyology.ecommerce.auth.dto.ResetPasswordRequest;
import com.buyology.ecommerce.auth.dto.SignInRequest;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.auth.dto.SignUpRequest;
import com.buyology.ecommerce.auth.service.AppleOAuthService;
import com.buyology.ecommerce.auth.service.AuthService;
import com.buyology.ecommerce.auth.service.GoogleOAuthService;
import com.buyology.ecommerce.auth.service.TokenService;
import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.service.TokenService.RotateTokensResult;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.user.domain.Users;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Authentication APIs for users")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final GoogleOAuthService googleOAuthService;
    private final AppleOAuthService appleOAuthService;
    private final TokenService tokenService;

    public AuthController(AuthService authService,
            GoogleOAuthService googleOAuthService,
            AppleOAuthService appleOAuthService,
            TokenService tokenService) {
        this.authService = authService;
        this.googleOAuthService = googleOAuthService;
        this.appleOAuthService = appleOAuthService;
        this.tokenService = tokenService;
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Operation(summary = "Initiate registration",
            description = "Validates credentials and sends an OTP to the user's email")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<String>> signup(@RequestBody SignUpRequest request) {
        return authService.signup(request);
    }

    @Operation(summary = "Verify OTP",
            description = "Verifies the OTP and completes registration. " +
                    "Access token is returned in the JSON body. " +
                    "Refresh token is delivered as an HttpOnly Set-Cookie header " +
                    "(Path=/auth/refresh, MaxAge=7d). " +
                    "NOTE: Swagger UI cannot display HttpOnly cookies — use curl or Postman to inspect.")
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<SignInResponse>> verifyOtp(
            @RequestBody OtpVerifyRequest request,
            HttpServletRequest httpRequest) {
        return authService.verifyOtp(request, httpRequest);
    }

    // ── Signin ────────────────────────────────────────────────────────────────

    @Operation(summary = "Sign in",
            description = "Authenticate with email and password. " +
                    "Access token is returned in the JSON body. " +
                    "Refresh token is delivered as an HttpOnly Set-Cookie header " +
                    "(Path=/auth/refresh, MaxAge=7d). " +
                    "NOTE: Swagger UI cannot display HttpOnly cookies — use curl or Postman to inspect.")
    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<SignInResponse>> signin(
            @RequestBody SignInRequest request,
            HttpServletRequest httpRequest) {
        return authService.signin(request, httpRequest);
    }

    // ── Forgot / Reset password ───────────────────────────────────────────────

    @Operation(summary = "Forgot password",
            description = "Sends a 6-digit OTP to the registered email. " +
                    "Always returns 200 OK regardless of whether the email is registered " +
                    "(prevents user-enumeration). OTP expires in 10 minutes, max 5 attempts.")
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(
            @RequestBody ForgotPasswordRequest request) {
        return authService.forgotPassword(request);
    }

    @Operation(summary = "Reset password",
            description = "Verifies the OTP sent to the email and updates the password. " +
                    "All active refresh tokens are revoked on success (forces re-login on every device).")
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    // ── Token refresh ─────────────────────────────────────────────────────────

    @Operation(summary = "Refresh access token",
            description = "Reads the refresh_token HttpOnly cookie (Path=/auth/refresh), validates it, " +
                    "rotates it (old token revoked), and returns a new access token in JSON. " +
                    "A new HttpOnly cookie with the rotated refresh token is also set.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<SignInResponse>> refresh(
            @CookieValue(name = TokenService.REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletRequest httpRequest) {

        if (refreshToken == null || refreshToken.isBlank()) {
            return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "No refresh token provided");
        }

        try {
            String deviceInfo = httpRequest.getHeader("User-Agent");
            RotateTokensResult result = tokenService.rotateTokens(refreshToken, deviceInfo);

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, result.refreshCookieHeader())
                    .body(new ApiResponse<>(200, "Token refreshed successfully", result.signInResponse()));

        } catch (SecurityException e) {
            return ApiResponse.failure(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage(), e);
            return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Operation(summary = "Logout",
            description = "Revokes the refresh token in the database and clears the HttpOnly cookie.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            @CookieValue(name = TokenService.REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {

        if (refreshToken != null && !refreshToken.isBlank()) {
            tokenService.revokeRefreshToken(refreshToken);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, tokenService.buildClearRefreshTokenCookieString())
                .body(new ApiResponse<>(200, "Logged out successfully", null));
    }

    // ── Google OAuth ──────────────────────────────────────────────────────────

    @Operation(summary = "Google OAuth login",
            description = "Handles Google OAuth2 callback. Returns access and refresh tokens.")
    @PostMapping("/google/callback")
    public ResponseEntity<ApiResponse<SignInResponse>> googleCallback(
            @RequestBody GoogleOAuthRequest request,
            HttpServletRequest httpRequest) {
        String code = request.getCode();
        if (code == null || code.isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Authorization code is required");
        }
        try {
            AuthCredentials creds = googleOAuthService.processGoogleOAuth(code);
            return authService.buildSigninResponse(creds, httpRequest);
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Google OAuth callback failed: {}", e.getMessage(), e);
            return ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong during login");
        }
    }

    // ── Apple OAuth ───────────────────────────────────────────────────────────

    @Operation(summary = "Apple OAuth login",
            description = "Handles Apple OAuth2 callback. Returns access and refresh tokens.")
    @PostMapping("/apple/callback")
    public ResponseEntity<ApiResponse<SignInResponse>> appleCallback(
            @RequestBody AppleOAuthRequest request,
            HttpServletRequest httpRequest) {
        if (request.getCode() == null || request.getCode().isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Authorization code is required");
        }
        try {
            AuthCredentials creds = appleOAuthService.processAppleOAuth(request);
            return authService.buildSigninResponse(creds, httpRequest);
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Apple OAuth callback failed: {}", e.getMessage(), e);
            return ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong during login");
        }
    }
}
