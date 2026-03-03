package com.buyology.ecommerce.role.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.role.domain.Permission;
import com.buyology.ecommerce.role.domain.Role;
import com.buyology.ecommerce.role.domain.RolePermission;
import com.buyology.ecommerce.role.domain.RolePermissionId;
import com.buyology.ecommerce.role.dto.CreateRoleRequest;
import com.buyology.ecommerce.role.dto.PermissionResponse;
import com.buyology.ecommerce.role.dto.RoleResponse;
import com.buyology.ecommerce.role.dto.UpdateRoleRequest;
import com.buyology.ecommerce.role.repository.PermissionRepository;
import com.buyology.ecommerce.role.repository.RolePermissionRepository;
import com.buyology.ecommerce.role.repository.RoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public RoleService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        List<RoleResponse> roles = roleRepository.findAll()
                .stream()
                .map(RoleResponse::from)
                .toList();
        return ApiResponse.success(roles, "Roles retrieved successfully");
    }

    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(UUID id) {
        return roleRepository.findById(id)
                .map(role -> ApiResponse.success(RoleResponse.from(role), "Role retrieved successfully"))
                .orElse(ApiResponse.failure(HttpStatus.NOT_FOUND, "Role not found"));
    }

    @Transactional
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(CreateRoleRequest request) {
        if (roleRepository.existsByName(request.getName())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Role with name '" + request.getName() + "' already exists");
        }

        Role role = new Role();
        role.setName(request.getName().toUpperCase());
        role.setDescription(request.getDescription());
        role.setIsSystem(request.getIsSystem() != null ? request.getIsSystem() : true);

        Role saved = roleRepository.save(role);
        return ApiResponse.created(RoleResponse.from(saved), "Role created successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(UUID id, UpdateRoleRequest request) {
        return roleRepository.findById(id)
                .map(role -> {
                    if (request.getName() != null) {
                        if (!role.getName().equals(request.getName().toUpperCase())
                                && roleRepository.existsByName(request.getName().toUpperCase())) {
                            return ApiResponse.<RoleResponse>failure(HttpStatus.CONFLICT,
                                    "Role with name '" + request.getName() + "' already exists");
                        }
                        role.setName(request.getName().toUpperCase());
                    }
                    if (request.getDescription() != null) {
                        role.setDescription(request.getDescription());
                    }
                    if (request.getIsSystem() != null) {
                        role.setIsSystem(request.getIsSystem());
                    }
                    Role saved = roleRepository.save(role);
                    return ApiResponse.success(RoleResponse.from(saved), "Role updated successfully");
                })
                .orElse(ApiResponse.failure(HttpStatus.NOT_FOUND, "Role not found"));
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteRole(UUID id) {
        if (!roleRepository.existsById(id)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Role not found");
        }
        roleRepository.deleteById(id);
        return ApiResponse.success(null, "Role deleted successfully");
    }

    // =====================
    // Role-Permission Management
    // =====================

    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getRolePermissions(UUID roleId) {
        if (!roleRepository.existsById(roleId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Role not found");
        }

        List<PermissionResponse> permissions = rolePermissionRepository.findByIdRoleId(roleId)
                .stream()
                .map(rp -> PermissionResponse.from(rp.getPermission()))
                .toList();

        return ApiResponse.success(permissions, "Role permissions retrieved successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> addPermissionToRole(UUID roleId, UUID permissionId) {
        Role role = roleRepository.findById(roleId).orElse(null);
        if (role == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Role not found");
        }

        Permission permission = permissionRepository.findById(permissionId).orElse(null);
        if (permission == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Permission not found");
        }

        if (rolePermissionRepository.existsByIdRoleIdAndIdPermissionId(roleId, permissionId)) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Permission is already assigned to this role");
        }

        RolePermission rolePermission = new RolePermission();
        rolePermission.setId(new RolePermissionId(roleId, permissionId));
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);

        rolePermissionRepository.save(rolePermission);
        return ApiResponse.success(null, "Permission added to role successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> removePermissionFromRole(UUID roleId, UUID permissionId) {
        if (!roleRepository.existsById(roleId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Role not found");
        }
        if (!permissionRepository.existsById(permissionId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Permission not found");
        }
        if (!rolePermissionRepository.existsByIdRoleIdAndIdPermissionId(roleId, permissionId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Permission is not assigned to this role");
        }

        rolePermissionRepository.deleteByIdRoleIdAndIdPermissionId(roleId, permissionId);
        return ApiResponse.success(null, "Permission removed from role successfully");
    }
}
