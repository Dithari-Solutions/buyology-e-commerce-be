package com.buyology.ecommerce.auth.controller;

import org.springframework.http.ResponseEntity;
import com.buyology.ecommerce.user.domain.Users;
import org.springframework.web.bind.annotation.*;
import com.buyology.ecommerce.auth.dto.SignUpRequest;
import com.buyology.ecommerce.auth.service.AuthService;
import com.buyology.ecommerce.auth.dto.GoogleOAuthRequest;
import com.buyology.ecommerce.auth.dto.SignInRequest;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.auth.service.GoogleOAuthService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(
            AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignInResponse>> signup(@RequestBody SignUpRequest request) {
        return authService.signup(request); // now returns ResponseEntity
    }

    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<SignInResponse>> signin(@RequestBody SignInRequest request) {
        return authService.signin(request); // now returns ResponseEntity
    }

}
