package com.buyology.ecommerce.product.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.dto.BrandResponse;
import com.buyology.ecommerce.product.dto.CreateBrandRequest;
import com.buyology.ecommerce.product.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Brand", description = "Brand management APIs")
public class BrandController {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @Operation(summary = "Get all active brands")
    @GetMapping("/api/brand")
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getAllBrands() {
        return brandService.getAllBrands();
    }

    @Operation(summary = "Create a new brand (admin)")
    @PostMapping("/api/admin/brand")
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(
            @Valid @RequestBody CreateBrandRequest request) {
        return brandService.createBrand(request);
    }

    @Operation(summary = "Deactivate a brand (admin)")
    @DeleteMapping("/api/admin/brand/{brandId}")
    public ResponseEntity<ApiResponse<Void>> deleteBrand(@PathVariable UUID brandId) {
        return brandService.deleteBrand(brandId);
    }
}
