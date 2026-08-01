package com.buyology.ecommerce.role.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.role.dto.CreateRoleRequest;
import com.buyology.ecommerce.role.dto.PermissionResponse;
import com.buyology.ecommerce.role.dto.RoleHolderResponse;
import com.buyology.ecommerce.role.dto.RoleResponse;
import com.buyology.ecommerce.role.dto.SetRolePermissionsRequest;
import com.buyology.ecommerce.role.dto.UpdateRoleRequest;
import com.buyology.ecommerce.role.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/roles")
@Tag(name = "Roles", description = "Admin APIs for managing roles and their permissions")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "Get all roles")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        return roleService.getAllRoles();
    }

    @Operation(summary = "Get role by ID")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable UUID id) {
        return roleService.getRoleById(id);
    }

    @Operation(summary = "Create a new role")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return roleService.createRole(request);
    }

    @Operation(summary = "Update a role")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable UUID id,
            @RequestBody UpdateRoleRequest request) {
        return roleService.updateRole(id, request);
    }

    @Operation(summary = "Delete a role")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable UUID id) {
        return roleService.deleteRole(id);
    }

    // =====================
    // Role-Permission Management
    // =====================

    @Operation(summary = "Get all permissions assigned to a role")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/{roleId}/permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getRolePermissions(@PathVariable UUID roleId) {
        return roleService.getRolePermissions(roleId);
    }

    @Operation(summary = "Replace a role's permissions",
               description = "Sets the role's permissions to exactly the supplied ids — the whole "
                       + "permission matrix saved in one atomic call. An empty list clears them all.")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PutMapping("/{roleId}/permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> setRolePermissions(
            @PathVariable UUID roleId,
            @Valid @RequestBody SetRolePermissionsRequest request) {
        return roleService.setRolePermissions(roleId, request);
    }

    @Operation(summary = "List the users holding a role")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/{roleId}/users")
    public ResponseEntity<ApiResponse<List<RoleHolderResponse>>> getRoleHolders(@PathVariable UUID roleId) {
        return roleService.getRoleHolders(roleId);
    }

    @Operation(summary = "Add a permission to a role")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/{roleId}/permissions/{permissionId}")
    public ResponseEntity<ApiResponse<Void>> addPermissionToRole(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId) {
        return roleService.addPermissionToRole(roleId, permissionId);
    }

    @Operation(summary = "Remove a permission from a role")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    public ResponseEntity<ApiResponse<Void>> removePermissionFromRole(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId) {
        return roleService.removePermissionFromRole(roleId, permissionId);
    }
}
