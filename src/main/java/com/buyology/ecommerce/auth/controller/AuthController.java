package com.buyology.ecommerce.auth.controller;

import org.springframework.http.ResponseEntity;
import com.buyology.ecommerce.user.domain.Users;
import org.springframework.web.bind.annotation.*;
import com.buyology.ecommerce.auth.service.AuthService;
import com.buyology.ecommerce.auth.dto.GoogleOAuthRequest;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.auth.service.GoogleOAuthService;

@RestController
@RequestMapping("/auth")
public class AuthController {
    
    private final AuthService authService;

    public AuthController (
        AuthService authService
    ) {
        this.authService = authService;
    }
}
