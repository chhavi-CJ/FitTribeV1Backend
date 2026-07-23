package com.fittribe.api.group;

import com.fittribe.api.entity.*;
import com.fittribe.api.repository.*;
import com.fittribe.api.service.GroupProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupProgressServiceTest {

    private GroupMemberRepository              memberRepo;
    private GroupWeeklyProgressRepository      progressRepo;
    private GroupMemberGoalSnapshotRepository  snapshotRepo;
    private GroupRepository                    groupRepo;
    private UserRepository                     userRepo;
    private FeedItemRepository                 feedItemRepo;
    private GroupWeeklyCardRepository          groupWeeklyCardRepo;
    private JdbcTemplate                       jdbc;

    private GroupProgressService service;

    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final UUID USER_A   = UUID.randomUUID();
    private static final UUID USER_B   = UUID.randomUUID();
    private static final UUID USER_C   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        memberRepo          = mock(GroupMemberRepository.class);
        progressRepo        = mock(GroupWeeklyProgressRepository.class);
        snapshotRepo        = mock(GroupMemberGoalSnapshotRepository.class);
        groupRepo           = mock(GroupRepository.class);
        userRepo            = mock(UserRepository.class);
        feedItemRepo        = mock(FeedItemRepository.class);
        groupWeeklyCardRepo = mock(GroupWeeklyCardRepository.class);
        jdbc                = mock(JdbcTemplate.class);

        service = new GroupProgressService(memberRepo, progressRepo, snapshotRepo,
                groupRepo, userRepo, feedItemRepo, groupWeeklyCardRepo, jdbc);

        // Default: user A is in GROUP_ID
        GroupMember gm = new GroupMember();
        gm.setGroupId(GROUP_ID);
        gm.setUserId(USER_A);
        when(memberRepo.findByUserId(USER_A)).thenReturn(List.of(gm));

        User userA = new User();
        userA.setWeeklyGoal(4);
        when(userRepo.findById(USER_A)).thenReturn(Optional.of(userA));

        // Default: jdbc returns target=8 (2 members × 4)
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(GROUP_ID)))
                .thenReturn(8);
    }

    @Test
    void tier_upgrades_at_70_percent() {
        // target=10, need 7 sessions for BRONZE (70%)
        GroupWeeklyProgress progress = progressWithTarget(10);
        when(progressRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), anyInt(), anyInt()))
                .thenReturn(Optional.of(progress));

        // 7 active snapshots already logged
        when(snapshotRepo.findByGroupIdAndUserIdAndIsoYearAndIsoWeek(eq(GROUP_ID), eq(USER_A), anyInt(), anyInt()))
                .thenReturn(Optional.of(activeSnapshotWith(USER_A, 6)));
        when(snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), anyInt(), anyInt()))
                .thenReturn(List.of(activeSnapshotWith(USER_A, 7)));

        when(progressRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(snapshotRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordSessionForUser(USER_A, LocalDate.of(2026, 4, 21));

        assertEquals("BRONZE", progress.getCurrentTier());
        verify(feedItemRepo).save(argThat(fi -> "CARD_TIER_UPGRADED".equals(fi.getType())));
    }

    @Test
    void tier_upgrades_at_85_percent() {
        GroupWeeklyProgress progress = progressWithTarget(20);
        when(progressRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), anyInt(), anyInt()))
                .thenReturn(Optional.of(progress));

        // 17 sessions logged after this one → 85% of 20
        when(snapshotRepo.findByGroupIdAndUserIdAndIsoYearAndIsoWeek(eq(GROUP_ID), eq(USER_A), anyInt(), anyInt()))
                .thenReturn(Optional.of(activeSnapshotWith(USER_A, 16)));
        when(snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), anyInt(), anyInt()))
                .thenReturn(List.of(activeSnapshotWith(USER_A, 17)));

        progress.setCurrentTier("BRONZE"); // already bronze, should upgrade to silver
        when(progressRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(snapshotRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordSessionForUser(USER_A, LocalDate.of(2026, 4, 21));

        assertEquals("SILVER", progress.getCurrentTier());
        verify(feedItemRepo).save(argThat(fi -> "CARD_TIER_UPGRADED".equals(fi.getType())));
    }

    @Test
    void tier_upgrades_at_100_percent() {
        GroupWeeklyProgress progress = progressWithTarget(8);
        when(progressRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), anyInt(), anyInt()))
                .thenReturn(Optional.of(progress));

        when(snapshotRepo.findByGroupIdAndUserIdAndIsoYearAndIsoWeek(eq(GROUP_ID), eq(USER_A), anyInt(), anyInt()))
                .thenReturn(Optional.of(activeSnapshotWith(USER_A, 7)));
        when(snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), anyInt(), anyInt()))
                .thenReturn(List.of(activeSnapshotWith(USER_A, 8)));

        progress.setCurrentTier("SILVER");
        when(progressRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(snapshotRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordSessionForUser(USER_A, LocalDate.of(2026, 4, 21));

        assertEquals("GOLD", progress.getCurrentTier());
        verify(feedItemRepo).save(argThat(fi -> "CARD_TIER_UPGRADED".equals(fi.getType())));
    }

    @Test
    void overachiever_flag_set_above_100() {
        GroupWeeklyProgress progress = progressWithTarget(4);
        progress.setCurrentTier("GOLD");
        progress.setSessionsLogged(4);
        when(progressRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), anyInt(), anyInt()))
                .thenReturn(Optional.of(progress));

        // 5th session → 125%
        when(snapshotRepo.findByGroupIdAndUserIdAndIsoYearAndIsoWeek(eq(GROUP_ID), eq(USER_A), anyInt(), anyInt()))
                .thenReturn(Optional.of(activeSnapshotWith(USER_A, 4)));
        when(snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), anyInt(), anyInt()))
                .thenReturn(List.of(activeSnapshotWith(USER_A, 5)));

        when(progressRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(snapshotRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.recordSessionForUser(USER_A, LocalDate.of(2026, 4, 21));

        assertTrue(progress.isOverachiever());
        assertEquals("GOLD", progress.getCurrentTier());
    }

    @Test
    void join_mid_week_does_not_change_target() {
        GroupWeeklyProgress progress = progressWithTarget(12);
        when(progressRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), anyInt(), anyInt()))
                .thenReturn(Optional.of(progress));
        when(snapshotRepo.findByGroupIdAndUserIdAndIsoYearAndIsoWeek(eq(GROUP_ID), eq(USER_B), anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        when(snapshotRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.onMemberJoinedGroup(GROUP_ID, USER_B);

        // Target must not change
        assertEquals(12, progress.getTargetSessions());
        verify(progressRepo, never()).save(any());

        // Snapshot created with weekly_goal=0 and joined_this_week=true
        verify(snapshotRepo).save(argThat(s ->
                s.getWeeklyGoal() == 0 && Boolean.TRUE.equals(s.getJoinedThisWeek())));
    }

    @Test
    void leave_mid_week_drops_target_and_contributions() {
        GroupWeeklyProgress progress = progressWithTarget(12);
        progress.setSessionsLogged(5);
        progress.setCurrentTier("BRONZE");
        when(progressRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), anyInt(), anyInt()))
                .thenReturn(Optional.of(progress));

        // Leaving member had weekly_goal=4, contributed 2 sessions
        GroupMemberGoalSnapshot leavingSnapshot = activeSnapshotWith(USER_B, 2);
        leavingSnapshot.setWeeklyGoal(4);
        when(snapshotRepo.findByGroupIdAndUserIdAndIsoYearAndIsoWeek(eq(GROUP_ID), eq(USER_B), anyInt(), anyInt()))
                .thenReturn(Optional.of(leavingSnapshot));

        // Remaining active snapshot: USER_A with 3 sessions
        GroupMemberGoalSnapshot remainingSnapshot = activeSnapshotWith(USER_A, 3);
        remainingSnapshot.setIsActive(true);
        // After marking leavingSnapshot inactive, simulate the updated list
        when(snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), anyInt(), anyInt()))
                .thenAnswer(inv -> {
                    leavingSnapshot.setIsActive(false);
                    return List.of(leavingSnapshot, remainingSnapshot);
                });
        when(snapshotRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(progressRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.onMemberLeftGroup(GROUP_ID, USER_B);

        assertEquals(8, progress.getTargetSessions()); // 12 - 4
        assertEquals(3, progress.getSessionsLogged()); // only USER_A's active sessions
    }

    @Test
    void mid_week_tier_no_coins_awarded() {
        // Verify CoinService is NEVER called during recordSessionForUser.
        // Coins are only awarded by the Sunday lock job.
        GroupWeeklyProgress progress = progressWithTarget(4);
        when(progressRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), anyInt(), anyInt()))
                .thenReturn(Optional.of(progress));
        when(snapshotRepo.findByGroupIdAndUserIdAndIsoYearAndIsoWeek(eq(GROUP_ID), eq(USER_A), anyInt(), anyInt()))
                .thenReturn(Optional.of(activeSnapshotWith(USER_A, 3)));
        when(snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), anyInt(), anyInt()))
                .thenReturn(List.of(activeSnapshotWith(USER_A, 4)));
        when(progressRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(snapshotRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // GroupProgressService has no CoinService dependency — this test confirms
        // the service can be constructed without it (compile-time check).
        // Additionally verify no unexpected interactions on feedItemRepo for same tier.
        progress.setCurrentTier("GOLD"); // already GOLD — no upgrade event

        service.recordSessionForUser(USER_A, LocalDate.of(2026, 4, 21));

        verify(feedItemRepo, never()).save(any()); // no upgrade event written
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static GroupWeeklyProgress progressWithTarget(int target) {
        GroupWeeklyProgress p = new GroupWeeklyProgress();
        p.setGroupId(GROUP_ID);
        p.setTargetSessions(target);
        p.setSessionsLogged(0);
        p.setCurrentTier("NONE");
        return p;
    }

    private static GroupMemberGoalSnapshot activeSnapshotWith(UUID userId, int contributed) {
        GroupMemberGoalSnapshot s = new GroupMemberGoalSnapshot();
        s.setGroupId(GROUP_ID);
        s.setUserId(userId);
        s.setWeeklyGoal(4);
        s.setSessionsContributed(contributed);
        s.setIsActive(true);
        s.setJoinedThisWeek(false);
        s.setLeftThisWeek(false);
        return s;
    }
}
