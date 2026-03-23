package com.buyology.ecommerce.favorite.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.response.ApiResponse;
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

    public FavoriteService(FavoriteRepository favoriteRepository,
                           AuthCredentialRepository authCredentialRepository,
                           ProductRepository productRepository) {
        this.favoriteRepository = favoriteRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.productRepository = productRepository;
    }

    // ─── Add product to favorites ─────────────────────────────────────────────

    @Transactional
    public ResponseEntity<ApiResponse<FavoriteItemResponse>> addFavorite(UUID authCredentialId, UUID productId) {
        AuthCredentials authCredential = authCredentialRepository.findById(authCredentialId).orElse(null);
        if (authCredential == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Auth credential not found");
        }

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
        if (!authCredentialRepository.existsById(authCredentialId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Auth credential not found");
        }

        if (!favoriteRepository.existsByAuthCredential_IdAndProduct_Id(authCredentialId, productId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Product is not in favorites");
        }

        favoriteRepository.deleteByAuthCredential_IdAndProduct_Id(authCredentialId, productId);
        return ApiResponse.success(null, "Product removed from favorites");
    }

    // ─── Get my favorites ─────────────────────────────────────────────────────

    public ResponseEntity<ApiResponse<FavoriteListResponse>> getFavorites(UUID authCredentialId) {
        if (!authCredentialRepository.existsById(authCredentialId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Auth credential not found");
        }

        List<Favorite> favorites = favoriteRepository.findByAuthCredential_Id(authCredentialId);
        List<FavoriteItemResponse> items = favorites.stream()
                .map(this::toItemResponse)
                .collect(Collectors.toList());

        return ApiResponse.success(new FavoriteListResponse(authCredentialId, items), "Favorites retrieved");
    }

    // ─── Check if product is favorited ────────────────────────────────────────

    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkFavorite(UUID authCredentialId, UUID productId) {
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
