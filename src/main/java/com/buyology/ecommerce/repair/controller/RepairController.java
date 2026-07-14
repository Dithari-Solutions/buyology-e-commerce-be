package com.buyology.ecommerce.repair.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.repair.dto.ChooseDeliveryRequest;
import com.buyology.ecommerce.repair.dto.ChooseReturnRequest;
import com.buyology.ecommerce.repair.dto.PriceDecisionRequest;
import com.buyology.ecommerce.repair.dto.RepairRequestResponse;
import com.buyology.ecommerce.repair.dto.StoreLocationOptionResponse;
import com.buyology.ecommerce.repair.service.RepairService;
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
import java.util.UUID;

/**
 * Customer-facing device-repair endpoints. All require authentication; the service keys ownership
 * on the caller's credential (sub). The JWT filter sets the principal to users.id (uid).
 */
@RestController
@RequestMapping("/api/repairs")
@PreAuthorize("isAuthenticated()")
public class RepairController {

    private final RepairService repairService;

    public RepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    /**
     * Open a repair request. Multipart so up to four problem images can be attached; scalar fields
     * are plain {@code @RequestParam} (NOT a JSON @RequestPart) to avoid browser FormData 415 issues.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RepairRequestResponse>> create(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("productName") String productName,
            @RequestParam("brand") String brand,
            @RequestParam("model") String model,
            @RequestParam(value = "purchaseDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseDate,
            @RequestParam("description") String description,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return ApiResponse.created(
                repairService.create(userId, productName, brand, model, purchaseDate, description, images),
                "Repair request submitted");
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RepairRequestResponse>>> listMine(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.success(repairService.listOwn(userId), "Repair requests fetched");
    }

    /** Active store branches in a country (alpha-3) for the drop-off / pickup picker. */
    @GetMapping("/stores")
    public ResponseEntity<ApiResponse<List<StoreLocationOptionResponse>>> stores(
            @RequestParam("country") String country) {
        return ApiResponse.success(repairService.listStoreOptions(country), "Store branches fetched");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RepairRequestResponse>> get(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        return ApiResponse.success(repairService.getOwn(userId, id), "Repair request fetched");
    }

    @PostMapping("/{id}/delivery")
    public ResponseEntity<ApiResponse<RepairRequestResponse>> chooseDelivery(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id,
            @Valid @RequestBody ChooseDeliveryRequest req) {
        return ApiResponse.success(
                repairService.chooseDelivery(userId, id, req.getMethod(), req.getStoreLocationId(), req.getCurrency()),
                "Delivery method saved");
    }

    @PostMapping("/{id}/price-response")
    public ResponseEntity<ApiResponse<RepairRequestResponse>> respondToPrice(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id,
            @Valid @RequestBody PriceDecisionRequest req) {
        return ApiResponse.success(
                repairService.respondToPrice(userId, id, req.getAccept()),
                "Response recorded");
    }

    @PostMapping("/{id}/return")
    public ResponseEntity<ApiResponse<RepairRequestResponse>> chooseReturn(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID id,
            @Valid @RequestBody ChooseReturnRequest req) {
        return ApiResponse.success(
                repairService.chooseReturn(userId, id, req.getMethod(), req.getCurrency()),
                "Return method saved");
    }
}
