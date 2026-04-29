package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.supplier.domain.SupplierApplication.ApplicationStatus;
import com.buyology.ecommerce.supplier.dto.SupplierApplicationResponse;
import com.buyology.ecommerce.supplier.dto.SupplierApproveRequest;
import com.buyology.ecommerce.supplier.dto.SupplierRejectRequest;
import com.buyology.ecommerce.supplier.service.AdminSupplierService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/suppliers")
public class AdminSupplierController {

    private final AdminSupplierService adminSupplierService;

    public AdminSupplierController(AdminSupplierService adminSupplierService) {
        this.adminSupplierService = adminSupplierService;
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

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('supplier:application:review')")
    public ResponseEntity<ApiResponse<String>> rejectApplication(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierRejectRequest request) {
        return adminSupplierService.rejectApplication(id, request.getReason());
    }
}
