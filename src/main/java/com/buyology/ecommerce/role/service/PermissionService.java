package com.buyology.ecommerce.role.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.role.domain.Permission;
import com.buyology.ecommerce.role.dto.CreatePermissionRequest;
import com.buyology.ecommerce.role.dto.PermissionResponse;
import com.buyology.ecommerce.role.dto.UpdatePermissionRequest;
import com.buyology.ecommerce.role.repository.PermissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        List<PermissionResponse> permissions = permissionRepository.findAll()
                .stream()
                .map(PermissionResponse::from)
                .toList();
        return ApiResponse.success(permissions, "Permissions retrieved successfully");
    }

    public ResponseEntity<ApiResponse<PermissionResponse>> getPermissionById(UUID id) {
        return permissionRepository.findById(id)
                .map(permission -> ApiResponse.success(PermissionResponse.from(permission), "Permission retrieved successfully"))
                .orElse(ApiResponse.failure(HttpStatus.NOT_FOUND, "Permission not found"));
    }

    @Transactional
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(CreatePermissionRequest request) {
        if (permissionRepository.existsByCode(request.getCode())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Permission with code '" + request.getCode() + "' already exists");
        }

        Permission permission = new Permission();
        permission.setCode(request.getCode().toUpperCase());
        permission.setDescription(request.getDescription());

        Permission saved = permissionRepository.save(permission);
        return ApiResponse.created(PermissionResponse.from(saved), "Permission created successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<PermissionResponse>> updatePermission(UUID id, UpdatePermissionRequest request) {
        return permissionRepository.findById(id)
                .map(permission -> {
                    if (request.getDescription() != null) {
                        permission.setDescription(request.getDescription());
                    }
                    Permission saved = permissionRepository.save(permission);
                    return ApiResponse.success(PermissionResponse.from(saved), "Permission updated successfully");
                })
                .orElse(ApiResponse.failure(HttpStatus.NOT_FOUND, "Permission not found"));
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deletePermission(UUID id) {
        if (!permissionRepository.existsById(id)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Permission not found");
        }
        permissionRepository.deleteById(id);
        return ApiResponse.success(null, "Permission deleted successfully");
    }
}
