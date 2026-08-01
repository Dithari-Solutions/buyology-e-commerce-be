package com.buyology.ecommerce.sell.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.sell.domain.SellStatus;
import com.buyology.ecommerce.sell.dto.SellRequestResponse;
import com.buyology.ecommerce.sell.dto.SetSellOfferRequest;
import com.buyology.ecommerce.sell.dto.UpdateSellStatusRequest;
import com.buyology.ecommerce.sell.service.SellService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Procurement-facing sell (trade-in) endpoints. Secured to the SUPERADMIN + PROCUREMENT roles —
 * buy-back pricing is procurement's job, so these live alongside the B2B RFQ quotes rather than in
 * the repair team's queue.
 */
@RestController
@RequestMapping("/api/admin/sell-requests")
@PreAuthorize("hasAnyRole('SUPERADMIN','PROCUREMENT')")
public class AdminSellController {

    private final SellService sellService;

    public AdminSellController(SellService sellService) {
        this.sellService = sellService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SellRequestResponse>>> list(
            @RequestParam(required = false) SellStatus status) {
        return ApiResponse.success(sellService.listAll(status), "Sell requests fetched");
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> count() {
        return ApiResponse.success(Map.of("newCount", sellService.countUnread()), "New sell request count");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SellRequestResponse>> get(@PathVariable UUID id) {
        return ApiResponse.success(sellService.getByIdAdmin(id), "Sell request fetched");
    }

    /** Mark that the store received the device (→ UNDER_REVIEW). */
    @PostMapping("/{id}/received")
    public ResponseEntity<ApiResponse<SellRequestResponse>> markReceived(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id) {
        return ApiResponse.success(sellService.markDeviceReceived(id, adminId), "Device marked received");
    }

    /** Quote what Buyology will pay for the device (→ OFFER_MADE). */
    @PostMapping("/{id}/offer")
    public ResponseEntity<ApiResponse<SellRequestResponse>> setOffer(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id,
            @Valid @RequestBody SetSellOfferRequest req) {
        return ApiResponse.success(
                sellService.setOffer(id, req.getPrice(), req.getCurrency(), req.getValidFor(),
                        req.getInspectedCondition(), req.getNote(), adminId),
                "Offer sent");
    }

    /** Record that the store handed the payout over (ACCEPTED → COMPLETED). */
    @PostMapping("/{id}/paid")
    public ResponseEntity<ApiResponse<SellRequestResponse>> markPaidOut(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id) {
        return ApiResponse.success(sellService.markPaidOut(id, adminId), "Payout recorded");
    }

    /** Generic status transition (e.g. mark CANCELLED) with an optional note. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SellRequestResponse>> updateStatus(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id,
            @Valid @RequestBody UpdateSellStatusRequest req) {
        return ApiResponse.success(
                sellService.updateStatus(id, req.getStatus(), req.getNote(), adminId),
                "Sell request status updated");
    }
}
