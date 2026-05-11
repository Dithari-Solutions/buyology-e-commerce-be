package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.supplier.dto.SupplierRejectRequest;
import com.buyology.ecommerce.supplier.service.SupplierPortalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/supplier-products")
public class AdminSupplierProductController {

    private final SupplierPortalService supplierPortalService;

    public AdminSupplierProductController(SupplierPortalService supplierPortalService) {
        this.supplierPortalService = supplierPortalService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('supplier:product:approve')")
    public ResponseEntity<ApiResponse<Page<com.buyology.ecommerce.supplier.dto.SupplierProductSummary>>> listSupplierProducts(
            @RequestParam(required = false) Product.SupplierStatus supplierStatus,
            @RequestParam(required = false) UUID supplierId,
            @PageableDefault(size = 20) Pageable pageable) {
        return supplierPortalService.listSupplierProductsForAdmin(supplierStatus, supplierId, pageable);
    }

    @PostMapping("/{productId}/approve")
    @PreAuthorize("hasAuthority('supplier:product:approve')")
    public ResponseEntity<ApiResponse<String>> approveProduct(@PathVariable UUID productId) {
        return supplierPortalService.approveProduct(productId);
    }

    @PostMapping("/{productId}/reject")
    @PreAuthorize("hasAuthority('supplier:product:approve')")
    public ResponseEntity<ApiResponse<String>> rejectProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody SupplierRejectRequest request) {
        return supplierPortalService.rejectProduct(productId, request.getReason());
    }
}
