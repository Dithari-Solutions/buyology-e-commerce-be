package com.buyology.ecommerce.admin.controller;

import com.buyology.ecommerce.admin.dto.AdminEmailLookupResponse;
import com.buyology.ecommerce.admin.dto.AdminUserDetailResponse;
import com.buyology.ecommerce.admin.dto.AdminUserListResponse;
import com.buyology.ecommerce.admin.dto.CreateAdminRequest;
import com.buyology.ecommerce.admin.dto.PromoteToAdminRequest;
import com.buyology.ecommerce.admin.service.AdminInactivityService;
import com.buyology.ecommerce.admin.service.AdminUserService;
import com.buyology.ecommerce.auth.service.MfaService;
import com.buyology.ecommerce.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
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

    @Operation(summary = "Create an admin user and assign roles (SUPERADMIN only)",
               description = "Provisions a new ADMIN-type user with a LOCAL password and the given roles.")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> createAdmin(
            @Valid @RequestBody CreateAdminRequest request) {
        return adminUserService.createAdmin(request);
    }

    @Operation(summary = "List admin users (paginated, SUPERADMIN only)",
               description = "Admin accounts filtered in the database, each with its assigned roles. "
                       + "Use this rather than paging the full user list and filtering client-side.")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/admins")
    public ResponseEntity<ApiResponse<AdminUserListResponse>> listAdmins(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return adminUserService.listAdmins(page, size, search);
    }

    @Operation(summary = "Check what holds an email address (SUPERADMIN only)",
               description = "Explains a create-admin conflict: reports the account occupying the "
                       + "address and whether it can be promoted to admin instead.")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<AdminEmailLookupResponse>> lookupEmail(@RequestParam String email) {
        return adminUserService.lookupEmail(email);
    }

    @Operation(summary = "Promote an existing account to admin (SUPERADMIN only)",
               description = "Converts a customer account to ADMIN and assigns the given roles. "
                       + "Resolves the common case where staff already hold a storefront account.")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/{userId}/promote")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> promoteToAdmin(
            @PathVariable UUID userId,
            @Valid @RequestBody PromoteToAdminRequest request) {
        return adminUserService.promoteToAdmin(userId, request);
    }

    @Operation(summary = "List all users (paginated)")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('user:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<AdminUserListResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return adminUserService.listUsers(page, size, search);
    }

    @Operation(summary = "Get full user detail by authCredentialId — includes profile, favorites, and active cart")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('user:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/{authCredentialId}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(
            @PathVariable UUID authCredentialId) {
        return adminUserService.getUserDetail(authCredentialId);
    }

    @Operation(summary = "Block a user manually",
               description = "Sets the user's status to SUSPENDED and revokes all their active sessions")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('user:block') or @rbacPolicy.legacyAdmin()")
    @PatchMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<String>> blockUser(@PathVariable UUID userId) {
        return adminInactivityService.blockUser(userId);
    }

    @Operation(summary = "Unblock a user",
               description = "Restores the user's status to ACTIVE")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('user:block') or @rbacPolicy.legacyAdmin()")
    @PatchMapping("/{userId}/unblock")
    public ResponseEntity<ApiResponse<String>> unblockUser(@PathVariable UUID userId) {
        return adminInactivityService.unblockUser(userId);
    }

    @Operation(summary = "Trigger inactive-user blocking manually",
               description = "Immediately runs the inactivity check and suspends all users " +
                              "who have exceeded the configured inactivity threshold")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('user:block') or @rbacPolicy.legacyAdmin()")
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
