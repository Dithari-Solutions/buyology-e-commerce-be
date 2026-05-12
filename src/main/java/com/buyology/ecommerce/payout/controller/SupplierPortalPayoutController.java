package com.buyology.ecommerce.payout.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.payout.dto.PayoutEligibilityResponse;
import com.buyology.ecommerce.payout.dto.PayoutRequestResponse;
import com.buyology.ecommerce.payout.dto.SupplierPayoutAccountRequest;
import com.buyology.ecommerce.payout.dto.SupplierPayoutAccountResponse;
import com.buyology.ecommerce.payout.service.PayoutEligibilityService;
import com.buyology.ecommerce.payout.service.PayoutRequestService;
import com.buyology.ecommerce.payout.service.SupplierPayoutAccountService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/supplier/payouts")
public class SupplierPortalPayoutController {

    private final SupplierPayoutAccountService accountService;
    private final PayoutRequestService requestService;
    private final PayoutEligibilityService eligibilityService;

    public SupplierPortalPayoutController(SupplierPayoutAccountService accountService,
                                          PayoutRequestService requestService,
                                          PayoutEligibilityService eligibilityService) {
        this.accountService = accountService;
        this.requestService = requestService;
        this.eligibilityService = eligibilityService;
    }

    @GetMapping("/account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SupplierPayoutAccountResponse>> getAccount() {
        return ApiResponse.success(
                accountService.getForCurrentSupplier().orElse(null),
                "Payout account fetched");
    }

    @PutMapping("/account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SupplierPayoutAccountResponse>> upsertAccount(
            @Valid @RequestBody SupplierPayoutAccountRequest body) {
        return ApiResponse.success(accountService.upsertForCurrentSupplier(body), "Payout account saved");
    }

    @GetMapping("/eligibility")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PayoutEligibilityResponse>> eligibility() {
        var supplier = accountService.requireCurrentSupplier();
        return ApiResponse.success(eligibilityService.compute(supplier.getId()), "Payout eligibility");
    }

    @PostMapping("/requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PayoutRequestResponse>> submit() {
        return ApiResponse.created(requestService.submitForCurrentSupplier(), "Payout request submitted");
    }

    @GetMapping("/requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<PayoutRequestResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(requestService.listForCurrentSupplier(page, size), "Payout history");
    }
}
