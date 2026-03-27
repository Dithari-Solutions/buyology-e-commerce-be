package com.buyology.ecommerce.product.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.dto.ProductResponse;
import com.buyology.ecommerce.product.service.QuickDeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/product")
@Validated
@Tag(name = "Product", description = "Public APIs for browsing active products")
public class QuickDeliveryController {

    private final QuickDeliveryService quickDeliveryService;

    public QuickDeliveryController(QuickDeliveryService quickDeliveryService) {
        this.quickDeliveryService = quickDeliveryService;
    }

    @Operation(
            summary = "Get products available for ≤30-minute delivery",
            description = "Accepts the user's current GPS coordinates and returns all active products " +
                    "available in store locations within the 30-minute delivery radius (~12.5 km). " +
                    "The frontend should obtain the user's location via the browser Geolocation API " +
                    "and pass it as query parameters."
    )
    @GetMapping("/quick-delivery")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getQuickDeliveryProducts(
            @Parameter(description = "User latitude (-90 to 90)", required = true)
            @RequestParam @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,

            @Parameter(description = "User longitude (-180 to 180)", required = true)
            @RequestParam @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,

            @Parameter(description = "Response language code (e.g. en, az, ar)", required = true)
            @RequestParam String lang,

            @Parameter(description = "ISO 3166-1 alpha-3 country code to scope store pricing (e.g. UAE, AZE)")
            @RequestParam(required = false) String countryCode,

            @Parameter(description = "ISO 4217 currency for price display (e.g. AZN, AED). Defaults to the country's native currency.")
            @RequestParam(required = false) String currency) {

        return quickDeliveryService.getQuickDeliveryProducts(lat, lng, lang, countryCode, currency);
    }
}
