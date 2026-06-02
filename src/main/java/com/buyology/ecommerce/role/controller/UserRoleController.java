package com.buyology.ecommerce.role.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.role.dto.AssignRoleToUserRequest;
import com.buyology.ecommerce.role.dto.UserRoleResponse;
import com.buyology.ecommerce.role.service.UserRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/user-roles")
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "User Roles", description = "Admin APIs for assigning and removing roles from users")
public class UserRoleController {

    private final UserRoleService userRoleService;

    public UserRoleController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @Operation(summary = "Get all roles assigned to a user")
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<List<UserRoleResponse>>> getUserRoles(@PathVariable UUID userId) {
        return userRoleService.getUserRoles(userId);
    }

    @Operation(summary = "Assign a role to a user")
    @PostMapping
    public ResponseEntity<ApiResponse<UserRoleResponse>> assignRoleToUser(
            @Valid @RequestBody AssignRoleToUserRequest request) {
        return userRoleService.assignRoleToUser(request);
    }

    @Operation(summary = "Remove a role from a user")
    @DeleteMapping("/users/{userId}/roles/{roleId}")
    public ResponseEntity<ApiResponse<Void>> removeRoleFromUser(
            @PathVariable UUID userId,
            @PathVariable UUID roleId) {
        return userRoleService.removeRoleFromUser(userId, roleId);
    }
}
