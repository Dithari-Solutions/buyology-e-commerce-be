package com.buyology.ecommerce.store.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.store.dto.AssignProductRequest;
import com.buyology.ecommerce.store.dto.AssignVariantRequest;
import com.buyology.ecommerce.store.dto.StoreProductResponse;
import com.buyology.ecommerce.store.dto.UpdateStoreProductRequest;
import com.buyology.ecommerce.store.dto.UpdateStoreVariantRequest;
import com.buyology.ecommerce.store.service.StoreProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stores/{storeId}/products")
@PreAuthorize("hasRole('SUPERADMIN') or hasRole('STORE_ADMIN') or hasRole('ADMIN')")
@Tag(name = "Store Products", description = "Assign global products to a store with local pricing and stock")
public class StoreProductController {

    private final StoreProductService storeProductService;

    public StoreProductController(StoreProductService storeProductService) {
        this.storeProductService = storeProductService;
    }

    @Operation(summary = "Assign a product to a store",
            description = "Links a global product to a store with a store-specific price and optional discount. Variants can be assigned inline or added separately.")
    @PostMapping
    public ResponseEntity<ApiResponse<StoreProductResponse>> assignProduct(
            @PathVariable UUID storeId,
            @RequestBody @Valid AssignProductRequest request) {
        return storeProductService.assignProduct(storeId, request);
    }

    @Operation(summary = "List all products assigned to a store")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreProductResponse>>> getStoreProducts(
            @PathVariable UUID storeId) {
        return storeProductService.getStoreProducts(storeId);
    }

    @Operation(summary = "Update a store product's price, discount, or active status")
    @PatchMapping("/{storeProductId}")
    public ResponseEntity<ApiResponse<StoreProductResponse>> updateStoreProduct(
            @PathVariable UUID storeId,
            @PathVariable UUID storeProductId,
            @RequestBody @Valid UpdateStoreProductRequest request) {
        return storeProductService.updateStoreProduct(storeId, storeProductId, request);
    }

    @Operation(summary = "Remove a product from a store",
            description = "Soft-removes the store product assignment. The global product is unaffected.")
    @DeleteMapping("/{storeProductId}")
    public ResponseEntity<ApiResponse<Void>> removeProduct(
            @PathVariable UUID storeId,
            @PathVariable UUID storeProductId) {
        return storeProductService.removeProduct(storeId, storeProductId);
    }

    // ── Variant sub-resource ──────────────────────────────────────────────────

    @Operation(summary = "Assign a variant to a store product",
            description = "Sets the store-specific price and stock for a product variant. The variant must belong to the assigned product.")
    @PostMapping("/{storeProductId}/variants")
    public ResponseEntity<ApiResponse<StoreProductResponse.StoreVariantResponse>> assignVariant(
            @PathVariable UUID storeId,
            @PathVariable UUID storeProductId,
            @RequestBody @Valid AssignVariantRequest request) {
        return storeProductService.assignVariant(storeId, storeProductId, request);
    }

    @Operation(summary = "Update a store variant's price, stock, or active status")
    @PatchMapping("/{storeProductId}/variants/{storeVariantId}")
    public ResponseEntity<ApiResponse<StoreProductResponse.StoreVariantResponse>> updateStoreVariant(
            @PathVariable UUID storeId,
            @PathVariable UUID storeProductId,
            @PathVariable UUID storeVariantId,
            @RequestBody @Valid UpdateStoreVariantRequest request) {
        return storeProductService.updateStoreVariant(storeId, storeProductId, storeVariantId, request);
    }

    @Operation(summary = "Remove a variant from a store product")
    @DeleteMapping("/{storeProductId}/variants/{storeVariantId}")
    public ResponseEntity<ApiResponse<Void>> removeVariant(
            @PathVariable UUID storeId,
            @PathVariable UUID storeProductId,
            @PathVariable UUID storeVariantId) {
        return storeProductService.removeVariant(storeId, storeProductId, storeVariantId);
    }
}
