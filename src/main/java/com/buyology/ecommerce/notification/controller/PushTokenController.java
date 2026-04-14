package com.buyology.ecommerce.notification.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.notification.domain.UserPushToken;
import com.buyology.ecommerce.notification.domain.UserPushToken.DeviceType;
import com.buyology.ecommerce.notification.repository.UserPushTokenRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Allows customer devices to register their FCM token so the backend
 * can send push notifications for delivery status updates.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class PushTokenController {

    private final UserPushTokenRepository pushTokenRepository;

    public PushTokenController(UserPushTokenRepository pushTokenRepository) {
        this.pushTokenRepository = pushTokenRepository;
    }

    /**
     * Register or update the FCM push token for the authenticated customer's device.
     * One token is kept per user per device type — calling this again updates the token.
     *
     * POST /api/v1/notifications/register-token
     */
    @PostMapping("/register-token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> registerToken(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody RegisterTokenRequest request) {

        pushTokenRepository.findByUserIdAndDeviceType(userId, request.deviceType())
                .ifPresentOrElse(
                        existing -> {
                            existing.setFcmToken(request.fcmToken());
                            pushTokenRepository.save(existing);
                        },
                        () -> {
                            UserPushToken token = new UserPushToken();
                            token.setUserId(userId);
                            token.setFcmToken(request.fcmToken());
                            token.setDeviceType(request.deviceType());
                            pushTokenRepository.save(token);
                        }
                );

        return ApiResponse.success(null, "Push token registered");
    }

    /**
     * Remove the FCM token for the authenticated user's device (e.g. on logout).
     *
     * DELETE /api/v1/notifications/register-token
     */
    @DeleteMapping("/register-token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> removeToken(
            @AuthenticationPrincipal UUID userId,
            @RequestParam DeviceType deviceType) {

        pushTokenRepository.findByUserIdAndDeviceType(userId, deviceType)
                .ifPresent(pushTokenRepository::delete);

        return ApiResponse.success(null, "Push token removed");
    }

    public record RegisterTokenRequest(
            @NotBlank String fcmToken,
            @NotNull DeviceType deviceType
    ) {}
}
