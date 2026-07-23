package com.fittribe.api.service;

import com.fittribe.api.entity.Group;
import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.entity.User;
import com.fittribe.api.repository.ExerciseRepository;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.GroupRepository;
import com.fittribe.api.repository.NotificationRepository;
import com.fittribe.api.repository.PrEventRepository;
import com.fittribe.api.repository.SetLogRepository;
import com.fittribe.api.repository.UserDayStatusRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.strengthscore.ProgressSnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the notification triggers added in Package 2.
 *
 * Each step method is package-private so the test can call it directly
 * without going through the full async pipeline.
 */
class SessionFinishNotificationsTest {

    // ── Mocks ────────────────────────────────────────────────────────

    private NotificationService      notificationService;
    private NotificationRepository   notificationRepo;
    private GroupMemberRepository    groupMemberRepo;
    private GroupRepository          groupRepo;
    private WorkoutSessionRepository sessionRepo;
    private UserRepository           userRepo;

    private SessionFinishPostProcessor processor;

    // ── Test fixtures ────────────────────────────────────────────────

    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID GROUP_ID   = UUID.randomUUID();
    private static final UUID MEMBER_2   = UUID.randomUUID();

    private static final Instant WEEK_FROM = Instant.parse("2026-07-14T00:00:00Z");
    private static final Instant WEEK_TO   = Instant.parse("2026-07-21T00:00:00Z");

    @BeforeEach
    void setUp() {
        notificationService  = mock(NotificationService.class);
        notificationRepo     = mock(NotificationRepository.class);
        groupMemberRepo      = mock(GroupMemberRepository.class);
        groupRepo            = mock(GroupRepository.class);
        sessionRepo          = mock(WorkoutSessionRepository.class);
        userRepo             = mock(UserRepository.class);

        processor = new SessionFinishPostProcessor(
                mock(UserDayStatusRepository.class),
                sessionRepo,
                userRepo,
                mock(ProgressSnapshotService.class),
                mock(RankService.class),
                mock(CoinService.class),
                mock(com.fittribe.api.prv2.service.PrWritePathService.class),
                mock(PrEventRepository.class),
                mock(FeedEventWriter.class),
                mock(GroupProgressService.class),
                mock(SetLogRepository.class),
                mock(ExerciseRepository.class),
                mock(PlanService.class),
                mock(BonusFreezeGrantService.class),
                notificationService,
                notificationRepo,
                groupMemberRepo,
                groupRepo);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private SessionFinishContext ctx(int streak, int completed, int weeklyGoal) {
        return new SessionFinishContext(
                USER_ID, SESSION_ID, 1, completed >= weeklyGoal,
                completed, streak, streak - 1,
                BigDecimal.ZERO, 0, List.of(),
                Instant.now(), weeklyGoal, WEEK_FROM, WEEK_TO);
    }

    private GroupMember member(UUID userId) {
        GroupMember m = new GroupMember();
        m.setGroupId(GROUP_ID);
        m.setUserId(userId);
        return m;
    }

    private Group group(String name, int goal) {
        Group g = new Group();
        g.setName(name);
        g.setWeeklyGoal(goal);
        return g;
    }

    // ── EVENT 1: STREAK_MILESTONE ────────────────────────────────────

    @Test
    void streakMilestone_fires_on_milestone_streak() {
        processor.notifyStreakMilestone(ctx(10, 3, 4));

        verify(notificationService).notifyUser(
                eq(USER_ID), eq("STREAK_MILESTONE"),
                contains("10"), anyString(),
                isNull(), isNull(), anyMap(), eq(true));
    }

    @Test
    void streakMilestone_silent_on_non_milestone_streak() {
        processor.notifyStreakMilestone(ctx(7, 3, 4));
        verifyNoInteractions(notificationService);
    }

    @Test
    void streakMilestone_fires_for_each_defined_milestone() {
        for (int milestone : List.of(5, 10, 30, 50, 100, 365)) {
            reset(notificationService);
            processor.notifyStreakMilestone(ctx(milestone, 1, 4));
            verify(notificationService).notifyUser(
                    eq(USER_ID), eq("STREAK_MILESTONE"),
                    contains(String.valueOf(milestone)), anyString(),
                    isNull(), isNull(), anyMap(), eq(true));
        }
    }

    // ── EVENT 2: WEEKLY_GOAL_HIT ─────────────────────────────────────

    @Test
    void weeklyGoalHit_fires_on_exact_match() {
        processor.notifyWeeklyGoalHit(ctx(3, 4, 4));

        verify(notificationService).notifyUser(
                eq(USER_ID), eq("WEEKLY_GOAL_HIT"),
                contains("Weekly goal"), contains("4-session"),
                isNull(), isNull(), anyMap(), eq(true));
    }

    @Test
    void weeklyGoalHit_silent_when_under_goal() {
        processor.notifyWeeklyGoalHit(ctx(2, 3, 4));
        verifyNoInteractions(notificationService);
    }

    @Test
    void weeklyGoalHit_silent_when_over_goal() {
        // Extra sessions after goal is hit should not re-fire the notification
        processor.notifyWeeklyGoalHit(ctx(3, 5, 4));
        verifyNoInteractions(notificationService);
    }

    // ── EVENT 3: GROUP_GOAL_HIT ──────────────────────────────────────

    @Test
    void groupGoalHit_fires_to_all_members_when_all_hit_goal() {
        GroupMember self    = member(USER_ID);
        GroupMember other   = member(MEMBER_2);
        Group       grp     = group("Mumbai Crew", 4);

        when(groupMemberRepo.findByUserId(USER_ID)).thenReturn(List.of(self));
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(grp));
        when(notificationRepo.existsByGroupIdAndTypeAndCreatedAtAfter(
                any(), eq("GROUP_GOAL_HIT"), any(OffsetDateTime.class))).thenReturn(false);
        when(groupMemberRepo.findByGroupId(GROUP_ID)).thenReturn(List.of(self, other));
        when(sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                any(), eq("COMPLETED"), any(), any())).thenReturn(4);

