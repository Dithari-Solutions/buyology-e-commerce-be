package com.buyology.ecommerce.supplier.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.dto.SignInResponse;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.auth.service.AuthService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.utils.PasswordPolicy;
import com.buyology.ecommerce.common.utils.PasswordUtils;
import com.buyology.ecommerce.supplier.domain.Supplier;
import com.buyology.ecommerce.supplier.domain.SupplierSetupToken;
import com.buyology.ecommerce.supplier.dto.SetPasswordRequest;
import com.buyology.ecommerce.supplier.repository.SupplierRepository;
import com.buyology.ecommerce.supplier.repository.SupplierSetupTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class SupplierAuthService {

    private final SupplierSetupTokenRepository setupTokenRepository;
    private final SupplierRepository supplierRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final AuthService authService;

    public SupplierAuthService(
            SupplierSetupTokenRepository setupTokenRepository,
            SupplierRepository supplierRepository,
            AuthCredentialRepository authCredentialRepository,
            AuthService authService) {
        this.setupTokenRepository = setupTokenRepository;
        this.supplierRepository = supplierRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.authService = authService;
    }

    public ResponseEntity<ApiResponse<Map<String, Object>>> validateToken(String token) {
        return setupTokenRepository.findByTokenAndUsedFalse(token)
                .map(t -> {
                    if (t.isExpired()) {
                        return ApiResponse.<Map<String, Object>>failure(HttpStatus.GONE, "Setup link has expired");
                    }
                    Supplier supplier = supplierRepository.findById(t.getSupplierId()).orElse(null);
                    if (supplier == null) {
                        return ApiResponse.<Map<String, Object>>failure(HttpStatus.NOT_FOUND, "Supplier not found");
                    }
                    Map<String, Object> data = Map.of(
                            "valid", true,
                            "supplierEmail", supplier.getContactEmail(),
                            "businessName", supplier.getBusinessName());
                    return ApiResponse.success(data, "Token valid");
                })
                .orElseGet(() -> ApiResponse.failure(HttpStatus.NOT_FOUND, "Invalid or already used setup link"));
    }

    @Transactional
    public ResponseEntity<ApiResponse<SignInResponse>> setPassword(
            SetPasswordRequest request, HttpServletRequest httpRequest) {

        try {
            PasswordPolicy.validate(request.getPassword());
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (!request.getPassword().equals(request.getConfirmedPassword())) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }

        SupplierSetupToken setupToken = setupTokenRepository.findByTokenAndUsedFalse(request.getToken())
                .orElse(null);
        if (setupToken == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Invalid or already used setup link");
        }
        if (setupToken.isExpired()) {
            return ApiResponse.failure(HttpStatus.GONE, "Setup link has expired. Please contact support.");
        }

        Supplier supplier = supplierRepository.findById(setupToken.getSupplierId()).orElse(null);
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Supplier account not found");
        }

        AuthCredentials credentials = authCredentialRepository
                .findByEmailAndProvider(supplier.getContactEmail(), "LOCAL")
                .orElse(null);
        if (credentials == null) {
            return ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR, "Credentials not found. Please contact support.");
        }

        credentials.setPasswordHash(PasswordUtils.hashPassword(request.getPassword()));
        authCredentialRepository.save(credentials);

        setupToken.setUsed(true);
        setupTokenRepository.save(setupToken);

        return authService.buildSigninResponse(credentials, httpRequest);
    }
}
