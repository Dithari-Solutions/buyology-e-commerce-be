package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.auth.dto.SignInRequest;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.auth.service.AuthService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.supplier.dto.SetPasswordRequest;
import com.buyology.ecommerce.supplier.service.SupplierAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/supplier/auth")
public class SupplierAuthController {

    private final SupplierAuthService supplierAuthService;
    private final AuthService authService;

    public SupplierAuthController(SupplierAuthService supplierAuthService, AuthService authService) {
        this.supplierAuthService = supplierAuthService;
        this.authService = authService;
    }

    /**
     * Supplier-only signin. Rejects accounts that don't have the SUPPLIER role.
     * Mirrors {@code /auth/admin/signin} but for the supplier portal.
     */
    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<SignInResponse>> signin(
            @Valid @RequestBody SignInRequest request,
            HttpServletRequest httpRequest) {
        return authService.supplierSignin(request, httpRequest);
    }

    @GetMapping("/validate-token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateToken(@RequestParam String token) {
        return supplierAuthService.validateToken(token);
    }

    @PostMapping("/set-password")
    public ResponseEntity<ApiResponse<SignInResponse>> setPassword(
            @Valid @RequestBody SetPasswordRequest request,
            HttpServletRequest httpRequest) {
        return supplierAuthService.setPassword(request, httpRequest);
    }
}
