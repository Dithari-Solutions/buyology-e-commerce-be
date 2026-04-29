package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.auth.dto.SignInResponse;
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

    public SupplierAuthController(SupplierAuthService supplierAuthService) {
        this.supplierAuthService = supplierAuthService;
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
