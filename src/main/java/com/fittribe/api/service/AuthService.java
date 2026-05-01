package com.fittribe.api.service;

import com.fittribe.api.dto.response.AuthResponse;
import com.fittribe.api.entity.AuthProvider;
import com.fittribe.api.entity.User;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.UserRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Value("${app.mock-auth-enabled:false}")
    private boolean mockAuthEnabled;

    private final UserRepository userRepository;
    private final JwtService     jwtService;

    public AuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService     = jwtService;
    }

    @Transactional
    public AuthResponse verifyFirebaseToken(String idToken) {

        // ── Mock path (dev/test only) ─────────────────────────────────
        if (mockAuthEnabled && idToken.startsWith("mock_")) {
            return handleMockAuth(idToken);
        }

        // ── Real Firebase path ────────────────────────────────────────
        if (FirebaseApp.getApps().isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FIREBASE_NOT_CONFIGURED",
                    "Firebase is not configured on this server.");
        }

        FirebaseToken decoded;
        try {
            decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            log.warn("Firebase token verification failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_FIREBASE_TOKEN",
                    "Firebase ID token is invalid or expired.");
        }

        String firebaseUid = decoded.getUid();
        AuthProvider provider = detectProvider(decoded);
        String phone = (String) decoded.getClaims().get("phone_number");
        String email = decoded.getEmail();

        if (phone == null && email == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_IDENTIFIER",
                    "Firebase token contains neither phone nor email.");
        }

        String displayName = decoded.getName();

        return upsertUserAndIssueJwt(firebaseUid, provider, phone, email, displayName);
    }

    // ── Provider detection ────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    AuthProvider detectProvider(FirebaseToken token) {
        Map<String, Object> firebaseClaim =
                (Map<String, Object>) token.getClaims().get("firebase");

        if (firebaseClaim != null) {
            String signInProvider = (String) firebaseClaim.get("sign_in_provider");
            if (signInProvider != null) {
                return switch (signInProvider) {
                    case "phone"      -> AuthProvider.PHONE;
                    case "password"   -> AuthProvider.EMAIL;
                    case "google.com" -> AuthProvider.GOOGLE;
                    case "apple.com"  -> AuthProvider.APPLE;
                    default -> throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER",
                            "Provider not supported: " + signInProvider);
                };
            }
        }

        // Fallback: infer from available claims
        if (token.getClaims().containsKey("phone_number")) return AuthProvider.PHONE;
        if (token.getEmail() != null)                      return AuthProvider.EMAIL;

        throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PROVIDER",
                "Cannot determine auth provider from token.");
    }

    // ── Mock auth ─────────────────────────────────────────────────────

    private AuthResponse handleMockAuth(String idToken) {
        String suffix = idToken.substring("mock_".length());

        AuthProvider provider;
        String phone = null;
        String email = null;
        String firebaseUid;

        if (suffix.startsWith("+")) {
            provider    = AuthProvider.PHONE;
            phone       = suffix;
            firebaseUid = "mock_" + phone;
        } else if (suffix.startsWith("email_")) {
            provider    = AuthProvider.EMAIL;
            email       = suffix.substring("email_".length());
            firebaseUid = "mock_" + email;
        } else if (suffix.startsWith("google_")) {
            provider    = AuthProvider.GOOGLE;
            email       = suffix.substring("google_".length());
            firebaseUid = "mock_google_" + email;
        } else if (suffix.startsWith("apple_")) {
            provider    = AuthProvider.APPLE;
            email       = suffix.substring("apple_".length());
            firebaseUid = "mock_apple_" + email;
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MOCK_TOKEN",
                    "Mock token format not recognized. Use mock_+91..., mock_email_..., mock_google_..., or mock_apple_...");
        }

        return upsertUserAndIssueJwt(firebaseUid, provider, phone, email, null);
    }

    // ── Shared upsert + JWT issuance ──────────────────────────────────

    private AuthResponse upsertUserAndIssueJwt(String firebaseUid,
                                                AuthProvider provider,
                                                String phone,
                                                String email,
                                                String displayName) {
        // Look up by firebaseUid first (returning user fast path)
        Optional<User> existing = userRepository.findByFirebaseUid(firebaseUid);

        // If not found by uid, try linking by email or phone (account merge case)
        if (existing.isEmpty() && (email != null || phone != null)) {
            existing = userRepository.findByEmailOrPhone(email, phone);
        }

        boolean isNewUser;
        User user;

        if (existing.isPresent()) {
            user      = existing.get();
            isNewUser = user.getDisplayName() == null;

            // Backfill missing identifiers
            if (user.getEmail() == null && email != null) user.setEmail(email);
            if (user.getPhone() == null && phone != null) user.setPhone(phone);

            user.linkProvider(provider);
            userRepository.save(user);

        } else {
            // New user
            user = new User();
            user.setFirebaseUid(firebaseUid);
            user.setPhone(phone);
            user.setEmail(email);
            user.setAuthProvider(provider);
            user.linkProvider(provider);

            // Display name: prefer token claim, then email local-part, then null
            if (displayName == null && email != null) {
                int atIdx = email.indexOf('@');
                displayName = atIdx > 0 ? email.substring(0, atIdx) : email;
            }
            user.setDisplayName(displayName);

            try {
                user = userRepository.save(user);
            } catch (DataIntegrityViolationException e) {
                // Could be a duplicate firebase_uid, email, or phone — try all lookups
                Optional<User> recovered = userRepository.findByFirebaseUid(firebaseUid);
                if (recovered.isEmpty() && email != null) {
                    recovered = userRepository.findByEmailIgnoreCase(email);
                }
                if (recovered.isEmpty() && phone != null) {
                    recovered = userRepository.findByPhone(phone);
                }
                if (recovered.isPresent()) {
                    // Lost a concurrent-creation race — return the winner's JWT
                    user = recovered.get();
                } else {
                    throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_IDENTIFIER",
                            "An account with that phone or email already exists.");
                }
            }
            isNewUser = true;
        }

        String token = jwtService.generateToken(user.getId());
        return new AuthResponse(token, user.getId(), isNewUser, user.getDisplayName());
    }
}
