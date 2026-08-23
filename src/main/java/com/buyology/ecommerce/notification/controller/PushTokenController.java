package com.buyology.ecommerce.notification.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.notification.domain.NotificationHistory;
import com.buyology.ecommerce.notification.domain.UserPushToken;
import com.buyology.ecommerce.notification.domain.UserPushToken.DeviceType;
import com.buyology.ecommerce.notification.repository.NotificationHistoryRepository;
import com.buyology.ecommerce.notification.repository.UserPushTokenRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Allows customer devices to register their FCM token and retrieve notification history.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class PushTokenController {

    private final UserPushTokenRepository pushTokenRepository;
    private final NotificationHistoryRepository historyRepository;

    public PushTokenController(UserPushTokenRepository pushTokenRepository, NotificationHistoryRepository historyRepository) {
        this.pushTokenRepository = pushTokenRepository;
        this.historyRepository = historyRepository;
    }

    /**
     * Get the notification history for the authenticated user.
     */
    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<NotificationHistory>>> getHistory(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.success(historyRepository.findByUserIdOrderByCreatedAtDesc(userId), "Notification history retrieved");
    }

    /**
     * Mark every notification in the caller's history as read.
     */
    @PutMapping("/history/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Integer>> markAllRead(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.success(historyRepository.markAllRead(userId), "All notifications marked read");
    }

    /**
     * Mark a notification as read.
     */
    @PutMapping("/history/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        historyRepository.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                n.setRead(true);
                historyRepository.save(n);
            }
        });
        return ApiResponse.success(null, "Notification marked as read");
    }

    /**
     * Delete a notification from the authenticated user's history.
     */
    @DeleteMapping("/history/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        historyRepository.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(userId)) {
                historyRepository.delete(n);
            }
        });
        return ApiResponse.success(null, "Notification deleted");
    }

    /**
     * Get the count of unread notifications for the authenticated user.
     */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.success(historyRepository.countByUserIdAndIsReadFalse(userId), "Unread count retrieved");
    }

    /**
     * Register or update the FCM push token for the authenticated customer's device.
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
