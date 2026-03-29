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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.project-id:placeholder}")
    private String projectId;

    @Value("${firebase.service-account-path:firebase-service-account.json}")
    private String serviceAccountPath;

    @PostConstruct
    public void initialize() {
        if ("placeholder".equals(projectId)) {
            log.warn("Firebase project-id is 'placeholder' — Firebase auth is DISABLED. " +
                     "Set FIREBASE_PROJECT_ID and FIREBASE_SA_PATH in production.");
            return;
        }

        if (!FirebaseApp.getApps().isEmpty()) {
            return; // already initialised (e.g. hot-reload)
        }

        try {
            GoogleCredentials credentials;
            String saJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
            File saFile = new File(serviceAccountPath);
            if (saJson != null && !saJson.isBlank()) {
                credentials = GoogleCredentials.fromStream(
                        new ByteArrayInputStream(saJson.getBytes(StandardCharsets.UTF_8)));
                log.info("Firebase: using service account from FIREBASE_SERVICE_ACCOUNT_JSON env var");
            } else if (saFile.exists()) {
                credentials = GoogleCredentials.fromStream(new FileInputStream(saFile));
                log.info("Firebase: using service account at {}", serviceAccountPath);
            } else {
                credentials = GoogleCredentials.getApplicationDefault();
                log.info("Firebase: using application default credentials");
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase initialised for project: {}", projectId);

        } catch (IOException e) {
            throw new IllegalStateException(
                "Firebase initialisation failed — check FIREBASE_SA_PATH or GOOGLE_APPLICATION_CREDENTIALS: "
                + e.getMessage(), e);
        }
    }
}
