package com.fittribe.api.service;

import com.fittribe.api.entity.AuthProvider;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.UserRepository;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.detectProvider(FirebaseToken).
 *
 * FirebaseToken is a final class — it cannot be mocked with standard Mockito.
 * We construct fake instances by accessing the package-private Map-based
 * constructor via reflection.
 *
 * No Spring context is loaded; all collaborators are plain Mockito mocks.
 */
class AuthServiceProviderDetectionTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        UserRepository mockUserRepo = mock(UserRepository.class);
        JwtService     mockJwtService = mock(JwtService.class);
        authService = new AuthService(mockUserRepo, mockJwtService);
        // mockAuthEnabled defaults to false — not needed for detectProvider tests
    }

    // ── FirebaseToken construction helper ──────────────────────────────

    /**
     * Builds a real FirebaseToken instance from a raw-claims map using the
     * package-private constructor: FirebaseToken(Map<String, Object> claims).
     */
    private static FirebaseToken buildToken(Map<String, Object> claims) throws Exception {
        // FirebaseToken requires at least a "sub" (subject/uid) claim
        claims.putIfAbsent("sub", "test-uid");
        Constructor<FirebaseToken> ctor =
                FirebaseToken.class.getDeclaredConstructor(Map.class);
        ctor.setAccessible(true);
        return ctor.newInstance(claims);
    }

    /**
     * Builds a token whose "firebase.sign_in_provider" claim is set to the
     * given value. The top-level claims map wraps a nested "firebase" map.
     */
    private static FirebaseToken tokenWithSignInProvider(String signInProvider) throws Exception {
        Map<String, Object> firebaseClaim = new HashMap<>();
        firebaseClaim.put("sign_in_provider", signInProvider);

        Map<String, Object> claims = new HashMap<>();
        claims.put("firebase", firebaseClaim);
        return buildToken(claims);
    }

    // ── Tests: firebase.sign_in_provider present ───────────────────────

    @Test
    void phone_provider_via_firebase_claim() throws Exception {
        FirebaseToken token = tokenWithSignInProvider("phone");

        AuthProvider result = authService.detectProvider(token);

        assertEquals(AuthProvider.PHONE, result);
    }

    @Test
    void email_provider_via_firebase_claim_password() throws Exception {
        FirebaseToken token = tokenWithSignInProvider("password");

        AuthProvider result = authService.detectProvider(token);

        assertEquals(AuthProvider.EMAIL, result);
    }

    @Test
    void google_provider_via_firebase_claim() throws Exception {
        FirebaseToken token = tokenWithSignInProvider("google.com");

        AuthProvider result = authService.detectProvider(token);

        assertEquals(AuthProvider.GOOGLE, result);
    }

    @Test
    void apple_provider_via_firebase_claim() throws Exception {
        FirebaseToken token = tokenWithSignInProvider("apple.com");

        AuthProvider result = authService.detectProvider(token);

        assertEquals(AuthProvider.APPLE, result);
    }

    @Test
    void unsupported_provider_throws_api_exception_with_correct_code() throws Exception {
        FirebaseToken token = tokenWithSignInProvider("facebook.com");

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.detectProvider(token));

        assertEquals("UNSUPPORTED_PROVIDER", ex.getCode());
    }

    // ── Tests: fallback (no firebase claim present) ────────────────────

    @Test
    void fallback_phone_number_claim_present_returns_phone() throws Exception {
        // No "firebase" nested map — only a top-level "phone_number" claim
        Map<String, Object> claims = new HashMap<>();
        claims.put("phone_number", "+919876543210");
        FirebaseToken token = buildToken(claims);

        AuthProvider result = authService.detectProvider(token);

        assertEquals(AuthProvider.PHONE, result);
    }

    @Test
    void fallback_email_claim_present_returns_email() throws Exception {
        // No "firebase" nested map — only a top-level "email" claim.
        // FirebaseToken.getEmail() reads from the "email" key in the claims map.
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "user@example.com");
        FirebaseToken token = buildToken(claims);

        AuthProvider result = authService.detectProvider(token);

        assertEquals(AuthProvider.EMAIL, result);
    }

    @Test
    void fallback_neither_identifier_throws_api_exception_with_unknown_provider_code()
            throws Exception {
        // Completely empty claims — no firebase claim, no phone_number, no email
        Map<String, Object> claims = new HashMap<>();
        FirebaseToken token = buildToken(claims);

        ApiException ex = assertThrows(ApiException.class,
                () -> authService.detectProvider(token));

        assertEquals("UNKNOWN_PROVIDER", ex.getCode());
    }
}
