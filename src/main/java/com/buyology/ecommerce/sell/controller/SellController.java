package com.buyology.ecommerce.sell.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.sell.domain.DeviceCondition;
import com.buyology.ecommerce.sell.dto.ChooseSellDeliveryRequest;
import com.buyology.ecommerce.sell.dto.ChooseSellReturnRequest;
import com.buyology.ecommerce.sell.dto.OfferDecisionRequest;
import com.buyology.ecommerce.sell.dto.SellDeliveryResponse;
import com.buyology.ecommerce.sell.dto.SellRequestResponse;
import com.buyology.ecommerce.sell.dto.SellStoreOptionResponse;
import com.buyology.ecommerce.sell.service.SellService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Customer-facing sell (trade-in) endpoints. All require authentication; the service keys ownership
 * on the caller's credential (sub). The JWT filter sets the principal to users.id (uid).
 */
@RestController
@RequestMapping("/api/sell-requests")
@PreAuthorize("isAuthenticated()")
public class SellController {

    private final SellService sellService;

    public SellController(SellService sellService) {
        this.sellService = sellService;
    }

    /**
     * Whether the caller may open a sell request at all. The storefront calls this first and shows
     * a "complete your profile" screen instead of the form when {@code eligible} is false — a
     * trade-in ends with money changing hands, so we won't take the request without contact details.
     */
    @GetMapping("/eligibility")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> eligibility(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.success(
                Map.of("eligible", sellService.isProfileComplete(userId)),
                "Sell eligibility resolved");
    }

    /**
     * Open a sell request. Multipart so up to four device images can be attached; scalar fields are
     * plain {@code @RequestParam} (NOT a JSON @RequestPart) to avoid browser FormData 415 issues.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SellRequestResponse>> create(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("productName") String productName,
            @RequestParam("brand") String brand,
            @RequestParam("model") String model,
            @RequestParam(value = "purchaseDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseDate,
            @RequestParam(value = "deviceCondition", required = false) DeviceCondition deviceCondition,
            @RequestParam("description") String description,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return ApiResponse.created(
                sellService.create(userId, productName, brand, model, purchaseDate,
                        deviceCondition, description, images),
                "Sell request submitted");
    }

    /**
     * The caller's sell requests. {@code currency} is optional — when given, the AED AI valuation is
     * also returned converted into it for display (the AED figures are always present).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SellRequestResponse>>> listMine(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(value = "currency", required = false) String currency) {
        return ApiResponse.success(sellService.listOwn(userId, currency), "Sell requests fetched");
    }

    /** Active store branches in a country (alpha-3) for the drop-off / pickup / payout picker. */
    @GetMapping("/stores")
    public ResponseEntity<ApiResponse<List<SellStoreOptionResponse>>> stores(
            @RequestParam("country") String country) {
        return ApiResponse.success(sellService.listStoreOptions(country), "Store branches fetched");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SellRequestResponse>> get(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id,
            @RequestParam(value = "currency", required = false) String currency) {
        return ApiResponse.success(sellService.getOwn(userId, id, currency), "Sell request fetched");
    }

    @PostMapping("/{id}/delivery")
    public ResponseEntity<ApiResponse<SellDeliveryResponse>> chooseDelivery(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id,
            @Valid @RequestBody ChooseSellDeliveryRequest req) {
        return ApiResponse.success(
                sellService.chooseDelivery(userId, id, req.getMethod(), req.getStoreLocationId(),
                        req.getCurrency(), req.getRedirectionUrl()),
                "Delivery method saved");
    }

    @PostMapping("/{id}/offer-response")
    public ResponseEntity<ApiResponse<SellRequestResponse>> respondToOffer(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id,
            @Valid @RequestBody OfferDecisionRequest req) {
        return ApiResponse.success(
                sellService.respondToOffer(userId, id, req.getAccept(), req.getPayoutMethod()),
                "Response recorded");
    }

    @PostMapping("/{id}/return")
    public ResponseEntity<ApiResponse<SellDeliveryResponse>> chooseReturn(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id,
            @Valid @RequestBody ChooseSellReturnRequest req) {
        return ApiResponse.success(
                sellService.chooseReturn(userId, id, req.getMethod(), req.getCurrency(), req.getRedirectionUrl()),
                "Return method saved");
    }
}
