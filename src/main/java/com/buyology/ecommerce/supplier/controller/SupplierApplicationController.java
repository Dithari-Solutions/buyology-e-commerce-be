package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.supplier.dto.*;
import com.buyology.ecommerce.supplier.service.SupplierApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supplier/apply")
public class SupplierApplicationController {

    private final SupplierApplicationService applicationService;

    public SupplierApplicationController(SupplierApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/step-1")
    public ResponseEntity<ApiResponse<UUID>> step1(@Valid @RequestBody SupplierStep1Request request) {
        return applicationService.initiateApplication(request);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<String>> verifyOtp(@Valid @RequestBody SupplierOtpRequest request) {
        return applicationService.verifyOtp(request);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<String>> resendOtp(@RequestParam UUID applicationId) {
        return applicationService.resendOtp(applicationId);
    }

    @PostMapping("/step-2")
    public ResponseEntity<ApiResponse<String>> step2(@Valid @RequestBody SupplierStep2Request request) {
        return applicationService.saveStep2(request);
    }

    @PostMapping(value = "/step-3", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<String>> step3(
            @RequestParam UUID applicationId,
            @RequestParam(required = false) List<String> sellsElsewhere,
            @RequestParam(required = false) String canProvideImages,
            @RequestParam(required = false) String avgDispatchTime,
            @RequestParam(required = false) String handlesReturns,
            @RequestParam(required = false) String hasTradeLicense,
            @RequestParam(required = false) String websiteOrSocialLink,
            @RequestPart(value = "tradeLicense", required = false) MultipartFile tradeLicense) {
        return applicationService.saveStep3(
                applicationId, sellsElsewhere, canProvideImages, avgDispatchTime,
                handlesReturns, hasTradeLicense, websiteOrSocialLink, tradeLicense);
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<String>> submit(@Valid @RequestBody SupplierSubmitRequest request) {
        return applicationService.submitApplication(request);
    }
}
