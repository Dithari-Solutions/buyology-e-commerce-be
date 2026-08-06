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
 * Repair-team-facing device-repair endpoints, guarded by the {@code repair:read} / {@code repair:update}
 * permission codes so the repair desk can be granted access from the Roles &amp; Permissions console
 * (the seeded REPAIR role carries exactly these two). The {@code @rbacPolicy.legacyAdmin()} clause keeps
 * existing blanket admins working until {@code rbac.strict-permissions} is turned on, matching every other
 * admin controller.
 */
@RestController
@RequestMapping("/api/admin/repairs")
public class AdminRepairController {

    private final RepairService repairService;

    public AdminRepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('repair:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RepairRequestResponse>>> list(
            @RequestParam(required = false) RepairStatus status) {
        return ApiResponse.success(repairService.listAll(status), "Repair requests fetched");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('repair:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> count() {
        return ApiResponse.success(Map.of("newCount", repairService.countUnread()), "New repair count");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('repair:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RepairRequestResponse>> get(@PathVariable UUID id) {
        return ApiResponse.success(repairService.getByIdAdmin(id), "Repair request fetched");
    }

    /** Mark that the store received the device (→ UNDER_REVIEW). */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('repair:update') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{id}/received")
    public ResponseEntity<ApiResponse<RepairRequestResponse>> markReceived(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id) {
        return ApiResponse.success(repairService.markDeviceReceived(id, adminId), "Device marked received");
    }

    /** Quote the fixing price (→ PRICE_ESTIMATED). */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('repair:update') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{id}/price")
    public ResponseEntity<ApiResponse<RepairRequestResponse>> setPrice(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id,
            @Valid @RequestBody SetRepairPriceRequest req) {
        return ApiResponse.success(
                repairService.setPrice(id, req.getPrice(), req.getCurrency(), req.getEstimatedTime(), req.getNote(), adminId),
                "Price estimate sent");
    }

    /** Generic status transition (e.g. mark COMPLETED / CANCELLED) with an optional note. */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('repair:update') or @rbacPolicy.legacyAdmin()")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<RepairRequestResponse>> updateStatus(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id,
            @Valid @RequestBody UpdateRepairStatusRequest req) {
        return ApiResponse.success(
                repairService.updateStatus(id, req.getStatus(), req.getNote(), adminId),
                "Repair status updated");
    }
}
