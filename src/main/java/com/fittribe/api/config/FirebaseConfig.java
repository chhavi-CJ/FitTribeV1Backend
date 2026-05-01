package com.fittribe.api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.service-account-path:}")
    private String serviceAccountPath;

    @Value("${firebase.service-account-json:}")
    private String serviceAccountJson;

    @PostConstruct
    public void init() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                log.info("FirebaseApp already initialized; skipping");
                return;
            }

            GoogleCredentials credentials;

            if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
                log.info("Initializing Firebase Admin SDK from FIREBASE_SERVICE_ACCOUNT_JSON env var");
                InputStream stream = new ByteArrayInputStream(serviceAccountJson.getBytes());
                credentials = GoogleCredentials.fromStream(stream);
            } else if (serviceAccountPath != null && !serviceAccountPath.isBlank()) {
                log.info("Initializing Firebase Admin SDK from file: {}", serviceAccountPath);
                credentials = GoogleCredentials.fromStream(new FileInputStream(serviceAccountPath));
            } else {
                log.warn("Firebase Admin SDK NOT initialized — neither FIREBASE_SERVICE_ACCOUNT_PATH " +
                         "nor FIREBASE_SERVICE_ACCOUNT_JSON is set.");
                log.warn("Phone OTP mock auth still works. Google/Apple/email verification will return 503.");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK", e);
        }
    }
}
