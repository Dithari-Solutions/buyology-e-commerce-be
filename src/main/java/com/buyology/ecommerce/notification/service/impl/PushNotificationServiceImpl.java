package com.buyology.ecommerce.notification.service.impl;

import com.buyology.ecommerce.notification.domain.UserPushToken;
import com.buyology.ecommerce.notification.repository.UserPushTokenRepository;
import com.buyology.ecommerce.notification.service.PushNotificationService;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PushNotificationServiceImpl implements PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationServiceImpl.class);

    private final UserPushTokenRepository pushTokenRepository;

    /** Null when firebase.enabled=false — all sends are skipped gracefully. */
    @Autowired(required = false)
    private FirebaseMessaging firebaseMessaging;

    public PushNotificationServiceImpl(UserPushTokenRepository pushTokenRepository) {
        this.pushTokenRepository = pushTokenRepository;
    }

    @Override
    @Async
    @Transactional
    public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        if (firebaseMessaging == null) {
            log.debug("[Push] Firebase disabled — skipping push for userId={}", userId);
            return;
        }

        List<UserPushToken> tokens = pushTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.debug("[Push] No FCM tokens registered for userId={}", userId);
            return;
        }

        List<String> fcmTokens = tokens.stream().map(UserPushToken::getFcmToken).toList();

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
            log.info("[Push] Sent to userId={} tokens={} success={} failure={}",
                    userId, fcmTokens.size(),
                    batchResponse.getSuccessCount(), batchResponse.getFailureCount());

            // Remove stale tokens that are no longer registered on the device
            List<SendResponse> responses = batchResponse.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                SendResponse resp = responses.get(i);
                if (!resp.isSuccessful()) {
                    FirebaseMessagingException ex = resp.getException();
                    if (ex != null && MessagingErrorCode.UNREGISTERED.equals(ex.getMessagingErrorCode())) {
                        String staleToken = fcmTokens.get(i);
                        log.info("[Push] Removing stale FCM token for userId={}", userId);
                        pushTokenRepository.deleteByFcmToken(staleToken);
                    }
                }
            }
        } catch (FirebaseMessagingException ex) {
            log.error("[Push] Multicast failed for userId={} — {} {}", userId,
                    ex.getErrorCode(), ex.getMessage());
        }
    }
}
