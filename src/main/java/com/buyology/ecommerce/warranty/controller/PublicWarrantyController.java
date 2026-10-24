package com.buyology.ecommerce.warranty.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.warranty.dto.PublicWarrantyResponse;
import com.buyology.ecommerce.warranty.service.WarrantyCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The warranty checker behind https://v2.dithari.com/warranty. Public (no sign-in), read-only,
 * and allowed cross-origin from Dithari's sites without credentials — see SecurityConfig.
 */
@RestController
@RequestMapping("/api/warranty")
@Tag(name = "Warranty", description = "Public warranty card lookup by serial number")
public class PublicWarrantyController {

    private final WarrantyCardService warrantyCardService;

    public PublicWarrantyController(WarrantyCardService warrantyCardService) {
        this.warrantyCardService = warrantyCardService;
    }

    @Operation(summary = "Look up a warranty card by serial number",
            description = "Case, spaces and missing dashes are ignored, and O/I/L are read as 0/1. "
                    + "404 when no card matches.")
    @GetMapping("/{serial}")
    public ResponseEntity<ApiResponse<PublicWarrantyResponse>> lookup(@PathVariable String serial) {
        return ApiResponse.success(warrantyCardService.lookup(serial), "Warranty card found");
    }
}
