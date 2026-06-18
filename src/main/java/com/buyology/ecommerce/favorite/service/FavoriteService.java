package com.buyology.ecommerce.favorite.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.favorite.domain.Favorite;
import com.buyology.ecommerce.favorite.dto.AdminFavoriteEntryResponse;
import com.buyology.ecommerce.favorite.dto.AdminFavoritePageResponse;
import com.buyology.ecommerce.favorite.dto.FavoriteItemResponse;
import com.buyology.ecommerce.favorite.dto.FavoriteListResponse;
import com.buyology.ecommerce.favorite.repository.FavoriteRepository;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final ProductRepository productRepository;
    private final com.buyology.ecommerce.user.service.AccountStatusValidator accountStatusValidator;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           AuthCredentialRepository authCredentialRepository,
                           ProductRepository productRepository,
                           com.buyology.ecommerce.user.service.AccountStatusValidator accountStatusValidator) {
        this.favoriteRepository = favoriteRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.productRepository = productRepository;
        this.accountStatusValidator = accountStatusValidator;
    }

    // ─── Add product to favorites ─────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<FavoriteItemResponse>> addFavorite(UUID authCredentialId, UUID productId) {
        AuthCredentials authCredential = requireOwnedCredential(authCredentialId);
        // Block favouriting for accounts pending deletion — they must recover their account first.
        accountStatusValidator.requireActiveAccount(authCredential.getUserId());

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || "DELETED".equals(product.getStatus())) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product not found");
        }

        if (favoriteRepository.existsByAuthCredential_IdAndProduct_Id(authCredentialId, productId)) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Product is already in favorites");
        }

        Favorite favorite = favoriteRepository.save(new Favorite(authCredential, product));
        return ApiResponse.created(toItemResponse(favorite), "Product added to favorites");
    }

    // ─── Remove product from favorites ───────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<Void>> removeFavorite(UUID authCredentialId, UUID productId) {
        requireOwnedCredential(authCredentialId);

        if (!favoriteRepository.existsByAuthCredential_IdAndProduct_Id(authCredentialId, productId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product is not in favorites");
        }

        favoriteRepository.deleteByAuthCredential_IdAndProduct_Id(authCredentialId, productId);
        return ApiResponse.success(null, "Product removed from favorites");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> clearFavorites(UUID authCredentialId) {
        requireOwnedCredential(authCredentialId);
        favoriteRepository.deleteByAuthCredential_Id(authCredentialId);
        return ApiResponse.success(null, "All favorites cleared");
    }

    // ─── Get my favorites ─────────────────────────────────────────────────────

    public ResponseEntity<ApiResponse<FavoriteListResponse>> getFavorites(UUID authCredentialId) {
        requireOwnedCredential(authCredentialId);

        List<Favorite> favorites = favoriteRepository.findByAuthCredential_Id(authCredentialId);
        List<FavoriteItemResponse> items = favorites.stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());

        return ApiResponse.success(new FavoriteListResponse(authCredentialId, items), "Favorites retrieved");
    }

    // ─── Check if product is favorited ────────────────────────────────────────

    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkFavorite(UUID authCredentialId, UUID productId) {
        requireOwnedCredential(authCredentialId);
        boolean favorited = favoriteRepository.existsByAuthCredential_IdAndProduct_Id(authCredentialId, productId);
        return ApiResponse.success(Map.of("favorited", favorited), "Check complete");
    }

    // ─── Admin: paginated list of all favorites ───────────────────────────────

    public ResponseEntity<ApiResponse<AdminFavoritePageResponse>> getAllFavorites(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Favorite> favoritePage = favoriteRepository.findAllWithDetails(pageable);

        List<AdminFavoriteEntryResponse> items = favoritePage.getContent().stream()
                .map(this::toAdminEntry)
                .collect(Collectors.toList());

        AdminFavoritePageResponse response = new AdminFavoritePageResponse(
                page, size,
                favoritePage.getTotalElements(),
                favoritePage.getTotalPages(),
                items
        );

        return ApiResponse.success(response, "Favorites retrieved");
    }

    // ─── Admin: get favorites for a specific user ─────────────────────────────

    public ResponseEntity<ApiResponse<FavoriteListResponse>> getUserFavorites(UUID authCredentialId) {
        if (!authCredentialRepository.existsById(authCredentialId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Auth credential not found");
        }

        List<Favorite> favorites = favoriteRepository.findByAuthCredential_Id(authCredentialId);
        List<FavoriteItemResponse> items = favorites.stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());

        return ApiResponse.success(new FavoriteListResponse(authCredentialId, items), "User favorites retrieved");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Loads the AuthCredentials and asserts the authenticated principal (Users.id) owns it.
     * The path-supplied authCredentialId is NOT trusted as identity — ownership is verified
     * against the JWT principal. Throws IllegalArgumentException (→400/404) if absent,
     * AccessDeniedException (→403) if the caller is not the owner.
     */
    private AuthCredentials requireOwnedCredential(UUID authCredentialId) {
        AuthCredentials credential = authCredentialRepository.findById(authCredentialId)
                .orElseThrow(() -> new IllegalArgumentException("Auth credential not found"));
        SecurityUtils.requireSelf(credential.getUserId());
        return credential;
    }

    private FavoriteItemResponse toItemResponse(Favorite favorite) {
        return new FavoriteItemResponse(
                favorite.getId(),
                favorite.getProduct().getId(),
                favorite.getProduct().getSku(),
                favorite.getCreatedAt()
        );
    }

    private AdminFavoriteEntryResponse toAdminEntry(Favorite favorite) {
        return new AdminFavoriteEntryResponse(
                favorite.getId(),
                favorite.getAuthCredential().getId(),
                favorite.getAuthCredential().getEmail(),
                favorite.getProduct().getId(),
                favorite.getProduct().getSku(),
                favorite.getCreatedAt()
        );
    }
}
