package com.buyology.ecommerce.admin.dto;

import com.buyology.ecommerce.cart.dto.CartResponse;
import com.buyology.ecommerce.favorite.dto.FavoriteListResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class AdminUserDetailResponse {

    // ── Identity ──────────────────────────────────────────────────────────────
    private UUID userId;
    private UUID authCredentialId;
    private String email;
    private String userType;
    private String status;
    private Instant joinedAt;

    // ── Profile ───────────────────────────────────────────────────────────────
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private String avatarUrl;

    // ── Favorites ─────────────────────────────────────────────────────────────
    private FavoriteListResponse favorites;

    // ── Active cart ───────────────────────────────────────────────────────────
    private CartResponse activeCart;

    public AdminUserDetailResponse() {
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getAuthCredentialId() { return authCredentialId; }
    public void setAuthCredentialId(UUID authCredentialId) { this.authCredentialId = authCredentialId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public FavoriteListResponse getFavorites() { return favorites; }
    public void setFavorites(FavoriteListResponse favorites) { this.favorites = favorites; }

    public CartResponse getActiveCart() { return activeCart; }
    public void setActiveCart(CartResponse activeCart) { this.activeCart = activeCart; }
}
