package com.buyology.ecommerce.warranty.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.warranty.dto.OrderItemWarranty;
import com.buyology.ecommerce.warranty.dto.VoidWarrantyRequest;
import com.buyology.ecommerce.warranty.service.WarrantyCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Withdrawing a card, from the admin order page. Refunds are per order and carry no item list, so
 * which unit came back is a person's call; nothing voids cards automatically.
 */
@RestController
@RequestMapping("/api/admin/warranty-cards")
@Tag(name = "Admin Warranty Cards", description = "Void or restore a warranty card")
public class AdminWarrantyCardController {

    private final WarrantyCardService warrantyCardService;

    public AdminWarrantyCardController(WarrantyCardService warrantyCardService) {
        this.warrantyCardService = warrantyCardService;
    }

    @Operation(summary = "Void a warranty card", description = "The checker then shows it as no longer valid.")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('order:warranty:update') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{serial}/void")
    public ResponseEntity<ApiResponse<OrderItemWarranty>> voidCard(
            @AuthenticationPrincipal UUID adminUserId,
            @PathVariable String serial,
            @RequestBody(required = false) VoidWarrantyRequest request) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.success(warrantyCardService.voidCard(serial, reason, adminUserId),
                "Warranty card voided");
    }

    @Operation(summary = "Restore a voided warranty card")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('order:warranty:update') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{serial}/restore")
    public ResponseEntity<ApiResponse<OrderItemWarranty>> restoreCard(
            @AuthenticationPrincipal UUID adminUserId,
            @PathVariable String serial) {
        return ApiResponse.success(warrantyCardService.restoreCard(serial, adminUserId),
                "Warranty card restored");
    }
}
