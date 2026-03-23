package com.buyology.ecommerce.favorite.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.favorite.dto.AdminFavoritePageResponse;
import com.buyology.ecommerce.favorite.dto.FavoriteListResponse;
import com.buyology.ecommerce.favorite.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/favorites")
@Tag(name = "Admin – Favorites", description = "Admin access to user favorites")
public class AdminFavoriteController {

    private final FavoriteService favoriteService;

    public AdminFavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @Operation(summary = "List all favorites across all users (paginated)")
    @GetMapping
    public ResponseEntity<ApiResponse<AdminFavoritePageResponse>> getAllFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return favoriteService.getAllFavorites(page, size);
    }

    @Operation(summary = "Get favorites for a specific user")
    @GetMapping("/users/{authCredentialId}")
    public ResponseEntity<ApiResponse<FavoriteListResponse>> getUserFavorites(
            @PathVariable UUID authCredentialId) {
        return favoriteService.getUserFavorites(authCredentialId);
    }
}
