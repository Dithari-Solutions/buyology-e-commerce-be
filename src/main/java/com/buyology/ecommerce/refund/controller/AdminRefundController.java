package com.buyology.ecommerce.refund.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.refund.dto.AdminRefundActionRequest;
import com.buyology.ecommerce.refund.dto.RefundRequestResponse;
import com.buyology.ecommerce.refund.dto.RefundSettingResponse;
import com.buyology.ecommerce.refund.dto.UpdateRefundSettingRequest;
import com.buyology.ecommerce.refund.enums.RefundRequestStatus;
import com.buyology.ecommerce.refund.service.RefundRequestService;
import com.buyology.ecommerce.refund.service.RefundSettingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminRefundController {

    private final RefundRequestService refundService;
    private final RefundSettingService settingService;

    public AdminRefundController(RefundRequestService refundService,
                                 RefundSettingService settingService) {
        this.refundService = refundService;
        this.settingService = settingService;
    }

    // ── Settings ────────────────────────────────────────────────────────────

    @GetMapping("/refund-settings")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('refund:setting:read') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<RefundSettingResponse>> getSettings() {
        return ApiResponse.success(settingService.getResponse(), "Refund settings fetched");
    }

    @PutMapping("/refund-settings")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('refund:setting:update') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<RefundSettingResponse>> updateSettings(
            @AuthenticationPrincipal UUID adminId,
            @Valid @RequestBody UpdateRefundSettingRequest body) {
        return ApiResponse.success(settingService.update(body, adminId), "Refund settings updated");
    }

    // ── Refund requests ─────────────────────────────────────────────────────

    @GetMapping("/refunds")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('refund:read') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<Page<RefundRequestResponse>>> list(
            @RequestParam(required = false) RefundRequestStatus status,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(refundService.listForAdmin(status, storeId, page, size), "Refund requests fetched");
    }

    @GetMapping("/refunds/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('refund:read') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> get(@PathVariable UUID id) {
        return ApiResponse.success(refundService.getForAdmin(id), "Refund request fetched");
    }

    @PostMapping("/refunds/{id}/approve")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('refund:moderate') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> approve(
            @AuthenticationPrincipal UUID adminId,
            @PathVariable UUID id,
            @RequestBody(required = false) AdminRefundActionRequest body) {
        String note = body == null ? null : body.adminNote();
        return ApiResponse.success(refundService.approve(id, adminId, note), "Refund request approved");
    }

    @PostMapping("/refunds/{id}/reject")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('refund:moderate') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> reject(
            @AuthenticationPrincipal UUID adminId,
            @PathVariable UUID id,
            @RequestBody AdminRefundActionRequest body) {
        String reason = body == null ? null : body.rejectionReason();
        return ApiResponse.success(refundService.reject(id, adminId, reason), "Refund request rejected");
    }

    @PostMapping("/refunds/{id}/mark-received")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('refund:update') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> markReceived(
            @AuthenticationPrincipal UUID adminId,
            @PathVariable UUID id) {
        return ApiResponse.success(refundService.markReceived(id, adminId), "Refund request marked received");
    }

    @PostMapping("/refunds/{id}/pay")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('refund:payout') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> pay(
            @AuthenticationPrincipal UUID adminId,
            @PathVariable UUID id) {
        return ApiResponse.success(refundService.pay(id, adminId), "Refund processed");
    }
}
