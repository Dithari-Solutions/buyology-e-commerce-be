package com.buyology.ecommerce.payout.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.payout.dto.AdminPayoutActionRequest;
import com.buyology.ecommerce.payout.dto.PayoutRequestResponse;
import com.buyology.ecommerce.payout.dto.SupplierPayoutAccountResponse;
import com.buyology.ecommerce.payout.enums.PayoutRequestStatus;
import com.buyology.ecommerce.payout.service.PayoutRequestService;
import com.buyology.ecommerce.payout.service.SupplierPayoutAccountService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/payouts")
public class AdminPayoutController {

    private final PayoutRequestService requestService;
    private final SupplierPayoutAccountService accountService;

    public AdminPayoutController(PayoutRequestService requestService,
                                 SupplierPayoutAccountService accountService) {
        this.requestService = requestService;
        this.accountService = accountService;
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<PayoutRequestResponse>>> list(
            @RequestParam(required = false) PayoutRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(requestService.listForAdmin(status, page, size), "Payout requests fetched");
    }

    @GetMapping("/requests/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PayoutRequestResponse>> get(@PathVariable UUID id) {
        return ApiResponse.success(requestService.getForAdmin(id), "Payout request fetched");
    }

    @PostMapping("/requests/{id}/mark-paid")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PayoutRequestResponse>> markPaid(
            @AuthenticationPrincipal UUID adminId,
            @PathVariable UUID id,
            @RequestBody(required = false) AdminPayoutActionRequest body) {
        String note = body == null ? null : body.note();
        return ApiResponse.success(requestService.markPaid(id, adminId, note), "Payout request marked paid");
    }

    @PostMapping("/requests/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PayoutRequestResponse>> reject(
            @AuthenticationPrincipal UUID adminId,
            @PathVariable UUID id,
            @RequestBody AdminPayoutActionRequest body) {
        String note = body == null ? null : body.note();
        return ApiResponse.success(requestService.reject(id, adminId, note), "Payout request rejected");
    }

    @GetMapping("/suppliers/{supplierId}/account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SupplierPayoutAccountResponse>> supplierAccount(
            @PathVariable UUID supplierId) {
        return accountService.getForSupplier(supplierId)
                .map(a -> ApiResponse.success(a, "Supplier payout account fetched"))
                .orElseGet(() -> ApiResponse.failure(HttpStatus.NOT_FOUND, "No payout account on file"));
    }
}
