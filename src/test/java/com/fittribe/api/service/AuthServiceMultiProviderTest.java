package com.fittribe.api.service;

import com.fittribe.api.dto.response.AuthResponse;
import com.fittribe.api.entity.AuthProvider;
import com.fittribe.api.entity.User;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.verifyFirebaseToken() exercised via the mock-auth
 * path (idToken starts with "mock_"). Mock auth is enabled on each test instance
 * by flipping the private mockAuthEnabled field with reflection — no Spring
 * context is needed, and FirebaseAuth is never called.
 *
 * No @SpringBootTest, no @ExtendWith(MockitoExtension.class) — plain JUnit 5
 * with manual Mockito mocks, matching the project's existing test style.
 */
class AuthServiceMultiProviderTest {

    private UserRepository userRepo;
    private JwtService     jwtService;
    private AuthService    authService;

    // ── Reflection helper: flip the @Value-injected field ─────────────

    private static void setMockAuthEnabled(AuthService service, boolean value) throws Exception {
        Field field = AuthService.class.getDeclaredField("mockAuthEnabled");
        field.setAccessible(true);
        field.set(service, value);
    }

    // ── Reflection helper: set a UUID on a new User (simulates DB UUID gen) ──

    private static void assignId(User user) throws Exception {
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, UUID.randomUUID());
    }

    // ── Common setup ───────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        userRepo   = mock(UserRepository.class);
        jwtService = mock(JwtService.class);
        authService = new AuthService(userRepo, jwtService);
        setMockAuthEnabled(authService, true);

        when(jwtService.generateToken(any())).thenReturn("test-jwt-token");

        // Simulate DB UUID assignment on first save of a new User
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                assignId(u);
            }
            return u;
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /** Stubs both lookup paths to return empty (truly new user). */
    private void noExistingUser() {
        when(userRepo.findByFirebaseUid(any())).thenReturn(Optional.empty());
        when(userRepo.findByEmailOrPhone(any(), any())).thenReturn(Optional.empty());
    }

    // ── Tests ──────────────────────────────────────────────────────────

    @Test
    void new_phone_user_saved_with_phone_provider_and_marked_as_new() {
        noExistingUser();

        AuthResponse response = authService.verifyFirebaseToken("mock_+919876543210");

        assertTrue(response.isNewUser());
        assertEquals("test-jwt-token", response.token());

        // Capture the saved user
        verify(userRepo, atLeastOnce()).save(any(User.class));

        // Verify the user that was passed to save had correct fields
        // We use an ArgumentCaptor-free approach: capture via answer
        // The save stub already captured it — re-query via a fresh captor
        org.mockito.ArgumentCaptor<User> captor =
                org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepo, atLeastOnce()).save(captor.capture());

        User saved = captor.getAllValues().stream()
                .filter(u -> u.getPhone() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No user with phone was saved"));

        assertEquals(AuthProvider.PHONE, saved.getAuthProvider());
        assertEquals("+919876543210", saved.getPhone());
        assertNull(saved.getEmail());
    }

    @Test
    void new_email_user_saved_with_email_provider_and_local_part_as_display_name() {
        noExistingUser();

        AuthResponse response = authService.verifyFirebaseToken("mock_email_test@example.com");

        assertTrue(response.isNewUser());

        org.mockito.ArgumentCaptor<User> captor =
                org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepo, atLeastOnce()).save(captor.capture());

        User saved = captor.getAllValues().stream()
                .filter(u -> u.getEmail() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No user with email was saved"));

        assertEquals(AuthProvider.EMAIL, saved.getAuthProvider());
        assertEquals("test@example.com", saved.getEmail());
        assertNull(saved.getPhone());
        // Display name defaults to the local-part of the email address
        assertEquals("test", saved.getDisplayName());
    }

    @Test
    void new_google_user_saved_with_google_provider() {
        noExistingUser();

        AuthResponse response = authService.verifyFirebaseToken("mock_google_user@gmail.com");

        assertTrue(response.isNewUser());

        org.mockito.ArgumentCaptor<User> captor =
                org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepo, atLeastOnce()).save(captor.capture());

        User saved = captor.getAllValues().stream()
                .filter(u -> u.getEmail() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No user with email was saved"));

        assertEquals(AuthProvider.GOOGLE, saved.getAuthProvider());
        assertEquals("user@gmail.com", saved.getEmail());
        assertNull(saved.getPhone());
    }

    @Test
    void returning_user_found_by_firebase_uid_is_not_marked_new() throws Exception {
        // Existing user with a display name → isNewUser = false
        User existingUser = new User();
        assignId(existingUser);
        existingUser.setFirebaseUid("mock_+919876543210");
        existingUser.setPhone("+919876543210");
        existingUser.setDisplayName("Raj");
        existingUser.setAuthProvider(AuthProvider.PHONE);

        when(userRepo.findByFirebaseUid("mock_+919876543210"))
                .thenReturn(Optional.of(existingUser));

        AuthResponse response = authService.verifyFirebaseToken("mock_+919876543210");

        assertFalse(response.isNewUser());
        // save() is still called to persist any linked-provider updates
        verify(userRepo, atLeastOnce()).save(any(User.class));
    }

    @Test
    void account_linking_email_backfilled_and_google_added_to_linked_providers()
            throws Exception {
        // Existing phone user — found NOT by firebaseUid but by email/phone lookup
        User existingPhoneUser = new User();
        assignId(existingPhoneUser);
        existingPhoneUser.setFirebaseUid("mock_+919876543210");
        existingPhoneUser.setPhone("+919876543210");
        existingPhoneUser.setDisplayName("Raj");
        existingPhoneUser.setAuthProvider(AuthProvider.PHONE);
        existingPhoneUser.linkProvider(AuthProvider.PHONE);
        // email is null — will be backfilled by account linking

        String googleFirebaseUid = "mock_google_raj@gmail.com";

        // Not found by the new Google firebaseUid
        when(userRepo.findByFirebaseUid(googleFirebaseUid))
                .thenReturn(Optional.empty());
        // Found by email/phone lookup (email = "raj@gmail.com", phone fallback)
        when(userRepo.findByEmailOrPhone("raj@gmail.com", null))
                .thenReturn(Optional.of(existingPhoneUser));

        authService.verifyFirebaseToken("mock_google_raj@gmail.com");

        // Email must now be set on the linked user
        assertEquals("raj@gmail.com", existingPhoneUser.getEmail());
        // GOOGLE must appear in the linked providers set
        assertTrue(existingPhoneUser.getLinkedProviders().contains(AuthProvider.GOOGLE),
                "Expected GOOGLE in linked providers but got: "
                        + existingPhoneUser.getLinkedProviders());

        verify(userRepo, atLeastOnce()).save(existingPhoneUser);
    }

    @Test
    void mock_token_with_unrecognized_format_throws_invalid_mock_token() {
        // "mock_unknown_format" — suffix does not start with +, email_, google_, or apple_
        ApiException ex = assertThrows(ApiException.class,
                () -> authService.verifyFirebaseToken("mock_unknown_format"));

        assertEquals("INVALID_MOCK_TOKEN", ex.getCode());
    }
}
