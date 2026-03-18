package com.buyology.ecommerce.product.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.dto.ProductFilterRequest;
import com.buyology.ecommerce.product.dto.ProductResponse;
import com.buyology.ecommerce.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/product")
@Tag(name = "Product", description = "Public APIs for browsing active products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Get all active products")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts(
            @RequestParam String lang) {
        return productService.getAllProductsPublic(lang);
    }

    @Operation(summary = "Get active product by ID with all related details")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @PathVariable UUID productId,
            @RequestParam String lang) {
        return productService.getProductByIdPublic(productId, lang);
    }

    @Operation(summary = "Get active products by category")
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProductsByCategory(
            @PathVariable UUID categoryId,
            @RequestParam String lang) {
        return productService.getProductsByCategoryPublic(categoryId, lang);
    }

    @Operation(summary = "Search and filter active products",
            description = "All filter params are optional. Spec filters (ram, storage, processor, " +
                    "screenSize, touchableScreen, operatingSystem, keyboardLanguage) match against " +
                    "product spec groups using codes: ram, storage, processor, screen_size, " +
                    "touchable_screen, operating_system, keyboard_language")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> searchProducts(
            @ModelAttribute ProductFilterRequest filter,
            @RequestParam String lang) {
        return productService.searchProducts(filter, lang);
    }
}
