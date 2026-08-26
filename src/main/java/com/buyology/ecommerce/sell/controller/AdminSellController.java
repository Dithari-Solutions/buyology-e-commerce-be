package com.buyology.ecommerce.sell.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.sell.domain.SellStatus;
import com.buyology.ecommerce.sell.dto.SellRequestResponse;
import com.buyology.ecommerce.sell.dto.SetSellOfferRequest;
import com.buyology.ecommerce.sell.dto.UpdateSellStatusRequest;
import com.buyology.ecommerce.sell.service.SellAiEstimateService;
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
 * Procurement-facing sell (trade-in) endpoints, guarded by the {@code sell:read} / {@code sell:update}
 * permission codes — buy-back pricing is procurement's job, so these live alongside the B2B RFQ quotes
 * rather than in the repair team's queue, and the seeded PROCUREMENT role carries both codes. The
 * {@code @rbacPolicy.legacyAdmin()} clause keeps existing blanket admins working until
 * {@code rbac.strict-permissions} is turned on, matching every other admin controller.
 */
@RestController
@RequestMapping("/api/admin/sell-requests")
public class AdminSellController {

    private final SellService sellService;
    private final SellAiEstimateService aiEstimateService;

    public AdminSellController(SellService sellService, SellAiEstimateService aiEstimateService) {
        this.sellService = sellService;
        this.aiEstimateService = aiEstimateService;
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('sell:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SellRequestResponse>>> list(
            @RequestParam(required = false) SellStatus status) {
        return ApiResponse.success(sellService.listAll(status), "Sell requests fetched");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('sell:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> count() {
        return ApiResponse.success(Map.of("newCount", sellService.countUnread()), "New sell request count");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('sell:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SellRequestResponse>> get(@PathVariable UUID id) {
        return ApiResponse.success(sellService.getByIdAdmin(id), "Sell request fetched");
    }

    /**
     * Runs the buy-back valuation for a request that has none.
     *
     * <p>The valuation otherwise happens exactly once, on the submit event, and there was no second
     * chance at it: a request submitted while the feature was off — an unset ANTHROPIC_API_KEY
     * leaves it inert — kept an empty estimate for good, and the only remedy anyone had was to ask
     * the customer to submit the whole thing again. That is a poor thing to ask of someone who
     * already filled the form in once, and it loses their original photos.
     *
     * <p>Synchronous on purpose: the admin pressed a button and needs to see the answer, which is
     * a vision call of a few seconds. Re-running is safe — the valuation simply overwrites.
     */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('sell:update') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{id}/ai-estimate")
    public ResponseEntity<ApiResponse<SellRequestResponse>> generateEstimate(@PathVariable UUID id) {
        // estimate() returns quietly whatever goes wrong, which would look to an admin like a
        // device nothing could be said about rather than a key nobody set or a call that failed.
        String problem = aiEstimateService.explainEstimate(id);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        return ApiResponse.success(sellService.getByIdAdmin(id), "Valuation generated");
    }

    /** Mark that the store received the device (→ UNDER_REVIEW). */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('sell:update') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{id}/received")
    public ResponseEntity<ApiResponse<SellRequestResponse>> markReceived(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id) {
        return ApiResponse.success(sellService.markDeviceReceived(id, adminId), "Device marked received");
    }

    /** Quote what Buyology will pay for the device (→ OFFER_MADE). */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('sell:update') or @rbacPolicy.legacyAdmin()")
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
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('sell:update') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{id}/paid")
    public ResponseEntity<ApiResponse<SellRequestResponse>> markPaidOut(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id) {
        return ApiResponse.success(sellService.markPaidOut(id, adminId), "Payout recorded");
    }

    /** Generic status transition (e.g. mark CANCELLED) with an optional note. */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('sell:update') or @rbacPolicy.legacyAdmin()")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SellRequestResponse>> updateStatus(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id,
            @Valid @RequestBody UpdateSellStatusRequest req) {
        return ApiResponse.success(
                sellService.updateStatus(id, req.getStatus(), req.getNote(), adminId),
                "Sell request status updated");
    }
}
