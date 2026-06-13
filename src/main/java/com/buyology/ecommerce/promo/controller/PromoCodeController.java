package com.buyology.ecommerce.promo.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.promo.dto.*;
import com.buyology.ecommerce.promo.service.PromoCodeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    public PromoCodeController(PromoCodeService promoCodeService) {
        this.promoCodeService = promoCodeService;
    }

    // ── Customer endpoints ───────────────────────────────────────────────────

    @PostMapping("/api/promo/validate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ValidatePromoCodeResponse>> validate(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ValidatePromoCodeRequest req) {
        ValidatePromoCodeResponse result = promoCodeService.validateAndCalculate(
                req.getCode(), userId, req.getOrderAmount(), req.getProductIds());
        return ApiResponse.success(result, "Promo code validated");
    }

    /** Current redemption offer (token cost + reward) for rendering the redeem button. */
    @GetMapping("/api/promo/redeem-info")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TokenRedemptionInfoResponse>> redeemInfo(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.success(promoCodeService.getRedemptionInfo(userId), "Redemption info fetched");
    }

    /** Redeem the configured number of tokens for a personal coupon code. */
    @PostMapping("/api/promo/redeem")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<RedeemTokensResponse>> redeem(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.success(promoCodeService.redeemTokens(userId), "Tokens redeemed");
    }

    // ── Admin endpoints ──────────────────────────────────────────────────────

    @PostMapping("/api/admin/promo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PromoCodeResponse>> create(
            @Valid @RequestBody CreatePromoCodeRequest req) {
        return ApiResponse.created(promoCodeService.createPromoCode(req), "Promo code created");
    }

    @GetMapping("/api/admin/promo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PromoCodeResponse>>> listAll() {
        return ApiResponse.success(promoCodeService.listAll(), "Promo codes fetched");
    }

    @GetMapping("/api/admin/promo/{id}/usages")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PromoUsageResponse>>> usages(@PathVariable UUID id) {
        return ApiResponse.success(promoCodeService.listUsages(id), "Promo usage fetched");
    }

    @PutMapping("/api/admin/promo/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PromoCodeResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePromoCodeRequest req) {
        return ApiResponse.success(promoCodeService.updatePromoCode(id, req), "Promo code updated");
    }

    @DeleteMapping("/api/admin/promo/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        promoCodeService.deactivate(id);
        return ApiResponse.success(null, "Promo code deactivated");
    }

    @PostMapping("/api/admin/promo/{id}/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> sendToCustomers(
            @PathVariable UUID id,
            @RequestBody SendPromoRequest req) {
        promoCodeService.sendPromoToCustomers(id, req);
        return ApiResponse.success(null, "Promo code is being sent to customers");
    }

    /** Mint a coupon bound to ONE user and email/notify only that user. */
    @PostMapping("/api/admin/promo/issue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PromoCodeResponse>> issueToUser(
            @Valid @RequestBody IssuePersonalCodeRequest req) {
        return ApiResponse.created(promoCodeService.issuePersonalCode(req),
                "Personal promo code issued");
    }

    // ── Admin: token-redemption configuration ────────────────────────────────

    @GetMapping("/api/admin/promo/redeem-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TokenRedemptionConfigDto>> getRedeemConfig() {
        return ApiResponse.success(
                TokenRedemptionConfigDto.from(promoCodeService.getOrCreateConfig()),
                "Token redemption config fetched");
    }

    @PutMapping("/api/admin/promo/redeem-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TokenRedemptionConfigDto>> updateRedeemConfig(
            @RequestBody TokenRedemptionConfigDto req) {
        return ApiResponse.success(promoCodeService.updateConfig(req),
                "Token redemption config updated");
    }
}
