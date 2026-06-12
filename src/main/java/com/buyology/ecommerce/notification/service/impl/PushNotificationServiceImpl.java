package com.buyology.ecommerce.notification.service.impl;

import com.buyology.ecommerce.notification.domain.NotificationHistory;
import com.buyology.ecommerce.notification.domain.UserPushToken;
import com.buyology.ecommerce.notification.repository.NotificationHistoryRepository;
import com.buyology.ecommerce.notification.repository.UserPushTokenRepository;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PushNotificationServiceImpl implements PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationServiceImpl.class);

    /** Expo Push API — delivers to iOS (APNs) and Android (FCM) without a backend Firebase SDK. */
    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final UserPushTokenRepository pushTokenRepository;
    private final NotificationHistoryRepository historyRepository;
    private final RestClient expoClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Null when firebase.enabled=false — legacy FCM sends are skipped gracefully. */
    @Autowired(required = false)
    private FirebaseMessaging firebaseMessaging;

    public PushNotificationServiceImpl(UserPushTokenRepository pushTokenRepository, NotificationHistoryRepository historyRepository) {
        this.pushTokenRepository = pushTokenRepository;
        this.historyRepository = historyRepository;
    }

    @Override
    @Async
    @Transactional
    public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        sendToUserInternal(userId, title, body, null, data);
    }

    @Override
    @Async
    @Transactional
    public void sendToUser(UUID userId, String title, String body, String type, Map<String, String> data) {
        sendToUserInternal(userId, title, body, type, data);
    }

    private void sendToUserInternal(UUID userId, String title, String body, String type, Map<String, String> data) {
        // Record in history (independent of delivery)
        try {
            NotificationHistory history = new NotificationHistory(userId, title, body, type);
            historyRepository.save(history);
        } catch (Exception e) {
            log.error("[Push] Failed to save notification history for userId={}: {}", userId, e.getMessage());
        }

        List<UserPushToken> tokens = pushTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.debug("[Push] No push tokens registered for userId={}", userId);
            return;
        }

        // Route each token by format: Expo push tokens go through Expo's API; anything else is
        // treated as a raw FCM token and sent via Firebase (skipped when Firebase is disabled).
        List<String> expoTokens = new ArrayList<>();
        List<String> fcmTokens = new ArrayList<>();
        for (UserPushToken t : tokens) {
            String tok = t.getFcmToken();
            if (isExpoToken(tok)) {
                expoTokens.add(tok);
            } else if (tok != null && !tok.isBlank()) {
                fcmTokens.add(tok);
            }
        }

        if (!expoTokens.isEmpty()) {
            sendViaExpo(userId, expoTokens, title, body, data);
        }
        if (!fcmTokens.isEmpty()) {
            sendViaFirebase(userId, fcmTokens, title, body, data);
        }
    }

    private static boolean isExpoToken(String token) {
        return token != null && (token.startsWith("ExponentPushToken[") || token.startsWith("ExpoPushToken["));
    }

    private void sendViaExpo(UUID userId, List<String> tokens, String title, String body, Map<String, String> data) {
        try {
            List<Map<String, Object>> messages = new ArrayList<>(tokens.size());
            for (String to : tokens) {
                Map<String, Object> m = new HashMap<>();
                m.put("to", to);
                m.put("title", title);
                m.put("body", body);
                m.put("sound", "default");
                if (data != null && !data.isEmpty()) {
                    m.put("data", data);
                }
                messages.add(m);
            }

            String responseBody = expoClient.post()
                    .uri(EXPO_PUSH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(messages)
                    .retrieve()
                    .body(String.class);

            log.info("[Push] Expo sent to userId={} tokens={}", userId, tokens.size());

            // Inspect per-message tickets; drop tokens Expo reports as no longer registered.
            if (responseBody != null) {
                JsonNode arr = objectMapper.readTree(responseBody).path("data");
                if (arr.isArray()) {
                    for (int i = 0; i < arr.size() && i < tokens.size(); i++) {
                        JsonNode item = arr.get(i);
                        if ("error".equals(item.path("status").asText())) {
                            String err = item.path("details").path("error").asText("");
                            log.warn("[Push] Expo ticket error for userId={}: {} ({})",
                                    userId, item.path("message").asText(), err);
                            if ("DeviceNotRegistered".equals(err)) {
                                pushTokenRepository.deleteByFcmToken(tokens.get(i));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Push] Expo send failed for userId={}: {}", userId, e.getMessage());
        }
    }

    private void sendViaFirebase(UUID userId, List<String> fcmTokens, String title, String body, Map<String, String> data) {
        if (firebaseMessaging == null) {
            log.debug("[Push] Firebase disabled — skipping {} legacy FCM token(s) for userId={}", fcmTokens.size(), userId);
            return;
        }

        MulticastMessage.Builder builder = MulticastMessage.builder()
                .addAllTokens(fcmTokens)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());

        if (data != null) {
            data.forEach(builder::putData);
        }

        try {
            BatchResponse batchResponse = firebaseMessaging.sendEachForMulticast(builder.build());
            log.info("[Push] FCM sent to userId={} tokens={} success={} failure={}",
                    userId, fcmTokens.size(),
                    batchResponse.getSuccessCount(), batchResponse.getFailureCount());

            List<SendResponse> responses = batchResponse.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                SendResponse resp = responses.get(i);
                if (!resp.isSuccessful()) {
                    FirebaseMessagingException ex = resp.getException();
                    if (ex != null && MessagingErrorCode.UNREGISTERED.equals(ex.getMessagingErrorCode())) {
                        log.info("[Push] Removing stale FCM token for userId={}", userId);
                        pushTokenRepository.deleteByFcmToken(fcmTokens.get(i));
                    }
                }
            }
        } catch (FirebaseMessagingException ex) {
            log.error("[Push] Multicast failed for userId={} — {} {}", userId,
                    ex.getErrorCode(), ex.getMessage());
        }
    }
}
