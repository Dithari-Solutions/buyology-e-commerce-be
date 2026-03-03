package com.buyology.ecommerce.role.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.role.domain.Permission;
import com.buyology.ecommerce.role.domain.UserPermission;
import com.buyology.ecommerce.role.domain.UserPermissionId;
import com.buyology.ecommerce.role.dto.AssignPermissionToUserRequest;
import com.buyology.ecommerce.role.dto.UserPermissionResponse;
import com.buyology.ecommerce.role.repository.PermissionRepository;
import com.buyology.ecommerce.role.repository.UserPermissionRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserPermissionService {

    private final UserPermissionRepository userPermissionRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    public UserPermissionService(
            UserPermissionRepository userPermissionRepository,
            UserRepository userRepository,
            PermissionRepository permissionRepository) {
        this.userPermissionRepository = userPermissionRepository;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
    }

    public ResponseEntity<ApiResponse<List<UserPermissionResponse>>> getUserPermissions(UUID userId) {
        if (!userRepository.existsById(userId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }

        List<UserPermissionResponse> permissions = userPermissionRepository.findByIdUserId(userId)
                .stream()
                .map(UserPermissionResponse::from)
                .toList();

        return ApiResponse.success(permissions, "User permissions retrieved successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<UserPermissionResponse>> assignPermissionToUser(AssignPermissionToUserRequest request) {
        Users user = userRepository.findById(request.getUserId()).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }

        Permission permission = permissionRepository.findById(request.getPermissionId()).orElse(null);
        if (permission == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Permission not found");
        }

        if (userPermissionRepository.existsByIdUserIdAndIdPermissionId(request.getUserId(), request.getPermissionId())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Permission is already assigned to this user");
        }

        UserPermission userPermission = new UserPermission();
        userPermission.setId(new UserPermissionId(request.getUserId(), request.getPermissionId()));
        userPermission.setUser(user);
        userPermission.setPermission(permission);
        userPermission.setEffect(request.getEffect());

        if (request.getAssignedBy() != null) {
            userRepository.findById(request.getAssignedBy()).ifPresent(userPermission::setAssignedBy);
        }

        UserPermission saved = userPermissionRepository.save(userPermission);
        return ApiResponse.created(UserPermissionResponse.from(saved), "Permission assigned to user successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> removePermissionFromUser(UUID userId, UUID permissionId) {
        if (!userRepository.existsById(userId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }
        if (!permissionRepository.existsById(permissionId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Permission not found");
        }
        if (!userPermissionRepository.existsByIdUserIdAndIdPermissionId(userId, permissionId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Permission is not assigned to this user");
        }

        userPermissionRepository.deleteByIdUserIdAndIdPermissionId(userId, permissionId);
        return ApiResponse.success(null, "Permission removed from user successfully");
    }
}
