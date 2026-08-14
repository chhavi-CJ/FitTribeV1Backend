package com.fittribe.api.scheduler;

import com.fittribe.api.entity.Group;
import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.UserDayStatus;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.GroupRepository;
import com.fittribe.api.repository.NotificationRepository;
import com.fittribe.api.repository.UserDayStatusRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationSchedulerTest {

    private NotificationService      notificationService;
    private UserRepository           userRepo;
    private WorkoutSessionRepository sessionRepo;
    private GroupMemberRepository    groupMemberRepo;
    private GroupRepository          groupRepo;
    private NotificationRepository   notificationRepo;
    private UserDayStatusRepository  userDayStatusRepo;

    private NotificationScheduler scheduler;

    private static final UUID USER_ID  = UUID.randomUUID();
    private static final UUID MEMBER_2 = UUID.randomUUID();
    private static final UUID GROUP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        userRepo            = mock(UserRepository.class);
        sessionRepo         = mock(WorkoutSessionRepository.class);
        groupMemberRepo     = mock(GroupMemberRepository.class);
        groupRepo           = mock(GroupRepository.class);
        notificationRepo    = mock(NotificationRepository.class);
        userDayStatusRepo   = mock(UserDayStatusRepository.class);

        scheduler = new NotificationScheduler(
                notificationService, userRepo, sessionRepo,
                groupMemberRepo, groupRepo, notificationRepo, userDayStatusRepo);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User user(int streak, UUID id) {
        User u = new User();
        u.setId(id);
        u.setStreak(streak);
        return u;
    }

    private WorkoutSession session(Instant finishedAt) {
        WorkoutSession s = new WorkoutSession();
        s.setId(UUID.randomUUID());
        s.setFinishedAt(finishedAt);
        return s;
    }

    private GroupMember member(UUID userId, UUID groupId) {
        GroupMember m = new GroupMember();
        m.setUserId(userId);
        m.setGroupId(groupId);
        return m;
    }

    private Group group(String name, UUID id) {
        Group g = new Group();
        g.setName(name);
        // Inject id via reflection — Group.getId() is set by JPA normally
        try {
            java.lang.reflect.Field f = Group.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(g, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return g;
    }

    // ── DAILY_WORKOUT_REMINDER (three tiers) ───────────────────────────────────

    @Test
    void dailyReminder_notifies_with_daily_reminder_when_no_crunch() {
        // User has streak=1 (< 3), not behind on goal → DAILY_REMINDER tier
        when(userRepo.findAll()).thenReturn(List.of(user(1, USER_ID)));
        when(sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                eq(USER_ID), eq("COMPLETED"), any(), any())).thenReturn(false);
        when(userDayStatusRepo.findByIdUserIdAndIdDate(eq(USER_ID), any())).thenReturn(Optional.empty());

        scheduler.dailyWorkoutReminderJob();

        verify(notificationService).notifyUser(
                eq(USER_ID), eq("DAILY_REMINDER"),
                anyString(), anyString(),
                isNull(), isNull(), anyMap(), eq(true));
    }

    @Test
    void dailyReminder_notifies_with_streak_risk_when_streak_gte_3() {
        // User has streak=5 (>= 3), not behind → STREAK_RISK tier
        when(userRepo.findAll()).thenReturn(List.of(user(5, USER_ID)));
        when(sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                eq(USER_ID), eq("COMPLETED"), any(), any())).thenReturn(false);
        when(userDayStatusRepo.findByIdUserIdAndIdDate(eq(USER_ID), any())).thenReturn(Optional.empty());

        scheduler.dailyWorkoutReminderJob();

        verify(notificationService).notifyUser(
                eq(USER_ID), eq("STREAK_RISK"),
                anyString(), anyString(),
                isNull(), isNull(), anyMap(), eq(true));
    }

    @Test
    void dailyReminder_skips_when_trained_today() {
        // User already logged today → skip
        when(userRepo.findAll()).thenReturn(List.of(user(5, USER_ID)));
        when(sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                eq(USER_ID), eq("COMPLETED"), any(), any())).thenReturn(true);

        scheduler.dailyWorkoutReminderJob();

        verifyNoInteractions(notificationService);
    }

    @Test
    void dailyReminder_skips_when_exempt_status_set() {
        // User has REST/SICK/TRAVELLING/BUSY status → skip
        UserDayStatus status = new UserDayStatus();
        status.setStatus("REST");
        when(userRepo.findAll()).thenReturn(List.of(user(5, USER_ID)));
        when(sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                eq(USER_ID), eq("COMPLETED"), any(), any())).thenReturn(false);
        when(userDayStatusRepo.findByIdUserIdAndIdDate(eq(USER_ID), any()))
                .thenReturn(Optional.of(status));

        scheduler.dailyWorkoutReminderJob();

        verifyNoInteractions(notificationService);
    }

    // ── COMEBACK_NUDGE ────────────────────────────────────────────────────────

    @Test
    void comebackNudge_notifies_user_inactive_3_days() {
        User u = user(0, USER_ID);
        WorkoutSession last = session(Instant.now().minus(3, ChronoUnit.DAYS));

        when(userRepo.findAllByStreak(0)).thenReturn(List.of(u));
        when(sessionRepo.findFirstByUserIdAndStatusOrderByFinishedAtDesc(USER_ID, "COMPLETED"))
                .thenReturn(Optional.of(last));
        // No STREAK_RISK or GROUP_TRAINED today
        when(notificationRepo.existsByRecipientIdAndTypeAndCreatedAtAfter(
                eq(USER_ID), eq("STREAK_RISK"), any(OffsetDateTime.class))).thenReturn(false);
        when(notificationRepo.existsByRecipientIdAndTypeAndCreatedAtAfter(
                eq(USER_ID), eq("GROUP_TRAINED_WITHOUT_YOU"), any(OffsetDateTime.class))).thenReturn(false);
        // No cooldown hit for COMEBACK_NUDGE
        when(notificationRepo.existsByRecipientIdAndTypeAndCreatedAtAfter(
                eq(USER_ID), eq("COMEBACK_NUDGE"), any(OffsetDateTime.class))).thenReturn(false);
        when(userDayStatusRepo.findByIdUserIdAndIdDate(eq(USER_ID), any())).thenReturn(Optional.empty());

        scheduler.comebackNudgeJob();

        verify(notificationService).notifyUser(
                eq(USER_ID), eq("COMEBACK_NUDGE"),
                anyString(), anyString(),
                isNull(), isNull(), anyMap(), eq(true));
    }

    @Test
    void comebackNudge_skips_when_inactive_less_than_2_days() {
        User u = user(0, USER_ID);
        WorkoutSession last = session(Instant.now().minus(1, ChronoUnit.DAYS)); // 24 h ago

        when(userRepo.findAllByStreak(0)).thenReturn(List.of(u));
        when(sessionRepo.findFirstByUserIdAndStatusOrderByFinishedAtDesc(USER_ID, "COMPLETED"))
                .thenReturn(Optional.of(last));

        scheduler.comebackNudgeJob();

        verifyNoInteractions(notificationService);
    }

    @Test
    void comebackNudge_skips_when_nudged_within_3_days() {
        User u = user(0, USER_ID);
        WorkoutSession last = session(Instant.now().minus(5, ChronoUnit.DAYS));

        when(userRepo.findAllByStreak(0)).thenReturn(List.of(u));
        when(sessionRepo.findFirstByUserIdAndStatusOrderByFinishedAtDesc(USER_ID, "COMPLETED"))
                .thenReturn(Optional.of(last));
        when(notificationRepo.existsByRecipientIdAndTypeAndCreatedAtAfter(
                eq(USER_ID), eq("STREAK_RISK"), any(OffsetDateTime.class))).thenReturn(false);
        when(notificationRepo.existsByRecipientIdAndTypeAndCreatedAtAfter(
                eq(USER_ID), eq("GROUP_TRAINED_WITHOUT_YOU"), any(OffsetDateTime.class))).thenReturn(false);
        // 3-day cooldown: COMEBACK_NUDGE was sent within last 3 days
        when(notificationRepo.existsByRecipientIdAndTypeAndCreatedAtAfter(
                eq(USER_ID), eq("COMEBACK_NUDGE"), any(OffsetDateTime.class))).thenReturn(true);
        when(userDayStatusRepo.findByIdUserIdAndIdDate(eq(USER_ID), any())).thenReturn(Optional.empty());

        scheduler.comebackNudgeJob();

        verifyNoInteractions(notificationService);
    }

    // ── GROUP_TRAINED_WITHOUT_YOU ─────────────────────────────────────────────

    @Test
    void groupTrained_notifies_absent_member_when_teammate_trained() {
        Group g = group("Mumbai Crew", GROUP_ID);
        when(groupRepo.findAll()).thenReturn(List.of(g));
        when(groupMemberRepo.findByGroupId(GROUP_ID))
                .thenReturn(List.of(member(USER_ID, GROUP_ID), member(MEMBER_2, GROUP_ID)));
        // USER_ID trained, MEMBER_2 did not
        when(sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                eq(USER_ID), any(), any(), any())).thenReturn(true);
        when(sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                eq(MEMBER_2), any(), any(), any())).thenReturn(false);
        // No prior push for MEMBER_2 today
        when(notificationRepo.existsByRecipientIdAndTypeAndCreatedAtAfter(
                eq(MEMBER_2), any(), any(OffsetDateTime.class))).thenReturn(false);
        when(userDayStatusRepo.findByIdUserIdAndIdDate(eq(MEMBER_2), any())).thenReturn(Optional.empty());

        scheduler.groupTrainedWithoutYouJob();

        verify(notificationService).notifyUser(
                eq(MEMBER_2), eq("GROUP_TRAINED_WITHOUT_YOU"),
                anyString(), anyString(),
                isNull(), eq(GROUP_ID), anyMap(), eq(true));
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void groupTrained_skips_user_who_already_got_streak_risk_today() {
        Group g = group("Crew", GROUP_ID);
        when(groupRepo.findAll()).thenReturn(List.of(g));
        when(groupMemberRepo.findByGroupId(GROUP_ID))
                .thenReturn(List.of(member(USER_ID, GROUP_ID), member(MEMBER_2, GROUP_ID)));
        when(sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                eq(USER_ID), any(), any(), any())).thenReturn(true);
        when(sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                eq(MEMBER_2), any(), any(), any())).thenReturn(false);
        // STREAK_RISK already sent to MEMBER_2 today
        when(notificationRepo.existsByRecipientIdAndTypeAndCreatedAtAfter(
                eq(MEMBER_2), eq("STREAK_RISK"), any(OffsetDateTime.class))).thenReturn(true);

        scheduler.groupTrainedWithoutYouJob();

        verifyNoInteractions(notificationService);
    }

    @Test
    void groupTrained_skips_notification_when_all_members_trained() {
        Group g = group("Crew", GROUP_ID);
        when(groupRepo.findAll()).thenReturn(List.of(g));
        when(groupMemberRepo.findByGroupId(GROUP_ID))
                .thenReturn(List.of(member(USER_ID, GROUP_ID), member(MEMBER_2, GROUP_ID)));
        // Both members trained today
        when(sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                any(), any(), any(), any())).thenReturn(true);

        scheduler.groupTrainedWithoutYouJob();

        verifyNoInteractions(notificationService);
    }

    // ── hasReceivedPushToday ──────────────────────────────────────────────────

    @Test
    void hasReceivedPushToday_returns_true_when_any_type_matches() {
        when(notificationRepo.existsByRecipientIdAndTypeAndCreatedAtAfter(
                eq(USER_ID), eq("STREAK_RISK"), any(OffsetDateTime.class))).thenReturn(true);

        boolean result = scheduler.hasReceivedPushToday(USER_ID, "STREAK_RISK", "COMEBACK_NUDGE");

        assertTrue(result);
    }

    @Test
    void hasReceivedPushToday_returns_false_when_no_type_matches() {
        when(notificationRepo.existsByRecipientIdAndTypeAndCreatedAtAfter(
                eq(USER_ID), any(), any(OffsetDateTime.class))).thenReturn(false);

        boolean result = scheduler.hasReceivedPushToday(USER_ID, "STREAK_RISK", "COMEBACK_NUDGE");

        assertFalse(result);
    }

    private void assertFalse(boolean result) {
        org.junit.jupiter.api.Assertions.assertFalse(result);
    }

    private void assertTrue(boolean result) {
        org.junit.jupiter.api.Assertions.assertTrue(result);
    }
}
