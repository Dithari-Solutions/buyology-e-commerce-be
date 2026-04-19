package com.buyology.ecommerce.product.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.dto.ProductFilterRequest;
import com.buyology.ecommerce.product.dto.ProductFiltersResponse;
import com.buyology.ecommerce.product.dto.ProductResponse;
import com.buyology.ecommerce.product.service.ProductFilterService;
import com.buyology.ecommerce.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    private final ProductFilterService productFilterService;

    public ProductController(ProductService productService, ProductFilterService productFilterService) {
        this.productService = productService;
        this.productFilterService = productFilterService;
    }

    @Operation(summary = "Get available filter options",
            description = "Returns all filter options (price range, conditions, categories, brands, " +
                    "availability statuses, and dynamic spec filters) derived from the active product catalog. " +
                    "When countryCode is provided, price range is scoped to that country's stores.")
    @GetMapping("/filters")
    public ResponseEntity<ApiResponse<ProductFiltersResponse>> getFilters(
            @RequestParam String lang,
            @Parameter(description = "ISO 3166-1 alpha-3 country code to scope price range (e.g. UAE, AZE)")
            @RequestParam(required = false) String countryCode) {
        return productFilterService.getAvailableFilters(countryCode, lang);
    }

    @Operation(summary = "Get all active products",
            description = "When countryCode is supplied, only products available in that country's stores " +
                    "are returned and each product includes storePrice + currency. " +
                    "currency defaults to the country's native currency if omitted.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts(
            @RequestParam String lang,
            @Parameter(description = "ISO 3166-1 alpha-3 country code (e.g. UAE, AZE)")
            @RequestParam(required = false) String countryCode,
            @Parameter(description = "ISO 4217 display currency (e.g. AZN, AED). Defaults to country's currency.")
            @RequestParam(required = false) String currency,
            @Parameter(description = "Customer latitude for express delivery badge")
            @RequestParam(required = false) Double lat,
            @Parameter(description = "Customer longitude for express delivery badge")
            @RequestParam(required = false) Double lng) {
        return productService.getAllProductsPublic(lang, countryCode, currency, lat, lng);
    }

    @Operation(summary = "Get active product by ID with all related details")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(
            @PathVariable UUID productId,
            @RequestParam String lang,
            @Parameter(description = "ISO 3166-1 alpha-3 country code (e.g. UAE, AZE)")
            @RequestParam(required = false) String countryCode,
            @Parameter(description = "ISO 4217 display currency (e.g. AZN, AED). Defaults to country's currency.")
            @RequestParam(required = false) String currency,
            @Parameter(description = "Customer latitude for express delivery badge")
            @RequestParam(required = false) Double lat,
            @Parameter(description = "Customer longitude for express delivery badge")
            @RequestParam(required = false) Double lng) {
        return productService.getProductByIdPublic(productId, lang, countryCode, currency, lat, lng);
    }

    @Operation(summary = "Get active products by category")
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProductsByCategory(
            @PathVariable UUID categoryId,
            @RequestParam String lang,
            @Parameter(description = "ISO 3166-1 alpha-3 country code (e.g. UAE, AZE)")
            @RequestParam(required = false) String countryCode,
            @Parameter(description = "ISO 4217 display currency (e.g. AZN, AED). Defaults to country's currency.")
            @RequestParam(required = false) String currency,
            @Parameter(description = "Customer latitude for express delivery badge")
            @RequestParam(required = false) Double lat,
            @Parameter(description = "Customer longitude for express delivery badge")
            @RequestParam(required = false) Double lng) {
        return productService.getProductsByCategoryPublic(categoryId, lang, countryCode, currency, lat, lng);
    }

    @Operation(summary = "Search and filter active products",
            description = "All filter params are optional. Spec filters (ram, storage, processor, " +
                    "screenSize, touchableScreen, operatingSystem, keyboardLanguage) match against " +
                    "product spec groups using codes: ram, storage, processor, screen_size, " +
                    "touchable_screen, operating_system, keyboard_language. " +
                    "When countryCode is provided, results are scoped to stores in that country.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> searchProducts(
            @ModelAttribute ProductFilterRequest filter,
            @RequestParam String lang,
            @Parameter(description = "ISO 3166-1 alpha-3 country code (e.g. UAE, AZE)")
            @RequestParam(required = false) String countryCode,
            @Parameter(description = "ISO 4217 display currency (e.g. AZN, AED). Defaults to country's currency.")
            @RequestParam(required = false) String currency,
            @Parameter(description = "Customer latitude for express delivery badge")
            @RequestParam(required = false) Double lat,
            @Parameter(description = "Customer longitude for express delivery badge")
            @RequestParam(required = false) Double lng) {
        return productService.searchProducts(filter, lang, countryCode, currency, lat, lng);
    }
}
