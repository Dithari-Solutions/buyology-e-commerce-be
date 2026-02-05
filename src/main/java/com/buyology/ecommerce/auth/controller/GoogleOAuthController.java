package com.buyology.ecommerce.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.buyology.ecommerce.user.domain.Users;
import org.springframework.web.bind.annotation.*;
import com.buyology.ecommerce.auth.dto.GoogleOAuthRequest;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.auth.service.GoogleOAuthService;

@RestController
@RequestMapping("/auth/google")
public class GoogleOAuthController {

    private final GoogleOAuthService googleOAuthService;

    public GoogleOAuthController(GoogleOAuthService googleOAuthService) {
        this.googleOAuthService = googleOAuthService;
    }

    @PostMapping("/callback")
    public ResponseEntity<ApiResponse<Users>> googleCallback(@RequestBody GoogleOAuthRequest request) {
        String code = request.getCode();
        if (code == null || code.isEmpty()) {
            // return ApiResponse.failure()
            //         .body(ApiResponse.failure(HttpStatus.BAD_REQUEST, "Authorization code is required"));
            return ApiResponse.<Users>failure(HttpStatus.BAD_REQUEST, "Authorization code is required");
        }

        try {
            Users user = googleOAuthService.processGoogleOAuth(code);
            // return ResponseEntity.ok(ApiResponse.success(user, "Logged in successfully"));
            return ApiResponse.<Users>success(user, "Logged in successfully.");
        } catch (IllegalArgumentException e) {
            // return ResponseEntity.badRequest()
            //         .body(ApiResponse.failure(400, e.getMessage()));
            return ApiResponse.<Users>failure(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            // return ResponseEntity.internalServerError()
            //         .body(ApiResponse.failure(500, "Something went wrong during login"));
            return ApiResponse.<Users>failure(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong during login");
        }
    }
}
