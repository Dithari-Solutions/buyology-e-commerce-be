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
 * <p>Set {@code firebase.enabled=true} and supply the service-account JSON
 * path via {@code firebase.service-account-key} to activate.
 * When disabled (default) the {@link FirebaseMessaging} bean is null and
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
            log.info("[Firebase] FCM disabled (firebase.enabled=false) — push notifications skipped");
            return null;
        }

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccountKey.getInputStream()))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("[Firebase] FirebaseApp initialised successfully");
        }

        return FirebaseMessaging.getInstance();
    }
}
