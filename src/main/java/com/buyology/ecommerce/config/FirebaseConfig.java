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

import java.io.IOException;

/**
 * Initialises the Firebase Admin SDK so the ecommerce backend can send
 * FCM push notifications to customer devices.
 *
 * <p>In production, the service-account JSON is written to
 * {@code /firebase/service-account.json} by the deploy script (base64-decoded
 * from the {@code FIREBASE_SERVICE_ACCOUNT_KEY} GitHub secret) and mounted
 * read-only into the container. {@code firebase.service-account-key} is set to
 * {@code file:/firebase/service-account.json} via the {@code FIREBASE_KEY_PATH}
 * env var.
 *
 * <p>Set {@code firebase.enabled=true} (via {@code FIREBASE_ENABLED=true}) to
 * activate. When disabled the {@link FirebaseMessaging} bean is null and
 * all push-sending code skips gracefully.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.enabled:false}")
    private boolean firebaseEnabled;

    @Value("${firebase.service-account-key:classpath:firebase-service-account.json}")
    private Resource serviceAccountKey;

    @Bean
    public FirebaseMessaging firebaseMessaging() throws IOException {
        if (!firebaseEnabled) {
            log.info("[Firebase] FCM disabled — push notifications skipped");
            return null;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccountKey.getInputStream()))
                    .build());
            log.info("[Firebase] FirebaseApp initialised from {}", serviceAccountKey);
        }

        return FirebaseMessaging.getInstance();
    }
}
