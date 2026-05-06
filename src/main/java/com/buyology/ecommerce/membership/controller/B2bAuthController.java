package com.buyology.ecommerce.membership.controller;

import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.membership.dto.B2bSetPasswordRequest;
import com.buyology.ecommerce.membership.service.B2bAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/membership/auth")
public class B2bAuthController {

    private final B2bAuthService authService;

    public B2bAuthController(B2bAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/validate-token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateToken(@RequestParam String token) {
        return authService.validateToken(token);
    }

    @PostMapping("/set-password")
    public ResponseEntity<ApiResponse<SignInResponse>> setPassword(
            @Valid @RequestBody B2bSetPasswordRequest request,
            HttpServletRequest httpRequest) {
        return authService.setPassword(request, httpRequest);
    }
}
