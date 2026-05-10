package com.buyology.ecommerce.refund.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.refund.dto.RefundRequestResponse;
import com.buyology.ecommerce.refund.dto.SetReturnMethodRequest;
import com.buyology.ecommerce.refund.service.RefundRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/refunds")
public class CustomerRefundController {

    private final RefundRequestService refundService;

    public CustomerRefundController(RefundRequestService refundService) {
        this.refundService = refundService;
    }

    /**
     * Create a refund request for one of the caller's delivered orders.
     * Multipart form: orderId, description, images[] (≥3, ≤8, image/*, ≤5 MB each).
     */
    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> create(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("orderId") UUID orderId,
            @RequestParam("description") String description,
            @RequestParam("images") List<MultipartFile> images) {
        return ApiResponse.created(
                refundService.createRequest(userId, orderId, description, images),
                "Refund request submitted");
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<RefundRequestResponse>>> listMine(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                refundService.listForCustomer(userId, page, size),
                "Refund requests fetched");
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> get(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ApiResponse.success(
                refundService.getForCustomer(id, userId),
                "Refund request fetched");
    }

    @PostMapping("/{id}/return-method")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> setReturnMethod(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody SetReturnMethodRequest body) {
        return ApiResponse.success(
                refundService.setReturnMethod(id, userId, body),
                "Return method updated");
    }
}
