package com.buyology.ecommerce.favorite.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.favorite.dto.FavoriteItemResponse;
import com.buyology.ecommerce.favorite.dto.FavoriteListResponse;
import com.buyology.ecommerce.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/favorites")
@Tag(name = "Favorites", description = "User favorites management")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @Operation(summary = "Get all favorites for a user")
    @GetMapping("/{authCredentialId}")
    public ResponseEntity<ApiResponse<FavoriteListResponse>> getFavorites(
            @PathVariable UUID authCredentialId) {
        return favoriteService.getFavorites(authCredentialId);
    }

    @Operation(summary = "Add a product to favorites")
    @PostMapping("/{authCredentialId}/products/{productId}")
    public ResponseEntity<ApiResponse<FavoriteItemResponse>> addFavorite(
            @PathVariable UUID authCredentialId,
            @PathVariable UUID productId) {
        return favoriteService.addFavorite(authCredentialId, productId);
    }

    @Operation(summary = "Remove a product from favorites")
    @DeleteMapping("/{authCredentialId}/products/{productId}")
    public ResponseEntity<ApiResponse<Void>> removeFavorite(
            @PathVariable UUID authCredentialId,
            @PathVariable UUID productId) {
        return favoriteService.removeFavorite(authCredentialId, productId);
    }

    @Operation(summary = "Check if a product is in the user's favorites")
    @GetMapping("/{authCredentialId}/products/{productId}/check")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkFavorite(
            @PathVariable UUID authCredentialId,
            @PathVariable UUID productId) {
        return favoriteService.checkFavorite(authCredentialId, productId);
    }
}
