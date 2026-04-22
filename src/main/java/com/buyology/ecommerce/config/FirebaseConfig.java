package com.buyology.ecommerce.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Initialises the Firebase Admin SDK so the ecommerce backend can send
 * FCM push notifications to customer devices.
 *
 * <p>Two ways to supply credentials (checked in order):
 * <ol>
 *   <li>{@code firebase.service-account-json} — raw JSON string from the
 *       {@code FIREBASE_SERVICE_ACCOUNT_KEY} env var (preferred for Docker).</li>
 *   <li>{@code firebase.service-account-key} — file/classpath resource path.</li>
 * </ol>
 * Set {@code firebase.enabled=true} to activate. When disabled the
 * {@link FirebaseMessaging} bean is null and push-sending code skips gracefully.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.enabled:false}")
    private boolean firebaseEnabled;

    /** Raw service-account JSON injected from FIREBASE_SERVICE_ACCOUNT_KEY env var. */
    @Value("${firebase.service-account-json:}")
    private String serviceAccountJson;

    @Value("${firebase.service-account-key:classpath:firebase-service-account.json}")
    private Resource serviceAccountKey;

    @Bean
    public FirebaseMessaging firebaseMessaging() throws IOException {
        if (!firebaseEnabled) {
            log.info("[Firebase] FCM disabled (firebase.enabled=false) — push notifications skipped");
            return null;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            GoogleCredentials credentials;
            if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
                credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
                log.info("[Firebase] Loaded credentials from FIREBASE_SERVICE_ACCOUNT_KEY env var");
            } else {
                credentials = GoogleCredentials.fromStream(serviceAccountKey.getInputStream());
                log.info("[Firebase] Loaded credentials from file: {}", serviceAccountKey);
            }
            FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build());
            log.info("[Firebase] FirebaseApp initialised successfully");
        }

        return FirebaseMessaging.getInstance();
    }
}
