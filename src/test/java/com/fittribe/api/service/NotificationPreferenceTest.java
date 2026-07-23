package com.fittribe.api.service;

import com.fittribe.api.entity.NotificationPreference;
import com.fittribe.api.repository.DeviceTokenRepository;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.NotificationPreferenceRepository;
import com.fittribe.api.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationPreferenceTest {

    private NotificationService service;
    private NotificationPreferenceRepository prefRepo;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @BeforeEach
    void setUp() {
        prefRepo = mock(NotificationPreferenceRepository.class);
        DeviceTokenRepository deviceTokenRepo = mock(DeviceTokenRepository.class);
        GroupMemberRepository groupMemberRepo = mock(GroupMemberRepository.class);
        NotificationRepository notificationRepo = mock(NotificationRepository.class);

        service = new NotificationService(deviceTokenRepo, groupMemberRepo, notificationRepo, prefRepo);
    }

    // ── shouldSendPush: category disabled ──────────────────────────────────

    @Test
    void shouldSendPush_returns_false_when_streak_category_disabled() {
        NotificationPreference prefs = new NotificationPreference(USER_ID);
        prefs.setStreakEnabled(false);
        when(prefRepo.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        // Call via reflection since it's private
        boolean result = invokePrivateShouldSendPush("STREAK_RISK");
        assertFalse(result);
    }

    @Test
    void shouldSendPush_returns_false_when_group_activity_disabled() {
        NotificationPreference prefs = new NotificationPreference(USER_ID);
        prefs.setGroupActivityEnabled(false);
        when(prefRepo.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        boolean result = invokePrivateShouldSendPush("GROUP_GOAL_HIT");
        assertFalse(result);
    }

    @Test
    void shouldSendPush_returns_false_when_weekly_report_disabled() {
        NotificationPreference prefs = new NotificationPreference(USER_ID);
        prefs.setWeeklyReportEnabled(false);
        when(prefRepo.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        boolean result = invokePrivateShouldSendPush("WEEKLY_REPORT_READY");
        assertFalse(result);
    }

    @Test
    void shouldSendPush_returns_false_when_poke_disabled() {
        NotificationPreference prefs = new NotificationPreference(USER_ID);
        prefs.setSocialEnabled(false);
        when(prefRepo.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        boolean result = invokePrivateShouldSendPush("POKE");
        assertFalse(result);
    }

    @Test
    void shouldSendPush_returns_false_when_comeback_disabled() {
        NotificationPreference prefs = new NotificationPreference(USER_ID);
        prefs.setComebackEnabled(false);
        when(prefRepo.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        boolean result = invokePrivateShouldSendPush("COMEBACK_NUDGE");
        assertFalse(result);
    }

    // ── shouldSendPush: category enabled, no quiet hours ──────────────────

    @Test
    void shouldSendPush_returns_true_when_category_enabled_and_no_quiet_hours() {
        NotificationPreference prefs = new NotificationPreference(USER_ID);
        prefs.setStreakEnabled(true);
        prefs.setQuietHoursEnabled(false);
        when(prefRepo.findByUserId(USER_ID)).thenReturn(Optional.of(prefs));

        boolean result = invokePrivateShouldSendPush("STREAK_MILESTONE");
        assertTrue(result);
    }

    // ── shouldSendPush: quiet hours configured (logic is runtime-dependent) ─

    @Test
    void shouldSendPush_respects_quiet_hours_flag() {
        NotificationPreference prefsNoQuiet = new NotificationPreference(USER_ID);
        prefsNoQuiet.setStreakEnabled(true);
        prefsNoQuiet.setQuietHoursEnabled(false);
        when(prefRepo.findByUserId(USER_ID)).thenReturn(Optional.of(prefsNoQuiet));

        boolean result = invokePrivateShouldSendPush("STREAK_RISK");
        assertTrue(result, "Should send when quiet hours disabled");

        // Quiet hours enabled — logic depends on current time (can't easily test without Clock mock)
        NotificationPreference prefsWithQuiet = new NotificationPreference(USER_ID);
        prefsWithQuiet.setStreakEnabled(true);
        prefsWithQuiet.setQuietHoursEnabled(true);
        prefsWithQuiet.setQuietStart(LocalTime.of(22, 0));
        prefsWithQuiet.setQuietEnd(LocalTime.of(7, 0));
        when(prefRepo.findByUserId(USER_ID)).thenReturn(Optional.of(prefsWithQuiet));

        // Result depends on current time — just verify it doesn't throw
        assertDoesNotThrow(() -> invokePrivateShouldSendPush("STREAK_RISK"));
    }

    // ── shouldSendPush: defaults when no preference row ────────────────────

    @Test
    void shouldSendPush_uses_defaults_when_no_preference_row() {
        when(prefRepo.findByUserId(USER_ID)).thenReturn(Optional.empty());

        // All categories should be enabled by default
        boolean streakResult = invokePrivateShouldSendPush("STREAK_RISK");
        assertTrue(streakResult);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean invokePrivateShouldSendPush(String type) {
        try {
            java.lang.reflect.Method method = NotificationService.class
                    .getDeclaredMethod("shouldSendPush", java.util.UUID.class, String.class);
            method.setAccessible(true);
            return (boolean) method.invoke(service, USER_ID, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke shouldSendPush", e);
        }
    }
}
