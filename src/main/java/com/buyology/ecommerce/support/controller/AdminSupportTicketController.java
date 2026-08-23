package com.buyology.ecommerce.support.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.support.domain.SupportTicketStatus;
import com.buyology.ecommerce.support.dto.AddSupportMessageRequest;
import com.buyology.ecommerce.support.dto.SupportTicketResponse;
import com.buyology.ecommerce.support.dto.UpdateSupportStatusRequest;
import com.buyology.ecommerce.support.service.SupportTicketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Support-team-facing ticket endpoints, guarded by the {@code support:read} / {@code support:update}
 * permission codes so the support desk can be granted access from the Roles &amp; Permissions
 * console. The {@code @rbacPolicy.legacyAdmin()} clause keeps existing blanket admins working until
 * {@code rbac.strict-permissions} is turned on, matching every other admin controller.
 */
@RestController
@RequestMapping("/api/admin/support-tickets")
public class AdminSupportTicketController {

    private final SupportTicketService supportTicketService;

    public AdminSupportTicketController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('support:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<SupportTicketResponse>>> list(
            @RequestParam(required = false) SupportTicketStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(supportTicketService.listAll(status, page, size), "Support tickets fetched");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('support:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> count() {
        return ApiResponse.success(Map.of("newCount", supportTicketService.countUnread()), "New ticket count");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('support:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> get(@PathVariable UUID id) {
        return ApiResponse.success(supportTicketService.getByIdAdmin(id), "Support ticket fetched");
    }

    /** Generic status transition (e.g. mark RESOLVED / CLOSED) with an optional customer-visible note. */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('support:update') or @rbacPolicy.legacyAdmin()")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> updateStatus(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id,
            @Valid @RequestBody UpdateSupportStatusRequest req) {
        return ApiResponse.success(
                supportTicketService.updateStatus(id, req.getStatus(), req.getNote(), adminId),
                "Support ticket status updated");
    }

    /** Team reply into the ticket's conversation. */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('support:update') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{id}/reply")
    public ResponseEntity<ApiResponse<SupportTicketResponse>> reply(
            @AuthenticationPrincipal UUID adminId, @PathVariable UUID id,
            @Valid @RequestBody AddSupportMessageRequest req) {
        return ApiResponse.success(
                supportTicketService.reply(id, adminId, req.getBody()),
                "Reply sent");
    }
}
