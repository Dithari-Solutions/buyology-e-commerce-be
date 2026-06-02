package com.buyology.ecommerce.role.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.role.dto.AssignPermissionToUserRequest;
import com.buyology.ecommerce.role.dto.UserPermissionResponse;
import com.buyology.ecommerce.role.service.UserPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/user-permissions")
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "User Permissions", description = "Admin APIs for assigning and removing direct permissions from users")
public class UserPermissionController {

    private final UserPermissionService userPermissionService;

    public UserPermissionController(UserPermissionService userPermissionService) {
        this.userPermissionService = userPermissionService;
    }

    @Operation(summary = "Get all direct permissions assigned to a user")
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<List<UserPermissionResponse>>> getUserPermissions(@PathVariable UUID userId) {
        return userPermissionService.getUserPermissions(userId);
    }

    @Operation(summary = "Assign a direct permission to a user (ALLOW or DENY)")
    @PostMapping
    public ResponseEntity<ApiResponse<UserPermissionResponse>> assignPermissionToUser(
            @Valid @RequestBody AssignPermissionToUserRequest request) {
        return userPermissionService.assignPermissionToUser(request);
    }

    @Operation(summary = "Remove a direct permission from a user")
    @DeleteMapping("/users/{userId}/permissions/{permissionId}")
    public ResponseEntity<ApiResponse<Void>> removePermissionFromUser(
            @PathVariable UUID userId,
            @PathVariable UUID permissionId) {
        return userPermissionService.removePermissionFromUser(userId, permissionId);
    }
}
