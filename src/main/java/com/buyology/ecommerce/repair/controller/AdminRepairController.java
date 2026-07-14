package com.buyology.ecommerce.repair.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.repair.domain.RepairStatus;
import com.buyology.ecommerce.repair.dto.RepairRequestResponse;
import com.buyology.ecommerce.repair.dto.SetRepairPriceRequest;
import com.buyology.ecommerce.repair.dto.UpdateRepairStatusRequest;
import com.buyology.ecommerce.repair.service.RepairService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repair-team-facing device-repair endpoints. Secured to the SUPERADMIN + REPAIR roles
 * (mirrors {@link com.buyology.ecommerce.b2b.productrequest.controller.AdminB2bProductRequestController}).
 */
@RestController
@RequestMapping("/api/admin/repairs")
@PreAuthorize("hasAnyRole('SUPERADMIN','REPAIR')")
public class AdminRepairController {

    private final RepairService repairService;

    public AdminRepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RepairRequestResponse>>> list(
            @RequestParam(required = false) RepairStatus status) {
        return ApiResponse.success(repairService.listAll(status), "Repair requests fetched");
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> count() {
        return ApiResponse.success(Map.of("newCount", repairService.countUnread()), "New repair count");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RepairRequestResponse>> get(@PathVariable UUID id) {
        return ApiResponse.success(repairService.getByIdAdmin(id), "Repair request fetched");
    }

    /** Mark that the store received the device (→ UNDER_REVIEW). */
    @PostMapping("/{id}/received")
    public ResponseEntity<ApiResponse<RepairRequestResponse>> markReceived(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id) {
        return ApiResponse.success(repairService.markDeviceReceived(id, adminId), "Device marked received");
    }

    /** Quote the fixing price (→ PRICE_ESTIMATED). */
    @PostMapping("/{id}/price")
    public ResponseEntity<ApiResponse<RepairRequestResponse>> setPrice(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id,
            @Valid @RequestBody SetRepairPriceRequest req) {
        return ApiResponse.success(
                repairService.setPrice(id, req.getPrice(), req.getCurrency(), req.getEstimatedTime(), req.getNote(), adminId),
                "Price estimate sent");
    }

    /** Generic status transition (e.g. mark COMPLETED / CANCELLED) with an optional note. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<RepairRequestResponse>> updateStatus(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id,
            @Valid @RequestBody UpdateRepairStatusRequest req) {
        return ApiResponse.success(
                repairService.updateStatus(id, req.getStatus(), req.getNote(), adminId),
                "Repair status updated");
    }
}
