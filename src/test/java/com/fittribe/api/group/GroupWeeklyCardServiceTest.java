package com.fittribe.api.group;

import com.fittribe.api.entity.*;
import com.fittribe.api.repository.*;
import com.fittribe.api.service.CoinService;
import com.fittribe.api.service.GroupWeeklyCardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupWeeklyCardServiceTest {

    private GroupWeeklyProgressRepository     progressRepo;
    private GroupWeeklyCardRepository         cardRepo;
    private GroupMemberGoalSnapshotRepository snapshotRepo;
    private GroupRepository                   groupRepo;
    private CoinService                       coinService;
    private JdbcTemplate                      jdbc;

    private GroupWeeklyCardService service;

    private static final UUID GROUP_ID  = UUID.randomUUID();
    private static final UUID USER_A    = UUID.randomUUID();
    private static final UUID USER_B    = UUID.randomUUID();
    private static final int  ISO_YEAR  = 2026;
    private static final int  ISO_WEEK  = 17;

    @BeforeEach
    void setUp() {
        progressRepo = mock(GroupWeeklyProgressRepository.class);
        cardRepo     = mock(GroupWeeklyCardRepository.class);
        snapshotRepo = mock(GroupMemberGoalSnapshotRepository.class);
        groupRepo    = mock(GroupRepository.class);
        coinService  = mock(CoinService.class);
        jdbc         = mock(JdbcTemplate.class);

        service = new GroupWeeklyCardService(progressRepo, cardRepo, snapshotRepo,
                groupRepo, coinService, jdbc);

        when(jdbc.queryForList(anyString(), eq(UUID.class))).thenReturn(List.of(GROUP_ID));
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group()));
        when(groupRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cardRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(progressRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Previous week: no card (streak starts at 1)
        when(cardRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), anyInt(), anyInt()))
                .thenReturn(Optional.empty());
    }

    @Test
    void lock_creates_card_with_correct_tier() {
        GroupWeeklyProgress progress = bronzeProgress(GROUP_ID, 7, 10);
        when(progressRepo.findByIsoYearAndIsoWeekAndLockedAtIsNull(ISO_YEAR, ISO_WEEK))
                .thenReturn(List.of(progress));
        when(snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), eq(ISO_YEAR), eq(ISO_WEEK)))
                .thenReturn(List.of(activeSnapshot(USER_A, 4), activeSnapshot(USER_B, 3)));

        int count = service.lockWeekForAllGroups(ISO_YEAR, ISO_WEEK);

        assertEquals(1, count);
        verify(cardRepo).save(argThat(c ->
                "BRONZE".equals(c.getFinalTier()) && c.getSessionsLogged() == 7 && c.getTargetSessions() == 10));
    }

    @Test
    void lock_awards_coins_to_contributors_only() {
        GroupWeeklyProgress progress = bronzeProgress(GROUP_ID, 7, 10);
        when(progressRepo.findByIsoYearAndIsoWeekAndLockedAtIsNull(ISO_YEAR, ISO_WEEK))
                .thenReturn(List.of(progress));

        // USER_A contributed, USER_B did not (sessions_contributed=0, should not receive coins)
        GroupMemberGoalSnapshot nonContributor = activeSnapshot(USER_B, 0);
        when(snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), eq(ISO_YEAR), eq(ISO_WEEK)))
                .thenReturn(List.of(activeSnapshot(USER_A, 4), nonContributor));

        service.lockWeekForAllGroups(ISO_YEAR, ISO_WEEK);

        verify(coinService).awardCoins(eq(USER_A), eq(25), eq("GROUP_TIER_EARNED"), anyString(), anyString());
        verify(coinService, never()).awardCoins(eq(USER_B), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void lock_skips_groups_below_bronze() {
        GroupWeeklyProgress progress = noneProgress(GROUP_ID);
        when(progressRepo.findByIsoYearAndIsoWeekAndLockedAtIsNull(ISO_YEAR, ISO_WEEK))
                .thenReturn(List.of(progress));
        when(snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        int count = service.lockWeekForAllGroups(ISO_YEAR, ISO_WEEK);

        assertEquals(0, count);
        verify(cardRepo, never()).save(any());
        verify(coinService, never()).awardCoins(any(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void lock_is_idempotent() {
        GroupWeeklyProgress progress = bronzeProgress(GROUP_ID, 7, 10);
        when(progressRepo.findByIsoYearAndIsoWeekAndLockedAtIsNull(ISO_YEAR, ISO_WEEK))
                .thenReturn(List.of(progress));
        when(snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(any(), anyInt(), anyInt()))
                .thenReturn(List.of(activeSnapshot(USER_A, 4)));

        // Simulate card already existing when lockWeekForGroup is called
        GroupWeeklyCard existingCard = new GroupWeeklyCard();
        existingCard.setFinalTier("BRONZE");
        when(cardRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), eq(ISO_YEAR), eq(ISO_WEEK)))
                .thenReturn(Optional.of(existingCard));

        service.lockWeekForAllGroups(ISO_YEAR, ISO_WEEK);

        // save should only be called for progress (to set lockedAt), not for a new card
        verify(cardRepo, never()).save(any());
        verify(coinService, never()).awardCoins(any(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    void streak_increments_on_bronze_plus() {
        GroupWeeklyProgress progress = bronzeProgress(GROUP_ID, 7, 10);
        when(progressRepo.findByIsoYearAndIsoWeekAndLockedAtIsNull(ISO_YEAR, ISO_WEEK))
                .thenReturn(List.of(progress));
        when(snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), eq(ISO_YEAR), eq(ISO_WEEK)))
                .thenReturn(List.of(activeSnapshot(USER_A, 4)));

        // Previous week had streak_at_lock=3
        GroupWeeklyCard prevCard = new GroupWeeklyCard();
        prevCard.setStreakAtLock(3);
        // Return no card for current week (not yet created), but return prev card for prev week
        when(cardRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), eq(ISO_YEAR), eq(ISO_WEEK)))
                .thenReturn(Optional.empty());
        // Previous week is ISO 2026-W16
        when(cardRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), eq(2026), eq(16)))
                .thenReturn(Optional.of(prevCard));

        service.lockWeekForAllGroups(ISO_YEAR, ISO_WEEK);

        verify(cardRepo).save(argThat(c -> c.getStreakAtLock() == 4));
        verify(groupRepo).save(argThat(g -> g.getStreak() == 4));
    }

    @Test
    void streak_resets_when_no_card_earned() {
        // Group has NONE tier — no card earned this week
        GroupWeeklyProgress progress = noneProgress(GROUP_ID);
        when(progressRepo.findByIsoYearAndIsoWeekAndLockedAtIsNull(ISO_YEAR, ISO_WEEK))
                .thenReturn(List.of(progress));
        when(snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        // No card for current week
        when(cardRepo.findByGroupIdAndIsoYearAndIsoWeek(eq(GROUP_ID), eq(ISO_YEAR), eq(ISO_WEEK)))
                .thenReturn(Optional.empty());

        service.lockWeekForAllGroups(ISO_YEAR, ISO_WEEK);

        verify(jdbc).update(contains("streak = 0"), eq(GROUP_ID));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static GroupWeeklyProgress bronzeProgress(UUID groupId, int logged, int target) {
        GroupWeeklyProgress p = new GroupWeeklyProgress();
        p.setGroupId(groupId);
        p.setIsoYear(ISO_YEAR);
        p.setIsoWeek(ISO_WEEK);
        p.setTargetSessions(target);
        p.setSessionsLogged(logged);
        p.setCurrentTier("BRONZE");
        p.setOverachiever(false);
        return p;
    }

    private static GroupWeeklyProgress noneProgress(UUID groupId) {
        GroupWeeklyProgress p = new GroupWeeklyProgress();
        p.setGroupId(groupId);
        p.setIsoYear(ISO_YEAR);
        p.setIsoWeek(ISO_WEEK);
        p.setTargetSessions(8);
        p.setSessionsLogged(2);
        p.setCurrentTier("NONE");
        return p;
    }

    private static GroupMemberGoalSnapshot activeSnapshot(UUID userId, int contributed) {
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

    private static Group group() {
        Group g = new Group();
        g.setStreak(3);
        return g;
    }
}
