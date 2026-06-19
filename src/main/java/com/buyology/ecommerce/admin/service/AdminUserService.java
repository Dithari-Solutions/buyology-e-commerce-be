package com.buyology.ecommerce.admin.service;

import com.buyology.ecommerce.admin.dto.AdminUserDetailResponse;
import com.buyology.ecommerce.admin.dto.AdminUserListResponse;
import com.buyology.ecommerce.admin.dto.AdminUserSummaryResponse;
import com.buyology.ecommerce.admin.dto.CreateAdminRequest;
import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
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
import com.buyology.ecommerce.user.domain.UserProfiles;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserProfilesRepository;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserProfilesRepository profilesRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final FavoriteRepository favoriteRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CartItemSpecSelectionRepository specSelectionRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public AdminUserService(UserRepository userRepository,
                            UserProfilesRepository profilesRepository,
                            AuthCredentialRepository authCredentialRepository,
                            FavoriteRepository favoriteRepository,
                            CartRepository cartRepository,
                            CartItemRepository cartItemRepository,
                            CartItemSpecSelectionRepository specSelectionRepository,
                            RoleRepository roleRepository,
                            UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.profilesRepository = profilesRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.favoriteRepository = favoriteRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.specSelectionRepository = specSelectionRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    // ─── Create an admin user with roles (SUPERADMIN only) ────────────────────

    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> createAdmin(CreateAdminRequest req) {
        String email = req.getEmail() == null ? null : req.getEmail().trim().toLowerCase();
        if (email == null || email.isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Email is required");
        }
        if (authCredentialRepository.findByEmailAndProvider(email, "LOCAL").isPresent()) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "An account with this email already exists");
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

        Users assignedBy = SecurityUtils.currentUserId() != null
                ? userRepository.findById(SecurityUtils.currentUserId()).orElse(null)
                : null;
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

    // ─── List all users (paginated) ───────────────────────────────────────────

    public ResponseEntity<ApiResponse<AdminUserListResponse>> listUsers(int page, int size, String search) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Users> usersPage = (search == null || search.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.searchUsers(search.trim(), pageable);

        List<AdminUserSummaryResponse> summaries = usersPage.getContent().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());

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

        AdminUserDetailResponse detail = new AdminUserDetailResponse();
        detail.setUserId(user.getId());
        detail.setAuthCredentialId(authCredentialId);
        detail.setEmail(email);
        detail.setUserType(user.getUserType() != null ? user.getUserType().name() : null);
        detail.setStatus(user.getStatus());
        detail.setJoinedAt(user.getCreatedAt());
        detail.setFirstName(user.getFirstName());
        detail.setLastName(user.getLastName());
        if (profile != null) {
            detail.setPhoneNumber(profile.getPhoneNumber());
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

    private AdminUserSummaryResponse toSummary(Users user) {
        String email = authCredentialRepository.findByUserId(user.getId()).stream()
                .map(AuthCredentials::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .findFirst()
                .orElse(null);

        // Pick the first credential ID as the representative authCredentialId
        UUID authCredentialId = authCredentialRepository.findByUserId(user.getId()).stream()
                .map(AuthCredentials::getId)
                .findFirst()
                .orElse(null);

        return new AdminUserSummaryResponse(
                user.getId(),
                authCredentialId,
                email,
                user.getFirstName(),
                user.getLastName(),
                user.getUserType() != null ? user.getUserType().name() : null,
                user.getStatus(),
                user.getCreatedAt()
        );
    }

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
