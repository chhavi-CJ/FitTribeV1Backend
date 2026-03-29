package com.fittribe.api.service;

import com.fittribe.api.dto.response.AuthResponse;
import com.fittribe.api.entity.User;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.UserRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse verifyFirebaseToken(String idToken) {
        if (FirebaseApp.getApps().isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FIREBASE_NOT_CONFIGURED",
                    "Firebase is not configured on this server. Set FIREBASE_PROJECT_ID in environment.");
        }

        FirebaseToken decoded;
        try {
            decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            log.warn("Firebase token verification failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_FIREBASE_TOKEN",
                    "Firebase ID token is invalid or expired.");
        }

        String uid   = decoded.getUid();
        String phone = (String) decoded.getClaims().get("phone_number");

        if (phone == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_PHONE",
                    "Firebase token does not contain a phone number.");
        }

        // ── Look up existing user ──────────────────────────────────────
        return userRepository.findByFirebaseUid(uid)
                .map(existing -> {
                    String token = jwtService.generateToken(existing.getId());
                    boolean isNew = existing.getDisplayName() == null;
                    return new AuthResponse(token, existing.getId(), isNew, existing.getDisplayName());
                })
                .orElseGet(() -> {
                    // ── Create new user ────────────────────────────────
                    User user = new User();
                    user.setFirebaseUid(uid);
                    user.setPhone(phone);
                    User saved;
                    try {
                        saved = userRepository.save(user);
                    } catch (DataIntegrityViolationException e) {
                        // Lost a concurrent-creation race — the other thread won.
                        // Fetch the row they created and return it as a normal login.
                        saved = userRepository.findByFirebaseUid(uid)
                                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                                        "AUTH_STATE_ERROR", "Auth state inconsistency. Please retry."));
                    }

                    String token = jwtService.generateToken(saved.getId());
                    return new AuthResponse(token, saved.getId(), true, null);
                });
    }
}
