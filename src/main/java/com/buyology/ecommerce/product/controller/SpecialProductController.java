package com.buyology.ecommerce.product.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.dto.ProductResponse;
import com.buyology.ecommerce.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/product")
@Tag(name = "Special Products", description = "Public APIs for super deals and limited stock products")
public class SpecialProductController {

    private final ProductService productService;

    public SpecialProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Get all active super-deal products")
    @GetMapping("/super-deals")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getSuperDeals(
            @RequestParam String lang,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String currency) {
        return productService.getSuperDeals(lang, countryCode, currency);
    }

    @Operation(summary = "Get all active limited-stock products")
    @GetMapping("/limited-stock")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getLimitedStockProducts(
            @RequestParam String lang,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String currency) {
        return productService.getLimitedStockProducts(lang, countryCode, currency);
    }
}
