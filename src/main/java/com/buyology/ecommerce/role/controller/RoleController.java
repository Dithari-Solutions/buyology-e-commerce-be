package com.buyology.ecommerce.role.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.role.dto.CreateRoleRequest;
import com.buyology.ecommerce.role.dto.PermissionResponse;
import com.buyology.ecommerce.role.dto.RoleResponse;
import com.buyology.ecommerce.role.dto.UpdateRoleRequest;
import com.buyology.ecommerce.role.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        return roleService.getAllRoles();
    }

    @Operation(summary = "Get role by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable UUID id) {
        return roleService.getRoleById(id);
    }

    @Operation(summary = "Create a new role")
    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return roleService.createRole(request);
    }

    @Operation(summary = "Update a role")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable UUID id,
            @RequestBody UpdateRoleRequest request) {
        return roleService.updateRole(id, request);
    }

    @Operation(summary = "Delete a role")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable UUID id) {
        return roleService.deleteRole(id);
    }

    // =====================
    // Role-Permission Management
    // =====================

    @Operation(summary = "Get all permissions assigned to a role")
    @GetMapping("/{roleId}/permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getRolePermissions(@PathVariable UUID roleId) {
        return roleService.getRolePermissions(roleId);
    }

    @Operation(summary = "Add a permission to a role")
    @PostMapping("/{roleId}/permissions/{permissionId}")
    public ResponseEntity<ApiResponse<Void>> addPermissionToRole(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId) {
        return roleService.addPermissionToRole(roleId, permissionId);
    }

    @Operation(summary = "Remove a permission from a role")
    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    public ResponseEntity<ApiResponse<Void>> removePermissionFromRole(
            @PathVariable UUID roleId,
            @PathVariable UUID permissionId) {
        return roleService.removePermissionFromRole(roleId, permissionId);
    }
}
