package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.supplier.domain.SupplierApplication.ApplicationStatus;
import com.buyology.ecommerce.supplier.dto.SupplierApplicationResponse;
import com.buyology.ecommerce.supplier.dto.SupplierApproveRequest;
import com.buyology.ecommerce.supplier.dto.SupplierRejectRequest;
import com.buyology.ecommerce.supplier.dto.SupplierResponse;
import com.buyology.ecommerce.supplier.dto.SupplierUpdateRequest;
import com.buyology.ecommerce.supplier.service.AdminSupplierService;
import com.buyology.ecommerce.supplier.service.SupplierLifecycleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/suppliers")
public class AdminSupplierController {

    private final AdminSupplierService adminSupplierService;
    private final SupplierLifecycleService lifecycleService;

    public AdminSupplierController(AdminSupplierService adminSupplierService,
                                   SupplierLifecycleService lifecycleService) {
        this.adminSupplierService = adminSupplierService;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('supplier:application:read')")
    public ResponseEntity<ApiResponse<Page<SupplierApplicationResponse>>> listApplications(
            @RequestParam(required = false) ApplicationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(adminSupplierService.listApplications(status, pageable), "Supplier applications");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier:application:read')")
    public ResponseEntity<ApiResponse<SupplierApplicationResponse>> getApplication(@PathVariable UUID id) {
        return adminSupplierService.getApplication(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('supplier:application:review')")
    public ResponseEntity<ApiResponse<UUID>> approveApplication(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierApproveRequest request) {
        return adminSupplierService.approveApplication(id, request.getStoreIds());
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("hasAuthority('supplier:application:read')")
    public ResponseEntity<ApiResponse<SupplierResponse>> getSupplier(@PathVariable UUID id) {
        return adminSupplierService.getSupplier(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier:application:review')")
    public ResponseEntity<ApiResponse<SupplierResponse>> updateSupplier(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierUpdateRequest request) {
        return adminSupplierService.updateSupplier(id, request);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('supplier:application:review')")
    public ResponseEntity<ApiResponse<String>> rejectApplication(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierRejectRequest request) {
        return adminSupplierService.rejectApplication(id, request.getReason());
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @PostMapping("/{id}/freeze")
    @PreAuthorize("hasAuthority('supplier:application:review')")
    public ResponseEntity<ApiResponse<String>> freezeSupplier(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID actorId) {
        return lifecycleService.adminFreeze(id, actorId != null ? "admin:" + actorId : "admin");
    }

    @PostMapping("/{id}/unfreeze")
    @PreAuthorize("hasAuthority('supplier:application:review')")
    public ResponseEntity<ApiResponse<String>> unfreezeSupplier(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID actorId) {
        return lifecycleService.adminUnfreeze(id, actorId != null ? "admin:" + actorId : "admin");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier:application:review')")
    public ResponseEntity<ApiResponse<String>> trashSupplier(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID actorId) {
        return lifecycleService.adminSoftDelete(id, actorId != null ? "admin:" + actorId : "admin");
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('supplier:application:review')")
    public ResponseEntity<ApiResponse<String>> restoreSupplier(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID actorId) {
        return lifecycleService.adminRestore(id, actorId != null ? "admin:" + actorId : "admin");
    }

    /**
     * Re-send the set-password email to an approved supplier who hasn't
     * completed setup yet. Errors with 409 if the supplier already has a
     * password.
     */
    @PostMapping("/{id}/resend-setup")
    @PreAuthorize("hasAuthority('supplier:application:review')")
    public ResponseEntity<ApiResponse<String>> resendSetup(@PathVariable UUID id) {
        return adminSupplierService.resendSetupEmail(id);
    }
}
