package com.buyology.ecommerce.role.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.role.dto.CreatePermissionRequest;
import com.buyology.ecommerce.role.dto.PermissionResponse;
import com.buyology.ecommerce.role.dto.UpdatePermissionRequest;
import com.buyology.ecommerce.role.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/permissions")
@Tag(name = "Permissions", description = "Admin APIs for managing permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Operation(summary = "Get all permissions")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        return permissionService.getAllPermissions();
    }

    @Operation(summary = "Get permission by ID")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponse>> getPermissionById(@PathVariable UUID id) {
        return permissionService.getPermissionById(id);
    }

    @Operation(summary = "Create a new permission")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(
            @Valid @RequestBody CreatePermissionRequest request) {
        return permissionService.createPermission(request);
    }

    @Operation(summary = "Update a permission")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PermissionResponse>> updatePermission(
            @PathVariable UUID id,
            @RequestBody UpdatePermissionRequest request) {
        return permissionService.updatePermission(id, request);
    }

    @Operation(summary = "Delete a permission")
    @PreAuthorize("hasRole('SUPERADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePermission(@PathVariable UUID id) {
        return permissionService.deletePermission(id);
    }
}
