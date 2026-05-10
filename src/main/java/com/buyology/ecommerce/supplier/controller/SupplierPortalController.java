package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.review.domain.ProductReview;
import com.buyology.ecommerce.review.domain.ProductReviewStats;
import com.buyology.ecommerce.supplier.service.SupplierLifecycleService;
import com.buyology.ecommerce.supplier.service.SupplierPortalService;
import com.buyology.ecommerce.supplier.service.SupplierProductImageService;
import com.buyology.ecommerce.supplier.service.SupplierReviewService;
import org.springframework.web.multipart.MultipartFile;
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
    private final SupplierProductImageService imageService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public SupplierPortalController(SupplierPortalService supplierPortalService,
                                    SupplierLifecycleService lifecycleService,
                                    SupplierReviewService reviewService,
                                    SupplierProductImageService imageService,
                                    com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.supplierPortalService = supplierPortalService;
        this.lifecycleService = lifecycleService;
        this.reviewService = reviewService;
        this.imageService = imageService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/stores")
    @PreAuthorize("hasAuthority('supplier:store:read')")
    public ResponseEntity<ApiResponse<List<com.buyology.ecommerce.supplier.dto.AssignedStoreResponse>>> getAssignedStores() {
        return supplierPortalService.getAssignedStores();
    }

    /**
     * Returns the currently authenticated supplier's own record (business name,
     * contact info, status). Used by the dashboard profile page to show
     * supplier-specific details when the logged-in user has the SUPPLIER role.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<com.buyology.ecommerce.supplier.domain.Supplier>> me() {
        return supplierPortalService.getCurrentSupplier();
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

    /**
     * Full supplier product creation — mirrors {@code POST /api/admin/product/create}
     * exactly (translations, specs, color options, variants, accessories, media)
     * but the result is hidden (status=INACTIVE, isActive=false, supplierStatus=
     * PENDING_REVIEW) until admin approves and the supplier publishes.
     */
    @PostMapping(value = "/products/full", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('supplier:product:create')")
    public ResponseEntity<ApiResponse<com.buyology.ecommerce.product.dto.ProductResponse>> submitFullProduct(
            @RequestPart("request") String requestJson,
            @RequestPart(value = "files", required = false) java.util.List<MultipartFile> files,
            @RequestPart("storeId") String storeId,
            @RequestPart("storePrice") String storePrice) throws Exception {
        com.buyology.ecommerce.product.dto.CreateProductRequest request =
                objectMapper.readValue(requestJson, com.buyology.ecommerce.product.dto.CreateProductRequest.class);
        return supplierPortalService.submitProductFull(
                request,
                files,
                java.util.UUID.fromString(storeId),
                new java.math.BigDecimal(storePrice));
    }

    // ── Product draft / publish / trash ──────────────────────────────────────

    /**
     * Upload product images (multipart). Validates: PNG or WebP, max 5 MB each,
     * max 8 images total, transparent background heuristic for PNG (rejects
     * non-bg-removed photos). Saves each to S3 and registers a ProductMedia row.
     */
    @PostMapping(value = "/products/{id}/images", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('supplier:product:update')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadProductImages(
            @PathVariable("id") UUID id,
            @RequestPart("files") List<MultipartFile> files) {
        return imageService.uploadImages(id, files);
    }

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
