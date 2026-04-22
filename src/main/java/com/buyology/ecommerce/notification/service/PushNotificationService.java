package com.buyology.ecommerce.notification.service;

import java.util.Map;
import java.util.UUID;

/**
 * Sends FCM push notifications to customer devices.
 * No-op when Firebase is disabled ({@code firebase.enabled=false}).
 */
public interface PushNotificationService {

    /**
     * Sends a push notification to all registered devices of the given user.
     *
     * @param userId    the customer's user ID
     * @param title     notification title shown in the system tray
     * @param body      notification body text
     * @param data      optional key-value data payload for the app to handle silently
     */
    void sendToUser(UUID userId, String title, String body, Map<String, String> data);

    /**
     * Sends a push notification and records it in the history with a specific type.
     */
    void sendToUser(UUID userId, String title, String body, String type, Map<String, String> data);
}
