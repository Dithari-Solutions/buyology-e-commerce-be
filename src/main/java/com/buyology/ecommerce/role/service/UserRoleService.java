package com.buyology.ecommerce.role.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.role.domain.Role;
import com.buyology.ecommerce.role.domain.UserRole;
import com.buyology.ecommerce.role.domain.UserRoleId;
import com.buyology.ecommerce.role.dto.AssignRoleToUserRequest;
import com.buyology.ecommerce.role.dto.UserRoleResponse;
import com.buyology.ecommerce.role.repository.RoleRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserRoleService {

    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserRoleService(
            UserRoleRepository userRoleRepository,
            UserRepository userRepository,
            RoleRepository roleRepository) {
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public ResponseEntity<ApiResponse<List<UserRoleResponse>>> getUserRoles(UUID userId) {
        if (!userRepository.existsById(userId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }

        List<UserRoleResponse> roles = userRoleRepository.findByIdUserId(userId)
                .stream()
                .map(UserRoleResponse::from)
                .toList();

        return ApiResponse.success(roles, "User roles retrieved successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<UserRoleResponse>> assignRoleToUser(AssignRoleToUserRequest request) {
        Users user = userRepository.findById(request.getUserId()).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }

        Role role = roleRepository.findById(request.getRoleId()).orElse(null);
        if (role == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Role not found");
        }

        if (userRoleRepository.existsByIdUserIdAndIdRoleId(request.getUserId(), request.getRoleId())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Role is already assigned to this user");
        }

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(request.getUserId(), request.getRoleId()));
        userRole.setUser(user);
        userRole.setRole(role);

        if (request.getAssignedBy() != null) {
            userRepository.findById(request.getAssignedBy()).ifPresent(userRole::setAssignedBy);
        }

        UserRole saved = userRoleRepository.save(userRole);
        return ApiResponse.created(UserRoleResponse.from(saved), "Role assigned to user successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> removeRoleFromUser(UUID userId, UUID roleId) {
        if (!userRepository.existsById(userId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }
        if (!roleRepository.existsById(roleId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Role not found");
        }
        if (!userRoleRepository.existsByIdUserIdAndIdRoleId(userId, roleId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Role is not assigned to this user");
        }

        userRoleRepository.deleteByIdUserIdAndIdRoleId(userId, roleId);
        return ApiResponse.success(null, "Role removed from user successfully");
    }
}
