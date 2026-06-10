package com.buyology.ecommerce.refund.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.refund.dto.PublicRefundSettingResponse;
import com.buyology.ecommerce.refund.service.RefundSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (storefront) read of the refund/return policy. The product detail page uses
 * the window-days value to render "{n}-Day Returns". Admin reads/writes the full
 * settings via {@link AdminRefundController} under /api/admin/refund-settings.
 */
@RestController
@RequestMapping("/api/refund-settings")
@Tag(name = "Refund Settings (Public)", description = "Storefront-facing return/refund policy")
public class PublicRefundController {

    private final RefundSettingService settingService;

    public PublicRefundController(RefundSettingService settingService) {
        this.settingService = settingService;
    }

    @Operation(summary = "Get the public return/refund window (days) and whether returns are enabled")
    @GetMapping
    public ResponseEntity<ApiResponse<PublicRefundSettingResponse>> getPublicSettings() {
        return ApiResponse.success(settingService.getPublicResponse(), "Refund settings fetched");
    }
}
