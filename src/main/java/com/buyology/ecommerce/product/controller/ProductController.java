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
            @RequestParam(required = false) String countryCode,
            @Parameter(description = "ISO 4217 display currency to express the price range in")
            @RequestParam(required = false) String currency,
            @Parameter(description = "User latitude — resolves express-store pricing so the range matches the cards")
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return productFilterService.getAvailableFilters(countryCode, lang, currency, lat, lng);
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
            @RequestParam(required = false) Double lng,
            @Parameter(description = "Page index (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max products per response)")
            @RequestParam(defaultValue = "60") int size,
            @Parameter(description = "Sort: POPULAR | NEWEST | PRICE_ASC | PRICE_DESC")
            @RequestParam(required = false) String sort) {
        return productService.getAllProductsPublic(lang, countryCode, currency, lat, lng, page, size, sort);
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

    @Operation(summary = "Get a product by slug (resolved across languages) in the requested language")
    @GetMapping("/by-slug")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductBySlug(
            @RequestParam String slug,
            @RequestParam String lang,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return productService.getProductBySlugPublic(slug, lang, countryCode, currency, lat, lng);
    }

    @Operation(summary = "Get related products (same category, popular)")
    @GetMapping("/{productId}/related")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getRelatedProducts(
            @PathVariable UUID productId,
            @RequestParam String lang,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return productService.getRelatedProducts(productId, lang, countryCode, currency, lat, lng);
    }

    @Operation(summary = "Popular for you — products similar to the supplied cart item IDs",
            description = "Aggregates related products across the categories of the supplied productIds " +
                    "(typically the user's current cart) and returns up to 8 popular suggestions, " +
                    "excluding the products already in the list.")
    @GetMapping("/popular-for-you")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getPopularForYou(
            @RequestParam(required = false) List<UUID> productIds,
            @RequestParam String lang,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return productService.getPopularForYou(productIds, lang, countryCode, currency, lat, lng);
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

    @Operation(summary = "Search products using Elasticsearch",
            description = "Performs a full-text search across product titles, descriptions, categories, and brands.")
    @GetMapping("/search-elastic")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> searchProductsElastic(
            @RequestParam String query,
            @RequestParam String lang,
            @Parameter(description = "ISO 3166-1 alpha-3 country code (e.g. UAE, AZE)")
            @RequestParam(required = false) String countryCode,
            @Parameter(description = "ISO 4217 display currency (e.g. AZN, AED). Defaults to country's currency.")
            @RequestParam(required = false) String currency,
            @Parameter(description = "Customer latitude for express delivery badge")
            @RequestParam(required = false) Double lat,
            @Parameter(description = "Customer longitude for express delivery badge")
            @RequestParam(required = false) Double lng) {
        return productService.searchProductsElastic(query, lang, countryCode, currency, lat, lng);
    }

    @Operation(summary = "Search and filter products",
            description = "Binds multiple optional filters (brand, category, condition, etc.) via query parameters.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> searchProducts(
            @ModelAttribute ProductFilterRequest filter,
            @RequestParam String lang,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return productService.searchProducts(filter, lang, countryCode, currency, lat, lng);
    }

    // ======================================================================
    // B2B browse (PUBLIC). Same params as the consumer endpoints, but the
    // catalog is sourced from B2B-enabled store products in B2B-enabled
    // countries and every product is returned quoteOnly=true (no buyable
    // price) so the storefront shows "Request a Quote".
    // ======================================================================

    @Operation(summary = "Get B2B products (public)",
            description = "Bulk/quote catalog: products offered for B2B in B2B-enabled countries. " +
                    "No buyable price is exposed — each product is marked quoteOnly=true.")
    @GetMapping("/b2b")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getB2bProducts(
            @RequestParam String lang,
            @Parameter(description = "ISO 3166-1 alpha-3 country code (e.g. UAE, AZE)")
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "60") int size,
            @Parameter(description = "Sort: NEWEST (price sorts are ignored on the B2B channel)")
            @RequestParam(required = false) String sort) {
        return productService.getAllB2bProductsPublic(lang, countryCode, currency, lat, lng, page, size, sort);
    }

    @Operation(summary = "Search B2B products (public)",
            description = "Same filters as /search, scoped to the B2B catalog. Products are quoteOnly=true.")
    @GetMapping("/b2b/search")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> searchB2bProducts(
            @ModelAttribute ProductFilterRequest filter,
            @RequestParam String lang,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return productService.searchB2bProducts(filter, lang, countryCode, currency, lat, lng);
    }

    @Operation(summary = "Get B2B filter options (public)",
            description = "Filter options for the B2B catalog. The price range is suppressed (0..0) " +
                    "because the B2B channel has no buyable price.")
    @GetMapping("/b2b/filters")
    public ResponseEntity<ApiResponse<ProductFiltersResponse>> getB2bFilters(
            @RequestParam String lang,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return productFilterService.getAvailableFilters(
                countryCode, lang, currency, lat, lng, ProductService.Channel.B2B);
    }

    @Operation(summary = "Get a B2B product by slug (public)",
            description = "B2B product detail resolved by slug. quoteOnly=true; 404 if not B2B-available.")
    @GetMapping("/b2b/by-slug")
    public ResponseEntity<ApiResponse<ProductResponse>> getB2bProductBySlug(
            @RequestParam String slug,
            @RequestParam String lang,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return productService.getProductBySlugB2bPublic(slug, lang, countryCode, currency, lat, lng);
    }
}