        processor.notifyGroupGoalHit(ctx(3, 4, 4));

        // Both members receive the notification
        verify(notificationService, times(2)).notifyUser(
                any(), eq("GROUP_GOAL_HIT"),
                contains("Mumbai Crew"), anyString(),
                isNull(), eq(GROUP_ID), anyMap(), eq(true));
    }

    @Test
    void groupGoalHit_skips_when_dedup_flag_set() {
        when(groupMemberRepo.findByUserId(USER_ID)).thenReturn(List.of(member(USER_ID)));
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group("Crew", 4)));
        when(notificationRepo.existsByGroupIdAndTypeAndCreatedAtAfter(
                any(), eq("GROUP_GOAL_HIT"), any(OffsetDateTime.class))).thenReturn(true);

        processor.notifyGroupGoalHit(ctx(3, 4, 4));

        verifyNoInteractions(notificationService);
    }

    @Test
    void groupGoalHit_silent_when_not_all_members_hit_goal() {
        GroupMember self  = member(USER_ID);
        GroupMember other = member(MEMBER_2);

        when(groupMemberRepo.findByUserId(USER_ID)).thenReturn(List.of(self));
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group("Crew", 4)));
        when(notificationRepo.existsByGroupIdAndTypeAndCreatedAtAfter(
                any(), eq("GROUP_GOAL_HIT"), any(OffsetDateTime.class))).thenReturn(false);
        when(groupMemberRepo.findByGroupId(GROUP_ID)).thenReturn(List.of(self, other));
        // self hit goal; MEMBER_2 has not
        when(sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                eq(USER_ID), any(), any(), any())).thenReturn(4);
        when(sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                eq(MEMBER_2), any(), any(), any())).thenReturn(2);

        processor.notifyGroupGoalHit(ctx(3, 4, 4));

        verifyNoInteractions(notificationService);
    }

    // ── EVENT 4: GROUP_MEMBER_LOGGED ─────────────────────────────────

    @Test
    void groupMemberLogged_creates_inapp_for_other_members_no_push() {
        User user = new User();
        user.setDisplayName("Riya");

        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
        when(groupMemberRepo.findByUserId(USER_ID)).thenReturn(List.of(member(USER_ID)));
        when(groupRepo.findAllById(anyList())).thenReturn(List.of(group("Mumbai Crew", 4)));
        when(groupMemberRepo.findByGroupId(GROUP_ID)).thenReturn(
                List.of(member(USER_ID), member(MEMBER_2)));

        processor.notifyGroupMemberLogged(ctx(3, 4, 4));

        verify(notificationService, times(1)).notifyUser(
                eq(MEMBER_2), eq("GROUP_MEMBER_LOGGED"),
                contains("Riya"), anyString(),
                eq(USER_ID), eq(GROUP_ID), anyMap(), eq(false));
    }

    @Test
    void groupMemberLogged_excludes_self() {
        User user = new User();
        user.setDisplayName("Riya");

        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
        when(groupMemberRepo.findByUserId(USER_ID)).thenReturn(List.of(member(USER_ID)));
        when(groupRepo.findAllById(anyList())).thenReturn(List.of(group("Crew", 4)));
        when(groupMemberRepo.findByGroupId(GROUP_ID)).thenReturn(List.of(member(USER_ID)));

        processor.notifyGroupMemberLogged(ctx(3, 4, 4));

        // Self-only group: no notifications should be sent
        verifyNoInteractions(notificationService);
    }

    @Test
    void groupMemberLogged_sends_no_push() {
        User user = new User();
        user.setDisplayName("Arjun");

        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
        when(groupMemberRepo.findByUserId(USER_ID)).thenReturn(List.of(member(USER_ID)));
        when(groupRepo.findAllById(anyList())).thenReturn(List.of(group("Delhi Gym", 4)));
        when(groupMemberRepo.findByGroupId(GROUP_ID)).thenReturn(
                List.of(member(USER_ID), member(MEMBER_2)));

        processor.notifyGroupMemberLogged(ctx(2, 2, 4));

        // sendPush must always be false for this event
        verify(notificationService).notifyUser(
                any(), any(), any(), any(), any(), any(), any(), eq(false));
    }
}
