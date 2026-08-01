package com.buyology.ecommerce.b2b.productrequest.controller;

import com.buyology.ecommerce.b2b.productrequest.domain.B2bProductRequestStatus;
import com.buyology.ecommerce.b2b.productrequest.dto.B2bProductRequestResponse;
import com.buyology.ecommerce.b2b.productrequest.dto.SendProductRequestUpdateRequest;
import com.buyology.ecommerce.b2b.productrequest.dto.UpdateProductRequestStatusRequest;
import com.buyology.ecommerce.b2b.productrequest.service.B2bProductRequestService;
import com.buyology.ecommerce.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Procurement-facing B2B product-sourcing request endpoints. Secured to the SUPERADMIN +
 * PROCUREMENT roles (mirrors {@link com.buyology.ecommerce.b2b.quote.controller.AdminB2bQuoteController}).
 */
@RestController
@RequestMapping("/api/admin/b2b/product-requests")
public class AdminB2bProductRequestController {

    private final B2bProductRequestService requestService;

    public AdminB2bProductRequestController(B2bProductRequestService requestService) {
        this.requestService = requestService;
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('b2b:product-request:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<B2bProductRequestResponse>>> list(
            @RequestParam(required = false) B2bProductRequestStatus status) {
        return ApiResponse.success(requestService.listAll(status), "Product requests fetched");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('b2b:product-request:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> count() {
        return ApiResponse.success(Map.of("newCount", requestService.countNew()), "New request count");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('b2b:product-request:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<B2bProductRequestResponse>> get(@PathVariable UUID id) {
        return ApiResponse.success(requestService.getById(id), "Product request fetched");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('b2b:product-request:moderate') or @rbacPolicy.legacyAdmin()")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<B2bProductRequestResponse>> updateStatus(
            @AuthenticationPrincipal UUID adminId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequestStatusRequest req) {
        return ApiResponse.success(
                requestService.updateStatus(id, req.getStatus(), req.getNote(), adminId),
                "Product request status updated");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('b2b:product-request:update') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{id}/update")
    public ResponseEntity<ApiResponse<B2bProductRequestResponse>> sendUpdate(
            @AuthenticationPrincipal UUID adminId,
            @PathVariable UUID id,
            @Valid @RequestBody SendProductRequestUpdateRequest req) {
        return ApiResponse.success(
                requestService.sendUpdate(id, req.getMessage(), adminId),
                "Update sent to member");
    }
}
