package com.buyology.ecommerce.admin.service;

import com.buyology.ecommerce.admin.dto.AdminEmailLookupResponse;
import com.buyology.ecommerce.admin.dto.AdminUserDetailResponse;
import com.buyology.ecommerce.admin.dto.AdminUserListResponse;
import com.buyology.ecommerce.admin.dto.AdminUserSummaryResponse;
import com.buyology.ecommerce.admin.dto.ChangeAdminPasswordRequest;
import com.buyology.ecommerce.admin.dto.CreateAdminRequest;
import com.buyology.ecommerce.admin.dto.PromoteToAdminRequest;
import com.buyology.ecommerce.admin.dto.SetAdminRolesRequest;
import com.buyology.ecommerce.admin.dto.UpdateAdminRequest;
import com.buyology.ecommerce.role.domain.UserPermission;
import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.auth.repository.RefreshTokenRepository;
import com.buyology.ecommerce.role.repository.UserPermissionRepository;
import com.buyology.ecommerce.common.utils.PasswordPolicy;
import com.buyology.ecommerce.common.utils.PasswordUtils;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.role.domain.Role;
import com.buyology.ecommerce.role.domain.UserRole;
import com.buyology.ecommerce.role.domain.UserRoleId;
import com.buyology.ecommerce.role.repository.RoleRepository;
import com.buyology.ecommerce.role.repository.UserRoleRepository;
import com.buyology.ecommerce.cart.domain.Cart;
import com.buyology.ecommerce.cart.domain.CartItem;
import com.buyology.ecommerce.cart.dto.CartItemResponse;
import com.buyology.ecommerce.cart.dto.CartItemSpecSelectionResponse;
import com.buyology.ecommerce.cart.dto.CartResponse;
import com.buyology.ecommerce.cart.repository.CartItemRepository;
import com.buyology.ecommerce.cart.repository.CartItemSpecSelectionRepository;
import com.buyology.ecommerce.cart.repository.CartRepository;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.favorite.domain.Favorite;
import com.buyology.ecommerce.favorite.dto.FavoriteItemResponse;
import com.buyology.ecommerce.favorite.dto.FavoriteListResponse;
import com.buyology.ecommerce.favorite.repository.FavoriteRepository;
import com.buyology.ecommerce.user.domain.UserAddress;
import com.buyology.ecommerce.user.domain.UserProfiles;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserAddressRepository;
import com.buyology.ecommerce.user.repository.UserProfilesRepository;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private static final String SUPERADMIN = "SUPERADMIN";

    private static final DateTimeFormatter JOINED_ON =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final UserRepository userRepository;
    private final UserProfilesRepository profilesRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final UserAddressRepository userAddressRepository;
    private final FavoriteRepository favoriteRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartItemSpecSelectionRepository specSelectionRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AdminUserService(UserRepository userRepository,
                            UserProfilesRepository profilesRepository,
                            AuthCredentialRepository authCredentialRepository,
                            UserAddressRepository userAddressRepository,
                            FavoriteRepository favoriteRepository,
                            CartRepository cartRepository,
                            CartItemRepository cartItemRepository,
                            CartItemSpecSelectionRepository specSelectionRepository,
                            RoleRepository roleRepository,
                            UserRoleRepository userRoleRepository,
                            UserPermissionRepository userPermissionRepository,
                            RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.profilesRepository = profilesRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.userAddressRepository = userAddressRepository;
        this.favoriteRepository = favoriteRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.specSelectionRepository = specSelectionRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.userPermissionRepository = userPermissionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // ─── Create an admin user with roles (SUPERADMIN only) ────────────────────

    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> createAdmin(CreateAdminRequest req) {
        String email = normaliseEmail(req.getEmail());
        if (email == null) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is required");
        }

        try {
            PasswordPolicy.validate(req.getPassword());
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        // Diagnose (rather than merely detect) an email clash: the blocking account is often a
        // customer or a stale credential the superadmin can neither see nor act on from this page.
        AdminEmailLookupResponse holder = lookupEmailHolder(email, true);
        if (!holder.isAvailable()) {
            return ApiResponse.failure(HttpStatus.CONFLICT, holder.getReason());
        }

        // Resolve + validate every requested role before creating anything.
        List<Role> roles = new ArrayList<>();
        if (req.getRoleIds() != null) {
            for (UUID roleId : req.getRoleIds()) {
                Role role = roleRepository.findById(roleId).orElse(null);
                if (role == null) {
                    return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Role not found: " + roleId);
                }
                roles.add(role);
            }
        }
        if (roles.isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "At least one role is required");
        }

        Users user = new Users();
        user.setFirstName(req.getFirstName() == null ? null : req.getFirstName().trim());
        user.setLastName(req.getLastName() == null ? null : req.getLastName().trim());
        user.setUserType(Users.UserType.ADMIN);
        user.setIsGuest(false);
        user.setStatus("ACTIVE");
        userRepository.save(user);

        AuthCredentials credentials = new AuthCredentials();
        credentials.setUserId(user.getId());
        credentials.setEmail(email);
        credentials.setPasswordHash(PasswordUtils.hashPassword(req.getPassword()));
        credentials.setProvider("LOCAL");
        credentials.setIsActive(true);
        authCredentialRepository.save(credentials);

        Users assignedBy = currentActor();
        for (Role role : roles) {
            if (!userRoleRepository.existsByIdUserIdAndIdRoleId(user.getId(), role.getId())) {
                UserRole ur = new UserRole();
                ur.setId(new UserRoleId(user.getId(), role.getId()));
                ur.setUser(user);
                ur.setRole(role);
                ur.setAssignedBy(assignedBy);
                userRoleRepository.save(ur);
            }
        }

        AdminUserDetailResponse detail = new AdminUserDetailResponse();
        detail.setUserId(user.getId());
        detail.setAuthCredentialId(credentials.getId());
        detail.setEmail(email);
        detail.setUserType(user.getUserType().name());
        detail.setStatus(user.getStatus());
        detail.setJoinedAt(user.getCreatedAt());
        detail.setFirstName(user.getFirstName());
        detail.setLastName(user.getLastName());
        return ApiResponse.created(detail, "Admin created");
    }

    // ─── Promote an existing account to admin (SUPERADMIN only) ───────────────

    /**
     * Converts a live account into an ADMIN and assigns roles.
     *
     * <p>The escape hatch for the most common create-admin conflict: the address already belongs to
     * a customer account (staff who once shopped on the storefront), which cannot be created over
     * and is not visible on the Admins page.
     */
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> promoteToAdmin(UUID userId, PromoteToAdminRequest req) {
        Users user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "This account is " + user.getStatus() + " and cannot be promoted. Restore it first.");
        }

        List<Role> roles = new ArrayList<>();
        if (req.getRoleIds() != null) {
            for (UUID roleId : req.getRoleIds()) {
                Role role = roleRepository.findById(roleId).orElse(null);
                if (role == null) {
                    return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Role not found: " + roleId);
                }
                roles.add(role);
            }
        }
        if (roles.isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "At least one role is required");
        }

        user.setUserType(Users.UserType.ADMIN);
        userRepository.save(user);

        Users assignedBy = currentActor();
        for (Role role : roles) {
            if (!userRoleRepository.existsByIdUserIdAndIdRoleId(user.getId(), role.getId())) {
                UserRole ur = new UserRole();
                ur.setId(new UserRoleId(user.getId(), role.getId()));
                ur.setUser(user);
                ur.setRole(role);
                ur.setAssignedBy(assignedBy);
                userRoleRepository.save(ur);
            }
        }

        log.info("User {} promoted to ADMIN with roles {} by {}",
                user.getId(),
                roles.stream().map(Role::getName).toList(),
                assignedBy != null ? assignedBy.getId() : "system");

        AuthCredentials credential = authCredentialRepository.findByUserId(user.getId()).stream()
                .findFirst().orElse(null);

        AdminUserDetailResponse detail = new AdminUserDetailResponse();
        detail.setUserId(user.getId());
        detail.setAuthCredentialId(credential != null ? credential.getId() : null);
        detail.setEmail(credential != null ? credential.getEmail() : null);
        detail.setUserType(user.getUserType().name());
        detail.setStatus(user.getStatus());
        detail.setJoinedAt(user.getCreatedAt());
        detail.setFirstName(user.getFirstName());
        detail.setLastName(user.getLastName());
        return ApiResponse.success(detail, "Account promoted to admin");
    }

    // ─── Edit / delete an admin (SUPERADMIN only) ─────────────────────────────

    /** Updates an admin's name and email. Absent fields are left as they are. */
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> updateAdmin(UUID userId, UpdateAdminRequest req) {
        Users user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Admin not found");
        }
        if (isRetired(user)) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "This account has been deleted and cannot be edited.");
        }

        if (req.getFirstName() != null) user.setFirstName(trimToNull(req.getFirstName()));
        if (req.getLastName() != null) user.setLastName(trimToNull(req.getLastName()));

        AuthCredentials credential = primaryCredential(user.getId());
        String newEmail = normaliseEmail(req.getEmail());
        if (newEmail != null && credential != null && !newEmail.equalsIgnoreCase(credential.getEmail())) {
            // Same diagnosis as creating an admin, minus this account's own rows — otherwise an admin
            // would always collide with itself the moment anything else on the form was edited.
            AdminEmailLookupResponse holder = lookupEmailHolder(newEmail, false, user.getId());
            if (!holder.isAvailable()) {
                return ApiResponse.failure(HttpStatus.CONFLICT, holder.getReason());
            }
            credential.setEmail(newEmail);
            authCredentialRepository.save(credential);
        }

        userRepository.save(user);
        log.info("Admin {} updated by {}", user.getId(), actorIdForLog());
        return ApiResponse.success(buildAdminDetail(user, credential), "Admin updated");
    }

    /**
     * Sets a new password for an admin and signs them out everywhere.
     *
     * <p>Revoking the refresh tokens is the point, not a side effect: this is the lost-access
     * recovery path, so any session already open on the old password must stop working.
     */
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<String>> changeAdminPassword(UUID userId, ChangeAdminPasswordRequest req) {
        Users user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Admin not found");
        }
        if (isRetired(user)) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "This account has been deleted.");
        }
        try {
            PasswordPolicy.validate(req.getNewPassword());
        } catch (IllegalArgumentException e) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        List<AuthCredentials> credentials = authCredentialRepository.findByUserId(user.getId()).stream()
                .filter(c -> "LOCAL".equalsIgnoreCase(c.getProvider()))
                .collect(Collectors.toList());
        if (credentials.isEmpty()) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "This account signs in through an external provider and has no password to change.");
        }

        String hash = PasswordUtils.hashPassword(req.getNewPassword());
        Instant now = Instant.now();
        int revoked = 0;
        for (AuthCredentials c : credentials) {
            c.setPasswordHash(hash);
            c.setIsActive(true);
            authCredentialRepository.save(c);
            revoked += refreshTokenRepository.revokeAllActiveByAuthCredential(c, now);
        }

        log.info("Password reset for admin {} by {} ({} session(s) revoked)",
                user.getId(), actorIdForLog(), revoked);
        return ApiResponse.success("updated",
                "Password updated. The admin has been signed out of " + revoked + " active session(s).");
    }

    /** Replaces an admin's roles with exactly {@code req.roleIds}. */
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<List<String>>> setAdminRoles(UUID userId, SetAdminRolesRequest req) {
        Users user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Admin not found");
        }

        List<UUID> requested = req.getRoleIds() == null ? List.of() : req.getRoleIds();
        LinkedHashSet<UUID> desired = new LinkedHashSet<>(requested);
        desired.remove(null);
        if (desired.isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "At least one role is required");
        }

        Map<UUID, Role> roles = new HashMap<>();
        for (UUID roleId : desired) {
            Role role = roleRepository.findById(roleId).orElse(null);
            if (role == null) {
                return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Role not found: " + roleId);
            }
            roles.put(roleId, role);
        }

        boolean keepsSuperadmin = roles.values().stream().anyMatch(r -> SUPERADMIN.equalsIgnoreCase(r.getName()));
        boolean hadSuperadmin = userRoleRepository.findRoleNamesByUserId(userId).stream()
                .anyMatch(SUPERADMIN::equalsIgnoreCase);
        if (hadSuperadmin && !keepsSuperadmin) {
            if (userId.equals(SecurityUtils.currentUserIdOrNull())) {
                return ApiResponse.failure(HttpStatus.CONFLICT,
                        "You cannot remove your own Super Admin role — you would lose access to this page. "
                                + "Ask another Super Admin to do it.");
            }
            if (countOtherSuperadmins(userId) == 0) {
                return ApiResponse.failure(HttpStatus.CONFLICT,
                        "This is the only Super Admin. Grant the role to someone else first.");
            }
        }

        Users assignedBy = currentActor();
        for (UserRole existing : userRoleRepository.findByIdUserId(userId)) {
            UUID roleId = existing.getId().getRoleId();
            if (!desired.contains(roleId)) {
                userRoleRepository.deleteByIdUserIdAndIdRoleId(userId, roleId);
            }
        }
        for (UUID roleId : desired) {
            if (!userRoleRepository.existsByIdUserIdAndIdRoleId(userId, roleId)) {
                UserRole ur = new UserRole();
                ur.setId(new UserRoleId(userId, roleId));
                ur.setUser(user);
                ur.setRole(roles.get(roleId));
                ur.setAssignedBy(assignedBy);
                userRoleRepository.save(ur);
            }
        }

        List<String> names = roles.values().stream().map(Role::getName).sorted().toList();
        log.info("Admin {} roles set to {} by {}", userId, names, actorIdForLog());
        return ApiResponse.success(names, "Roles updated");
    }

    /**
     * Deletes an admin account.
     *
     * <p>Anonymised rather than removed from the table. {@code user_roles.assigned_by} and various
     * audit trails point at admin rows, so a hard delete would either fail on a foreign key or erase
     * the record of who granted what. The email is rewritten so the address can be reused, and every
     * session is revoked so access stops immediately.
     */
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<String>> deleteAdmin(UUID userId) {
        Users user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Admin not found");
        }
        if (user.getUserType() != Users.UserType.ADMIN) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "This is a customer account. Delete it from the Users page instead.");
        }
        if (userId.equals(SecurityUtils.currentUserIdOrNull())) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "You cannot delete your own account.");
        }
        if (isRetired(user)) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "This account has already been deleted.");
        }
        boolean isSuperadmin = userRoleRepository.findRoleNamesByUserId(userId).stream()
                .anyMatch(SUPERADMIN::equalsIgnoreCase);
        if (isSuperadmin && countOtherSuperadmins(userId) == 0) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "This is the only Super Admin. Promote someone else before deleting this account.");
        }

        Instant now = Instant.now();
        int revoked = 0;
        for (AuthCredentials c : authCredentialRepository.findByUserId(userId)) {
            revoked += refreshTokenRepository.revokeAllActiveByAuthCredential(c, now);
            c.setIsActive(false);
            c.setEmail("deleted+" + userId + "@deleted.buyology.local");
            authCredentialRepository.save(c);
        }

        // Drop the access grants outright — a deleted admin restored by hand should come back with no
        // privileges rather than silently regaining whatever it held.
        for (UserRole ur : userRoleRepository.findByIdUserId(userId)) {
            userRoleRepository.deleteByIdUserIdAndIdRoleId(userId, ur.getId().getRoleId());
        }
        for (UserPermission up : userPermissionRepository.findByIdUserId(userId)) {
            userPermissionRepository.deleteByIdUserIdAndIdPermissionId(userId, up.getId().getPermissionId());
        }

        user.setStatus("DELETED");
        user.setDeletedAt(now);
        userRepository.save(user);

        log.info("Admin {} deleted by {} ({} session(s) revoked)", userId, actorIdForLog(), revoked);
        return ApiResponse.success("deleted", "Admin deleted and signed out everywhere.");
    }

    /** Live SUPERADMIN accounts other than {@code excludingUserId}. */
    private long countOtherSuperadmins(UUID excludingUserId) {
        return userRoleRepository.findUserIdsByRoleName(SUPERADMIN).stream()
                .filter(id -> !id.equals(excludingUserId))
                .map(id -> userRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .filter(u -> !isRetired(u))
                .count();
    }

    private AuthCredentials primaryCredential(UUID userId) {
        List<AuthCredentials> all = authCredentialRepository.findByUserId(userId);
        return all.stream()
                .filter(c -> "LOCAL".equalsIgnoreCase(c.getProvider()))
                .findFirst()
                .orElseGet(() -> all.stream().findFirst().orElse(null));
    }

    private AdminUserDetailResponse buildAdminDetail(Users user, AuthCredentials credential) {
        AdminUserDetailResponse detail = new AdminUserDetailResponse();
        detail.setUserId(user.getId());
        detail.setAuthCredentialId(credential != null ? credential.getId() : null);
        detail.setEmail(credential != null ? credential.getEmail() : null);
        detail.setUserType(user.getUserType() != null ? user.getUserType().name() : null);
        detail.setStatus(user.getStatus());
        detail.setJoinedAt(user.getCreatedAt());
        detail.setFirstName(user.getFirstName());
        detail.setLastName(user.getLastName());
        return detail;
    }

    private static String trimToNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Object actorIdForLog() {
        UUID id = SecurityUtils.currentUserIdOrNull();
        return id != null ? id : "system";
    }

    // ─── Email availability lookup (SUPERADMIN only) ──────────────────────────

    /** Reports what currently holds {@code rawEmail}, so a create-admin conflict is actionable. */
    public ResponseEntity<ApiResponse<AdminEmailLookupResponse>> lookupEmail(String rawEmail) {
        String email = normaliseEmail(rawEmail);
        if (email == null) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is required");
        }
        return ApiResponse.success(lookupEmailHolder(email, false), "Email lookup complete");
    }

    /**
     * Resolves the account holding {@code email}, optionally healing stale rows.
     *
     * <p>Matching is case-insensitive and spans every provider. The old check compared
     * {@code email = ?} against provider {@code LOCAL} only, which both missed social sign-ups and
     * — because {@code AuthService} stores the address as typed while this service lower-cases it —
     * disagreed with itself on mixed-case addresses.
     *
     * @param heal when true, credentials whose {@code users} row no longer exists are retired so the
     *             address becomes usable again. {@code auth_credentials.user_id} has no foreign key,
     *             so such orphans survive user deletion and would otherwise block the email forever.
     */
    private AdminEmailLookupResponse lookupEmailHolder(String email, boolean heal) {
        return lookupEmailHolder(email, heal, null);
    }

    /**
     * @param excludeUserId account whose own credentials do not count as a clash — set when editing an
     *                      existing admin, which would otherwise always collide with itself.
     */
    private AdminEmailLookupResponse lookupEmailHolder(String email, boolean heal, UUID excludeUserId) {
        AdminEmailLookupResponse result = new AdminEmailLookupResponse();
        result.setEmail(email);

        List<AuthCredentials> credentials = authCredentialRepository.findAllByEmailIgnoreCase(email).stream()
                .filter(c -> excludeUserId == null || !excludeUserId.equals(c.getUserId()))
                .collect(Collectors.toList());

        AuthCredentials blocking = null;
        Users blockingUser = null;
        for (AuthCredentials c : credentials) {
            Users owner = c.getUserId() == null ? null : userRepository.findById(c.getUserId()).orElse(null);
            if (owner == null) {
                if (heal) {
                    // Tombstone rather than delete: carts, favorites and refresh tokens hold real
                    // foreign keys to auth_credentials, so a DELETE here could fail and roll back the
                    // whole admin creation. Renaming frees the address and deactivating stops the row
                    // shadowing the new account at sign-in — the same treatment the account-deletion
                    // purge applies (UserAccountService).
                    log.warn("Retiring orphaned credential {} for {} — user {} no longer exists",
                            c.getId(), email, c.getUserId());
                    c.setIsActive(false);
                    c.setEmail("orphaned+" + c.getId() + "@deleted.buyology.local");
                    authCredentialRepository.save(c);
                }
                continue;
            }
            // Several rows can share one address (no unique index on email). Report the live one —
            // a soft-deleted account is a weaker explanation than an active one holding the same email.
            boolean preferable = blocking == null || (isRetired(blockingUser) && !isRetired(owner));
            if (preferable) {
                blocking = c;
                blockingUser = owner;
            }
        }

        if (blocking == null) {
            result.setAvailable(true);
            result.setReason("This email is available.");
            return result;
        }

        List<String> roleNames = userRoleRepository.findRoleNamesByUserId(blockingUser.getId());
        boolean isAdmin = blockingUser.getUserType() == Users.UserType.ADMIN;
        boolean retired = isRetired(blockingUser);
        String who = displayName(blockingUser, blocking.getEmail());
        String joined = blockingUser.getCreatedAt() != null ? JOINED_ON.format(blockingUser.getCreatedAt()) : null;

        result.setAvailable(false);
        result.setUserId(blockingUser.getId());
        result.setAuthCredentialId(blocking.getId());
        result.setFirstName(blockingUser.getFirstName());
        result.setLastName(blockingUser.getLastName());
        result.setUserType(blockingUser.getUserType() != null ? blockingUser.getUserType().name() : null);
        result.setStatus(blockingUser.getStatus());
        result.setProvider(blocking.getProvider());
        result.setJoinedAt(blockingUser.getCreatedAt());
        result.setRoles(roleNames);
        result.setPromotable(!isAdmin && !retired);

        if (retired) {
            result.setReason("This email belongs to " + who + ", an account scheduled for deletion ("
                    + blockingUser.getStatus() + "). Use a different address until the deletion completes.");
        } else if (isAdmin) {
            result.setReason("This email already belongs to the admin account " + who
                    + (roleNames.isEmpty() ? " (no roles assigned)" : " (" + String.join(", ", roleNames) + ")")
                    + ". Open it from the Admins list to change roles or reset access.");
        } else {
            result.setReason("This email belongs to a customer account, " + who
                    + (joined != null ? ", registered " + joined : "")
                    + ". Promote that account to admin, or use a different address.");
        }
        return result;
    }

    /** Accounts in the soft-delete grace window or already purged. */
    private static boolean isRetired(Users user) {
        String status = user == null ? null : user.getStatus();
        return user != null && (user.getDeletedAt() != null
                || "PENDING_DELETION".equalsIgnoreCase(status)
                || "DELETED".equalsIgnoreCase(status));
    }

    private static String displayName(Users user, String email) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName().trim()) + " "
                + (user.getLastName() == null ? "" : user.getLastName().trim())).trim();
        if (!name.isEmpty()) return name;
        return email != null && !email.isBlank() ? email : "an existing account";
    }

    private static String normaliseEmail(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Users currentActor() {
        UUID actorId = SecurityUtils.currentUserId();
        return actorId != null ? userRepository.findById(actorId).orElse(null) : null;
    }

    // ─── List admin users (paginated, server-side filtered) ───────────────────

    /**
     * Admin accounts only, filtered in the database.
     *
     * <p>Replaces the dashboard's old approach of pulling the newest 200 users of any type and
     * filtering client-side, which quietly dropped every admin once the store had more than 200
     * registered users — the reason admins could "already exist" yet be missing from the list.
     */
    public ResponseEntity<ApiResponse<AdminUserListResponse>> listAdmins(int page, int size, String search) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Users> adminsPage = (search == null || search.isBlank())
                ? userRepository.findByUserType(Users.UserType.ADMIN, pageable)
                : userRepository.searchUsersByType(Users.UserType.ADMIN, search.trim(), pageable);

        List<AdminUserSummaryResponse> summaries = toSummaries(adminsPage.getContent());

        // Attach each admin's roles so the list can show what they can actually do.
        for (AdminUserSummaryResponse summary : summaries) {
            summary.setRoles(summary.getUserId() == null
                    ? List.of()
                    : userRoleRepository.findRoleNamesByUserId(summary.getUserId()));
        }

        AdminUserListResponse response = new AdminUserListResponse(
                page, size,
                adminsPage.getTotalElements(),
                adminsPage.getTotalPages(),
                summaries
        );

        return ApiResponse.success(response, "Admins retrieved");
    }

    // ─── List all users (paginated) ───────────────────────────────────────────

    public ResponseEntity<ApiResponse<AdminUserListResponse>> listUsers(int page, int size, String search) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Users> usersPage = (search == null || search.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.searchUsers(search.trim(), pageable);

        List<AdminUserSummaryResponse> summaries = toSummaries(usersPage.getContent());

        AdminUserListResponse response = new AdminUserListResponse(
                page, size,
                usersPage.getTotalElements(),
                usersPage.getTotalPages(),
                summaries
        );

        return ApiResponse.success(response, "Users retrieved");
    }

    // ─── Get full user detail by authCredentialId ─────────────────────────────

    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(UUID authCredentialId) {
        AuthCredentials credential = authCredentialRepository.findById(authCredentialId).orElse(null);
        if (credential == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Auth credential not found");
        }

        Users user = userRepository.findById(credential.getUserId()).orElse(null);
        if (user == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "User not found");
        }

        UserProfiles profile = profilesRepository.findByUser(user).orElse(null);

        String email = authCredentialRepository.findByUserId(user.getId()).stream()
                .map(AuthCredentials::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .findFirst()
                .orElse(null);

        // Favorites
        List<Favorite> favorites = favoriteRepository.findByAuthCredential_Id(authCredentialId);
        List<FavoriteItemResponse> favoriteItems = favorites.stream()
                .map(f -> new FavoriteItemResponse(
                        f.getId(),
                        f.getProduct().getId(),
                        f.getProduct().getSku(),
                        f.getCreatedAt()))
                .collect(Collectors.toList());
        FavoriteListResponse favoriteListResponse = new FavoriteListResponse(authCredentialId, favoriteItems);

        // Active cart
        Cart activeCart = cartRepository
                .findFirstByAuthCredentialIdAndStatusOrderByUpdatedAtDesc(authCredentialId, Cart.CartStatus.ACTIVE)
                .orElse(null);
        CartResponse cartResponse = activeCart != null ? buildCartResponse(activeCart) : null;

        // Account name/phone are often empty (email/password signups never capture a name; a phone is
        // only stored after SMS verification). Fall back to the customer's saved address, which carries
        // both — so admins always see who the customer is.
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        String phone = profile != null ? profile.getPhoneNumber() : null;
        if ((isBlank(firstName) && isBlank(lastName)) || isBlank(phone)) {
            UserAddress addr = bestAddress(user);
            if (addr != null) {
                if (isBlank(firstName) && isBlank(lastName)) {
                    firstName = addr.getFirstName();
                    lastName = addr.getLastName();
                }
                if (isBlank(phone)) phone = addr.getPhoneNumber();
            }
        }

        AdminUserDetailResponse detail = new AdminUserDetailResponse();
        detail.setUserId(user.getId());
        detail.setAuthCredentialId(authCredentialId);
        detail.setEmail(email);
        detail.setUserType(user.getUserType() != null ? user.getUserType().name() : null);
        detail.setStatus(user.getStatus());
        detail.setJoinedAt(user.getCreatedAt());
        detail.setFirstName(firstName);
        detail.setLastName(lastName);
        detail.setPhoneNumber(phone);
        if (profile != null) {
            detail.setDateOfBirth(profile.getDateOfBirth());
            detail.setAvatarUrl(profile.getAvatarUrl());
        }
        detail.setRegistrationIp(user.getRegistrationIp());
        detail.setRegistrationDevice(user.getRegistrationDevice());
        detail.setFavorites(favoriteListResponse);
        detail.setActiveCart(cartResponse);

        return ApiResponse.success(detail, "User detail retrieved");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Maps a page of users to summaries, batch-loading email + representative credential id and a
     * fallback name from each user's saved address. Email/password signups never capture a name on the
     * Users row, but the address they entered at checkout carries one — so the admin list can still show
     * who the customer is. Batched (three queries total) to avoid an N+1 across list rows.
     */
    private List<AdminUserSummaryResponse> toSummaries(List<Users> users) {
        List<UUID> userIds = users.stream()
                .map(Users::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, String> emailByUserId = new HashMap<>();
        Map<UUID, UUID> credIdByUserId = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (AuthCredentials c : authCredentialRepository.findByUserIdIn(userIds)) {
                credIdByUserId.putIfAbsent(c.getUserId(), c.getId());
                String e = c.getEmail();
                if (e != null && !e.isBlank()) emailByUserId.putIfAbsent(c.getUserId(), e);
            }
        }

        // Best saved address per user (default preferred) — for the name fallback.
        Map<UUID, UserAddress> addressByUserId = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (UserAddress a : userAddressRepository.findByUserIds(userIds)) {
                UUID uid = a.getUser() != null ? a.getUser().getId() : null;
                if (uid == null) continue;
                UserAddress existing = addressByUserId.get(uid);
                if (existing == null || (a.isDefault() && !existing.isDefault())) {
                    addressByUserId.put(uid, a);
                }
            }
        }

        List<AdminUserSummaryResponse> summaries = new ArrayList<>();
        for (Users user : users) {
            UUID uid = user.getId();
            String firstName = user.getFirstName();
            String lastName = user.getLastName();
            if (isBlank(firstName) && isBlank(lastName)) {
                UserAddress addr = addressByUserId.get(uid);
                if (addr != null) {
                    firstName = addr.getFirstName();
                    lastName = addr.getLastName();
                }
            }
            summaries.add(new AdminUserSummaryResponse(
                    uid,
                    credIdByUserId.get(uid),
                    emailByUserId.get(uid),
                    firstName,
                    lastName,
                    user.getUserType() != null ? user.getUserType().name() : null,
                    user.getStatus(),
                    user.getCreatedAt()
            ));
        }
        return summaries;
    }

    /** Best saved address for a single user (default preferred, else any) — for name/phone fallback. */
    private UserAddress bestAddress(Users user) {
        return userAddressRepository.findByUserAndIsDefaultTrue(user)
                .orElseGet(() -> userAddressRepository.findAllByUser(user).stream().findFirst().orElse(null));
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private CartResponse buildCartResponse(Cart cart) {
        CartResponse response = new CartResponse();
        response.setId(cart.getId());
        response.setAuthCredentialId(cart.getAuthCredential().getId());
        response.setStatus(cart.getStatus().name());
        response.setTotalPrice(cart.getTotalPrice());
        response.setCreatedAt(cart.getCreatedAt());
        response.setUpdatedAt(cart.getUpdatedAt());

        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        List<CartItemResponse> itemResponses = new ArrayList<>();
        for (CartItem item : items) {
            CartItemResponse itemResponse = new CartItemResponse();
            itemResponse.setId(item.getId());
            itemResponse.setProductId(item.getProduct().getId());
            itemResponse.setProductSku(item.getProduct().getSku());
            if (item.getVariant() != null) {
                itemResponse.setVariantId(item.getVariant().getId());
                itemResponse.setVariantSku(item.getVariant().getSku());
            }
            itemResponse.setQuantity(item.getQuantity());
            itemResponse.setUnitPrice(item.getUnitPrice());
            itemResponse.setTotalPrice(item.getTotalPrice());
            itemResponse.setCreatedAt(item.getCreatedAt());
            itemResponse.setUpdatedAt(item.getUpdatedAt());

            List<CartItemSpecSelectionResponse> specResponses = specSelectionRepository
                    .findByCartItemId(item.getId()).stream()
                    .map(sel -> {
                        CartItemSpecSelectionResponse specResp = new CartItemSpecSelectionResponse();
                        specResp.setSpecOptionId(sel.getSpecOption().getId());
                        specResp.setGroupCode(sel.getSpecOption().getGroup().getCode());
                        specResp.setValue(sel.getSpecOption().getValue());
                        if (sel.getSpecOption().getUnit() != null) {
                            specResp.setUnit(sel.getSpecOption().getUnit().name());
                        }
                        specResp.setColorCode(sel.getSpecOption().getColorCode());
                        return specResp;
                    })
                    .collect(Collectors.toList());
            itemResponse.setSelectedSpecs(specResponses);
            itemResponses.add(itemResponse);
        }

        response.setItems(itemResponses);
        return response;
    }
}
