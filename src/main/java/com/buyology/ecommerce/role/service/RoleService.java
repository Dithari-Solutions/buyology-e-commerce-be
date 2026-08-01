package com.buyology.ecommerce.role.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.role.domain.Permission;
import com.buyology.ecommerce.role.domain.Role;
import com.buyology.ecommerce.role.domain.RolePermission;
import com.buyology.ecommerce.role.domain.RolePermissionId;
import com.buyology.ecommerce.role.domain.UserRole;
import com.buyology.ecommerce.role.dto.CreateRoleRequest;
import com.buyology.ecommerce.role.dto.PermissionResponse;
import com.buyology.ecommerce.role.dto.RoleHolderResponse;
import com.buyology.ecommerce.role.dto.RoleResponse;
import com.buyology.ecommerce.role.dto.SetRolePermissionsRequest;
import com.buyology.ecommerce.role.dto.UpdateRoleRequest;
import com.buyology.ecommerce.role.repository.PermissionRepository;
import com.buyology.ecommerce.role.repository.RolePermissionRepository;
import com.buyology.ecommerce.role.repository.RoleRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    /** Role names are used verbatim in {@code hasRole(...)} expressions and the JWT roles claim. */
    private static final Pattern ROLE_NAME = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;

    public RoleService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RolePermissionRepository rolePermissionRepository,
            UserRoleRepository userRoleRepository,
            UserRepository userRepository,
            AuthCredentialRepository authCredentialRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
    }

    /** Every role with its permission codes and holder count, batched into three queries. */
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        List<Role> roles = roleRepository.findAll();

        Map<UUID, List<String>> codesByRole = new HashMap<>();
        for (Object[] row : rolePermissionRepository.findAllRoleIdToPermissionCode()) {
            codesByRole.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add((String) row[1]);
        }

        Map<UUID, Long> holdersByRole = new HashMap<>();
        for (Object[] row : userRoleRepository.countHoldersPerRole()) {
            holdersByRole.put((UUID) row[0], ((Number) row[1]).longValue());
        }

        List<RoleResponse> responses = roles.stream()
                .sorted(Comparator.comparing(Role::getName, Comparator.nullsLast(String::compareTo)))
                .map(role -> {
                    List<String> codes = codesByRole.getOrDefault(role.getId(), List.of())
                            .stream().sorted().toList();
                    return RoleResponse.from(role, codes, holdersByRole.getOrDefault(role.getId(), 0L));
                })
                .toList();

        return ApiResponse.success(responses, "Roles retrieved successfully");
    }

    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(UUID id) {
        Role role = roleRepository.findById(id).orElse(null);
        if (role == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Role not found");
        }
        return ApiResponse.success(
                RoleResponse.from(role, permissionCodesOf(id), userRoleRepository.countByIdRoleId(id)),
                "Role retrieved successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(CreateRoleRequest request) {
        String name = normaliseRoleName(request.getName());
        if (name == null || !ROLE_NAME.matcher(name).matches()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, ROLE_NAME_HELP);
        }
        // Compare against the normalised name: the old check used the raw input, so "marketing"
        // passed and was then saved as a second "MARKETING" row.
        if (roleRepository.existsByName(name)) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Role '" + name + "' already exists");
        }

        Role role = new Role();
        role.setName(name);
        role.setDescription(trimToNull(request.getDescription()));
        // Roles created through the API are custom by definition; only the seeder creates system roles.
        role.setIsSystem(false);

        Role saved = roleRepository.save(role);
        log.info("Role {} created", name);
        return ApiResponse.created(RoleResponse.from(saved, List.of(), 0L), "Role created successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(UUID id, UpdateRoleRequest request) {
        Role role = roleRepository.findById(id).orElse(null);
        if (role == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Role not found");
        }

        if (request.getName() != null) {
            String name = normaliseRoleName(request.getName());
            if (name == null || !ROLE_NAME.matcher(name).matches()) {
                return ApiResponse.failure(HttpStatus.BAD_REQUEST, ROLE_NAME_HELP);
            }
            if (!name.equals(role.getName())) {
                if (RoleResponse.isProtected(role.getName())) {
                    return ApiResponse.failure(HttpStatus.CONFLICT,
                            "The " + role.getName() + " role cannot be renamed — access checks across the "
                                    + "platform reference it by name.");
                }
                if (Boolean.TRUE.equals(role.getIsSystem())) {
                    return ApiResponse.failure(HttpStatus.CONFLICT,
                            "'" + role.getName() + "' is a built-in role and cannot be renamed. "
                                    + "Create a custom role instead.");
                }
                if (roleRepository.existsByName(name)) {
                    return ApiResponse.failure(HttpStatus.CONFLICT, "Role '" + name + "' already exists");
                }
                role.setName(name);
            }
        }
        if (request.getDescription() != null) {
            role.setDescription(trimToNull(request.getDescription()));
        }

        Role saved = roleRepository.save(role);
        return ApiResponse.success(
                RoleResponse.from(saved, permissionCodesOf(id), userRoleRepository.countByIdRoleId(id)),
                "Role updated successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteRole(UUID id) {
        Role role = roleRepository.findById(id).orElse(null);
        if (role == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Role not found");
        }
        if (RoleResponse.isProtected(role.getName())) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "The " + role.getName() + " role cannot be deleted — it is the platform's recovery path.");
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "'" + role.getName() + "' is a built-in role and cannot be deleted. "
                            + "Clear its permissions or unassign it instead.");
        }
        long holders = userRoleRepository.countByIdRoleId(id);
        if (holders > 0) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "'" + role.getName() + "' is still assigned to " + holders
                            + (holders == 1 ? " user" : " users")
                            + ". Remove it from them before deleting the role.");
        }

        // role_permissions has a real FK to roles; without clearing it first the delete fails at the
        // database with an opaque 500.
        rolePermissionRepository.deleteByRoleId(id);
        roleRepository.delete(role);
        log.info("Role {} deleted", role.getName());
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
                .sorted(Comparator.comparing(PermissionResponse::getCode,
                        Comparator.nullsLast(String::compareTo)))
                .toList();

        return ApiResponse.success(permissions, "Role permissions retrieved successfully");
    }

    /**
     * Replaces a role's permissions with exactly {@code request.permissionIds}.
     *
     * <p>One call for the whole matrix: a half-applied grid (some checkboxes saved, some not) is a
     * worse outcome than a rejected save, and per-checkbox requests cannot be made atomic.
     */
    @Transactional
    public ResponseEntity<ApiResponse<RoleResponse>> setRolePermissions(
            UUID roleId, SetRolePermissionsRequest request) {
        Role role = roleRepository.findById(roleId).orElse(null);
        if (role == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Role not found");
        }
        if (RoleResponse.isProtected(role.getName())) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "The " + role.getName() + " role always holds every permission and cannot be edited.");
        }

        List<UUID> requested = request.getPermissionIds() == null ? List.of() : request.getPermissionIds();
        LinkedHashSet<UUID> desired = new LinkedHashSet<>(requested);
        desired.remove(null);

        // Resolve everything before writing, so an unknown id rejects the whole save.
        Map<UUID, Permission> permissions = new HashMap<>();
        for (UUID permissionId : desired) {
            Permission permission = permissionRepository.findById(permissionId).orElse(null);
            if (permission == null) {
                return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Permission not found: " + permissionId);
            }
            permissions.put(permissionId, permission);
        }

        for (RolePermission rp : rolePermissionRepository.findByIdRoleId(roleId)) {
            UUID permissionId = rp.getId().getPermissionId();
            if (!desired.contains(permissionId)) {
                rolePermissionRepository.deleteByIdRoleIdAndIdPermissionId(roleId, permissionId);
            }
        }
        for (UUID permissionId : desired) {
            if (!rolePermissionRepository.existsByIdRoleIdAndIdPermissionId(roleId, permissionId)) {
                RolePermission rp = new RolePermission();
                rp.setId(new RolePermissionId(roleId, permissionId));
                rp.setRole(role);
                rp.setPermission(permissions.get(permissionId));
                rolePermissionRepository.save(rp);
            }
        }

        List<String> codes = permissions.values().stream()
                .map(Permission::getCode)
                .sorted()
                .toList();
        log.info("Role {} permissions set to {}", role.getName(), codes);

        return ApiResponse.success(
                RoleResponse.from(role, codes, userRoleRepository.countByIdRoleId(roleId)),
                "Role permissions updated successfully");
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
        Role role = roleRepository.findById(roleId).orElse(null);
        if (role == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Role not found");
        }
        if (RoleResponse.isProtected(role.getName())) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "The " + role.getName() + " role always holds every permission and cannot be edited.");
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

    // =====================
    // Role holders
    // =====================

    /** Users holding {@code roleId} — so the impact of a role change is visible before saving it. */
    public ResponseEntity<ApiResponse<List<RoleHolderResponse>>> getRoleHolders(UUID roleId) {
        if (!roleRepository.existsById(roleId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Role not found");
        }

        List<UserRole> assignments = userRoleRepository.findByIdRoleId(roleId);
        List<UUID> userIds = assignments.stream()
                .map(ur -> ur.getId().getUserId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, Users> usersById = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(Users::getId, u -> u, (a, b) -> a));

        Map<UUID, AuthCredentials> credentialByUserId = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (AuthCredentials c : authCredentialRepository.findByUserIdIn(userIds)) {
                credentialByUserId.putIfAbsent(c.getUserId(), c);
            }
        }

        List<RoleHolderResponse> holders = assignments.stream()
                .map(ur -> {
                    UUID userId = ur.getId().getUserId();
                    Users user = usersById.get(userId);
                    if (user == null) return null;
                    AuthCredentials credential = credentialByUserId.get(userId);
                    return new RoleHolderResponse(
                            userId,
                            credential != null ? credential.getId() : null,
                            user.getFirstName(),
                            user.getLastName(),
                            credential != null ? credential.getEmail() : null,
                            user.getUserType() != null ? user.getUserType().name() : null,
                            user.getStatus(),
                            ur.getAssignedAt());
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RoleHolderResponse::getEmail,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        return ApiResponse.success(holders, "Role holders retrieved successfully");
    }

    // =====================
    // Helpers
    // =====================

    private static final String ROLE_NAME_HELP =
            "Role name must be 2–50 characters, start with a letter, and use only letters, numbers "
                    + "and underscores (e.g. CONTENT_EDITOR)";

    private List<String> permissionCodesOf(UUID roleId) {
        return rolePermissionRepository.findByIdRoleId(roleId).stream()
                .map(rp -> rp.getPermission().getCode())
                .sorted()
                .toList();
    }

    private static String normaliseRoleName(String raw) {
        if (raw == null) return null;
        String name = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return name.isEmpty() ? null : name;
    }

    private static String trimToNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
