package com.buyology.ecommerce.role.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.role.domain.Permission;
import com.buyology.ecommerce.role.dto.CreatePermissionRequest;
import com.buyology.ecommerce.role.dto.PermissionResponse;
import com.buyology.ecommerce.role.dto.UpdatePermissionRequest;
import com.buyology.ecommerce.role.repository.PermissionRepository;
import com.buyology.ecommerce.role.repository.RolePermissionRepository;
import com.buyology.ecommerce.role.repository.UserPermissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    /** Codes are lower-case {@code module:action} segments, e.g. {@code store:product:assign}. */
    private static final Pattern PERMISSION_CODE = Pattern.compile("^[a-z][a-z0-9]*(:[a-z0-9]+)+$");

    private static final String CODE_HELP =
            "Permission code must be lower-case colon-separated segments, e.g. 'store:product:assign'";

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionRepository userPermissionRepository;

    public PermissionService(PermissionRepository permissionRepository,
                             RolePermissionRepository rolePermissionRepository,
                             UserPermissionRepository userPermissionRepository) {
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userPermissionRepository = userPermissionRepository;
    }

    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        List<PermissionResponse> permissions = permissionRepository.findAll()
                .stream()
                .map(PermissionResponse::from)
                .sorted(Comparator.comparing(PermissionResponse::getCode,
                        Comparator.nullsLast(String::compareTo)))
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
        // Codes are matched verbatim by hasAuthority('...'), and every code the platform checks is
        // lower-case. Upper-casing here (as this used to) produced permissions that no guard could
        // ever match — grantable in the UI, inert at runtime.
        String code = request.getCode() == null ? null : request.getCode().trim().toLowerCase(Locale.ROOT);
        if (code == null || !PERMISSION_CODE.matcher(code).matches()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, CODE_HELP);
        }
        if (permissionRepository.existsByCode(code)) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Permission '" + code + "' already exists");
        }

        Permission permission = new Permission();
        permission.setCode(code);
        permission.setDescription(trimToNull(request.getDescription()));

        Permission saved = permissionRepository.save(permission);
        log.info("Permission {} created", code);
        return ApiResponse.created(PermissionResponse.from(saved), "Permission created successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<PermissionResponse>> updatePermission(UUID id, UpdatePermissionRequest request) {
        return permissionRepository.findById(id)
                .map(permission -> {
                    if (request.getDescription() != null) {
                        permission.setDescription(trimToNull(request.getDescription()));
                    }
                    Permission saved = permissionRepository.save(permission);
                    return ApiResponse.success(PermissionResponse.from(saved), "Permission updated successfully");
                })
                .orElse(ApiResponse.failure(HttpStatus.NOT_FOUND, "Permission not found"));
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deletePermission(UUID id) {
        Permission permission = permissionRepository.findById(id).orElse(null);
        if (permission == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Permission not found");
        }

        // role_permissions and user_permissions both hold real FKs to permissions; deleting a
        // referenced row fails at the database with an opaque 500 rather than a usable message.
        long roleUses = rolePermissionRepository.countByIdPermissionId(id);
        long userUses = userPermissionRepository.countByIdPermissionId(id);
        if (roleUses > 0 || userUses > 0) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "'" + permission.getCode() + "' is still granted by " + roleUses
                            + (roleUses == 1 ? " role" : " roles") + " and " + userUses
                            + (userUses == 1 ? " user override" : " user overrides")
                            + ". Revoke it everywhere before deleting it.");
        }

        permissionRepository.delete(permission);
        log.info("Permission {} deleted", permission.getCode());
        return ApiResponse.success(null, "Permission deleted successfully");
    }

    private static String trimToNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
