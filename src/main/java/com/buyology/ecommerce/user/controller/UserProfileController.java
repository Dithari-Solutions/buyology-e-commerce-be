package com.buyology.ecommerce.user.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.user.dto.ProfileResponse;
import com.buyology.ecommerce.user.dto.UpdateProfileRequest;
import com.buyology.ecommerce.user.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/{userId}/profile")
public class UserProfileController {

    private final UserProfileService profileService;

    public UserProfileController(UserProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Get the user's profile.
     * The response includes paymentReady and missingFields so the frontend
     * can show a "complete your profile" banner before checkout.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
            @PathVariable UUID userId) {
        return ApiResponse.success(profileService.getProfile(userId), "Profile fetched successfully");
    }

    /**
     * Update profile details (firstName, lastName, phoneNumber, dateOfBirth).
     * All fields are optional — send only what changed.
     */
    @PatchMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(profileService.updateProfile(userId, request), "Profile updated successfully");
    }

    /**
     * Upload or replace the user's avatar image.
     * Accepts JPEG, PNG, WebP, GIF.
     */
    @PatchMapping(value = "/avatar", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateAvatar(
            @PathVariable UUID userId,
            @RequestPart("avatar") MultipartFile file) {
        return ApiResponse.success(profileService.updateAvatar(userId, file), "Avatar updated successfully");
    }
}
