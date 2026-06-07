package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.supplier.domain.SupplierProductChangeRequest.Status;
import com.buyology.ecommerce.supplier.dto.SupplierProductChangeResponse;
import com.buyology.ecommerce.supplier.service.SupplierProductChangeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Superadmin review of supplier-initiated product change requests
 * (edit / delete / restore). Approving applies the change; rejecting records a reason.
 */
@RestController
@RequestMapping("/api/admin/supplier-product-changes")
@PreAuthorize("hasRole('SUPERADMIN')")
public class AdminSupplierProductChangeController {

    private final SupplierProductChangeService changeService;

    public AdminSupplierProductChangeController(SupplierProductChangeService changeService) {
        this.changeService = changeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SupplierProductChangeResponse>>> list(
            @RequestParam(required = false) Status status,
            @PageableDefault(size = 20) Pageable pageable) {
        return changeService.listForAdmin(status, pageable);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<SupplierProductChangeResponse>> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID adminId) {
        return changeService.approve(id, adminId);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<SupplierProductChangeResponse>> reject(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID adminId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return changeService.reject(id, adminId, reason);
    }
}
