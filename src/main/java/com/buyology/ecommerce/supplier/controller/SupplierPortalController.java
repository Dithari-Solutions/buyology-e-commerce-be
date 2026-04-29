package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.supplier.service.SupplierPortalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/supplier")
public class SupplierPortalController {

    private final SupplierPortalService supplierPortalService;

    public SupplierPortalController(SupplierPortalService supplierPortalService) {
        this.supplierPortalService = supplierPortalService;
    }

    @GetMapping("/stores")
    @PreAuthorize("hasAuthority('supplier:store:read')")
    public ResponseEntity<ApiResponse<List<Store>>> getAssignedStores() {
        return supplierPortalService.getAssignedStores();
    }

    @GetMapping("/products")
    @PreAuthorize("hasAuthority('supplier:product:read')")
    public ResponseEntity<ApiResponse<Page<Product>>> getMyProducts(
            @RequestParam(required = false) Product.SupplierStatus supplierStatus,
            @PageableDefault(size = 20) Pageable pageable) {
        return supplierPortalService.getMyProducts(supplierStatus, pageable);
    }

    @PostMapping("/products")
    @PreAuthorize("hasAuthority('supplier:product:create')")
    public ResponseEntity<ApiResponse<UUID>> submitProduct(
            @RequestParam UUID categoryId,
            @RequestParam UUID storeId,
            @RequestParam String sku,
            @RequestParam BigDecimal storePrice,
            @RequestParam(required = false) String productJson) {
        return supplierPortalService.submitProduct(categoryId, storeId, sku, storePrice, productJson);
    }
}
