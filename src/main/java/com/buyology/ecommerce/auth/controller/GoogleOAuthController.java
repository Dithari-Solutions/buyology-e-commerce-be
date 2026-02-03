package com.buyology.ecommerce.auth.controller;

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
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure(400, "Authorization code is required"));
        }

        try {
            Users user = googleOAuthService.processGoogleOAuth(code);
            return ResponseEntity.ok(ApiResponse.success(user, "Logged in successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure(400, e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.failure(500, "Something went wrong during login"));
        }
    }
}
