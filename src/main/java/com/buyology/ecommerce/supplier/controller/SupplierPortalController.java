package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.review.domain.ProductReview;
import com.buyology.ecommerce.review.domain.ProductReviewStats;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.supplier.service.SupplierLifecycleService;
import com.buyology.ecommerce.supplier.service.SupplierPortalService;
import com.buyology.ecommerce.supplier.service.SupplierReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/supplier")
public class SupplierPortalController {

    private final SupplierPortalService supplierPortalService;
    private final SupplierLifecycleService lifecycleService;
    private final SupplierReviewService reviewService;

    public SupplierPortalController(SupplierPortalService supplierPortalService,
                                    SupplierLifecycleService lifecycleService,
                                    SupplierReviewService reviewService) {
        this.supplierPortalService = supplierPortalService;
        this.lifecycleService = lifecycleService;
        this.reviewService = reviewService;
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

    // ── Product draft / publish / trash ──────────────────────────────────────

    @PatchMapping("/products/{id}/publish")
    @PreAuthorize("hasAuthority('supplier:product:update')")
    public ResponseEntity<ApiResponse<String>> publishProduct(@PathVariable UUID id) {
        return supplierPortalService.publishProduct(id);
    }

    @PatchMapping("/products/{id}/draft")
    @PreAuthorize("hasAuthority('supplier:product:update')")
    public ResponseEntity<ApiResponse<String>> draftProduct(@PathVariable UUID id) {
        return supplierPortalService.draftProduct(id);
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAuthority('supplier:product:update')")
    public ResponseEntity<ApiResponse<Void>> softDeleteProduct(@PathVariable UUID id) {
        return supplierPortalService.softDeleteOwnProduct(id);
    }

    @GetMapping("/products/trash")
    @PreAuthorize("hasAuthority('supplier:product:read')")
    public ResponseEntity<ApiResponse<Page<Product>>> listTrash(
            @PageableDefault(size = 20) Pageable pageable) {
        return supplierPortalService.listOwnTrash(pageable);
    }

    @PostMapping("/products/{id}/restore")
    @PreAuthorize("hasAuthority('supplier:product:update')")
    public ResponseEntity<ApiResponse<String>> restoreProduct(@PathVariable UUID id) {
        return supplierPortalService.restoreOwnProduct(id);
    }

    // ── Reviews ─────────────────────────────────────────────────────────────

    @GetMapping("/reviews")
    @PreAuthorize("hasAuthority('supplier:product:read')")
    public ResponseEntity<ApiResponse<Page<ProductReview>>> listReviews(
            @RequestParam(required = false) UUID productId,
            @PageableDefault(size = 20) Pageable pageable) {
        return reviewService.listReviewsForSupplier(productId, pageable);
    }

    @GetMapping("/reviews/summary")
    @PreAuthorize("hasAuthority('supplier:product:read')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reviewsSummary() {
        return reviewService.getSupplierSummary();
    }

    @GetMapping("/products/{id}/reviews/summary")
    @PreAuthorize("hasAuthority('supplier:product:read')")
    public ResponseEntity<ApiResponse<ProductReviewStats>> productReviewsSummary(@PathVariable UUID id) {
        return reviewService.getProductSummary(id);
    }

    // ── Account lifecycle (self) ─────────────────────────────────────────────

    @PostMapping("/account/freeze")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> freezeMyAccount() {
        return lifecycleService.selfFreeze();
    }

    @PostMapping("/account/unfreeze")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> unfreezeMyAccount() {
        return lifecycleService.selfUnfreeze();
    }

    @DeleteMapping("/account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> deleteMyAccount() {
        return lifecycleService.selfSoftDelete();
    }
}
