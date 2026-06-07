package com.buyology.ecommerce.admin.controller;

import com.buyology.ecommerce.admin.dto.AdminUserDetailResponse;
import com.buyology.ecommerce.admin.dto.AdminUserListResponse;
import com.buyology.ecommerce.admin.service.AdminInactivityService;
import com.buyology.ecommerce.admin.service.AdminUserService;
import com.buyology.ecommerce.auth.service.MfaService;
import com.buyology.ecommerce.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin – Users", description = "Admin access to user management")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AdminInactivityService adminInactivityService;
    private final MfaService mfaService;

    public AdminUserController(AdminUserService adminUserService,
                               AdminInactivityService adminInactivityService,
                               MfaService mfaService) {
        this.adminUserService = adminUserService;
        this.adminInactivityService = adminInactivityService;
        this.mfaService = mfaService;
    }

    @Operation(summary = "List all users (paginated)")
    @GetMapping
    public ResponseEntity<ApiResponse<AdminUserListResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminUserService.listUsers(page, size);
    }

    @Operation(summary = "Get full user detail by authCredentialId — includes profile, favorites, and active cart")
    @GetMapping("/{authCredentialId}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(
            @PathVariable UUID authCredentialId) {
        return adminUserService.getUserDetail(authCredentialId);
    }

    @Operation(summary = "Block a user manually",
               description = "Sets the user's status to SUSPENDED and revokes all their active sessions")
    @PatchMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<String>> blockUser(@PathVariable UUID userId) {
        return adminInactivityService.blockUser(userId);
    }

    @Operation(summary = "Unblock a user",
               description = "Restores the user's status to ACTIVE")
    @PatchMapping("/{userId}/unblock")
    public ResponseEntity<ApiResponse<String>> unblockUser(@PathVariable UUID userId) {
        return adminInactivityService.unblockUser(userId);
    }

    @Operation(summary = "Trigger inactive-user blocking manually",
               description = "Immediately runs the inactivity check and suspends all users " +
                              "who have exceeded the configured inactivity threshold")
    @PostMapping("/block-inactive")
    public ResponseEntity<ApiResponse<String>> blockInactiveUsers() {
        return adminInactivityService.triggerInactivityBlock();
    }

    @Operation(summary = "Reset a user's two-factor authentication (SUPERADMIN only)",
               description = "Wipes the user's Google Authenticator enrollment and recovery codes. "
                       + "The user is forced to re-enroll on their next sign-in. Use for lost-device recovery.")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/{userId}/mfa/reset")
    public ResponseEntity<ApiResponse<String>> resetUserMfa(@PathVariable UUID userId) {
        return mfaService.resetForUser(userId);
    }
}
