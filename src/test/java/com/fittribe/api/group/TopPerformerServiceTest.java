package com.fittribe.api.group;

import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.entity.GroupWeeklyTopPerformer;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.UserWeeklyStats;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.GroupRepository;
import com.fittribe.api.repository.GroupWeeklyTopPerformerRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.UserWeeklyStatsRepository;
import com.fittribe.api.service.EffortScoreCalculator;
import com.fittribe.api.service.TopPerformerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TopPerformerServiceTest {

    private GroupMemberRepository             memberRepo;
    private GroupRepository                   groupRepo;
    private UserWeeklyStatsRepository         statsRepo;
    private GroupWeeklyTopPerformerRepository topPerformerRepo;
    private EffortScoreCalculator             effortScoreCalculator;
    private UserRepository                    userRepo;
    private TopPerformerService               service;

    private static final UUID GROUP_ID  = UUID.randomUUID();
    private static final UUID USER_A    = UUID.randomUUID();
    private static final UUID USER_B    = UUID.randomUUID();
    // ISO week 2026-W17 starts Monday 2026-04-20
    private static final int  YEAR      = 2026;
    private static final int  WEEK      = 17;
    private static final LocalDate WEEK_START = LocalDate.of(2026, 4, 20);

    @BeforeEach
    void setUp() {
        memberRepo          = mock(GroupMemberRepository.class);
        groupRepo           = mock(GroupRepository.class);
        statsRepo           = mock(UserWeeklyStatsRepository.class);
        topPerformerRepo    = mock(GroupWeeklyTopPerformerRepository.class);
        effortScoreCalculator = mock(EffortScoreCalculator.class);
        userRepo            = mock(UserRepository.class);

        service = new TopPerformerService(memberRepo, groupRepo, statsRepo,
                topPerformerRepo, effortScoreCalculator, userRepo);

        // Default: no existing top performer for this week
        when(topPerformerRepo.findByGroupIdAndIsoYearAndIsoWeekAndDimension(
                any(), anyInt(), anyInt(), anyString()))
                .thenReturn(Optional.empty());

        // Default: no rotation history
        when(topPerformerRepo.findByWinnerUserIdAndGroupIdAndDimensionOrderByIsoYearDescIsoWeekDesc(
                any(), any(), anyString()))
                .thenReturn(List.of());

        // Default: users exist
        User userA = new User(); userA.setDisplayName("Alice");
        User userB = new User(); userB.setDisplayName("Bob");
        when(userRepo.findById(USER_A)).thenReturn(Optional.of(userA));
        when(userRepo.findById(USER_B)).thenReturn(Optional.of(userB));

        when(topPerformerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void no_qualifying_members_skips_insert() {
        when(memberRepo.findByGroupId(GROUP_ID)).thenReturn(List.of(memberFor(USER_A), memberFor(USER_B)));
        when(effortScoreCalculator.calculateEffortScore(USER_A, WEEK_START)).thenReturn(0);
        when(effortScoreCalculator.calculateEffortScore(USER_B, WEEK_START)).thenReturn(0);

        service.computeForGroup(GROUP_ID, YEAR, WEEK);

        verify(topPerformerRepo, never()).save(any());
    }

    @Test
    void highest_scorer_wins() {
        when(memberRepo.findByGroupId(GROUP_ID)).thenReturn(List.of(memberFor(USER_A), memberFor(USER_B)));
        when(effortScoreCalculator.calculateEffortScore(USER_A, WEEK_START)).thenReturn(72);
        when(effortScoreCalculator.calculateEffortScore(USER_B, WEEK_START)).thenReturn(55);
        when(statsRepo.findByUserIdAndWeekStartDate(USER_A, WEEK_START))
                .thenReturn(Optional.of(statsFor(USER_A, 5, 1)));

        service.computeForGroup(GROUP_ID, YEAR, WEEK);

        verify(topPerformerRepo).save(argThat(tp ->
                tp.getWinnerUserId().equals(USER_A) && tp.getScoreValue() == 72));
    }

    @Test
    void idempotent_skips_if_row_already_exists() {
        when(topPerformerRepo.findByGroupIdAndIsoYearAndIsoWeekAndDimension(
                GROUP_ID, YEAR, WEEK, "EFFORT"))
                .thenReturn(Optional.of(new GroupWeeklyTopPerformer()));

        service.computeForGroup(GROUP_ID, YEAR, WEEK);

        verify(memberRepo, never()).findByGroupId(any());
        verify(topPerformerRepo, never()).save(any());
    }

    @Test
    void rotation_rule_same_user_wins_1_week_can_still_win() {
        // USER_A won last week only — not blocked
        GroupWeeklyTopPerformer lastWin = winFor(USER_A, YEAR, WEEK - 1);
        when(topPerformerRepo.findByWinnerUserIdAndGroupIdAndDimensionOrderByIsoYearDescIsoWeekDesc(
                USER_A, GROUP_ID, "EFFORT"))
                .thenReturn(List.of(lastWin));

        when(memberRepo.findByGroupId(GROUP_ID)).thenReturn(List.of(memberFor(USER_A)));
        when(effortScoreCalculator.calculateEffortScore(USER_A, WEEK_START)).thenReturn(80);
        when(statsRepo.findByUserIdAndWeekStartDate(USER_A, WEEK_START))
                .thenReturn(Optional.of(statsFor(USER_A, 4, 0)));

        service.computeForGroup(GROUP_ID, YEAR, WEEK);

        verify(topPerformerRepo).save(argThat(tp -> tp.getWinnerUserId().equals(USER_A)));
    }

    @Test
    void rotation_rule_same_user_wins_2_consecutive_weeks_is_blocked() {
        // USER_A won W15 and W16 — should be blocked from W17, USER_B wins instead
        GroupWeeklyTopPerformer win1 = winFor(USER_A, YEAR, WEEK - 1); // W16
        GroupWeeklyTopPerformer win2 = winFor(USER_A, YEAR, WEEK - 2); // W15
        when(topPerformerRepo.findByWinnerUserIdAndGroupIdAndDimensionOrderByIsoYearDescIsoWeekDesc(
                USER_A, GROUP_ID, "EFFORT"))
                .thenReturn(List.of(win1, win2));

        when(memberRepo.findByGroupId(GROUP_ID)).thenReturn(List.of(memberFor(USER_A), memberFor(USER_B)));
        when(effortScoreCalculator.calculateEffortScore(USER_A, WEEK_START)).thenReturn(90);
        when(effortScoreCalculator.calculateEffortScore(USER_B, WEEK_START)).thenReturn(70);
        when(statsRepo.findByUserIdAndWeekStartDate(USER_B, WEEK_START))
                .thenReturn(Optional.of(statsFor(USER_B, 4, 0)));

        service.computeForGroup(GROUP_ID, YEAR, WEEK);

        verify(topPerformerRepo).save(argThat(tp -> tp.getWinnerUserId().equals(USER_B)));
    }

    @Test
    void rotation_rule_all_blocked_inserts_nothing() {
        // Only one member and they are blocked
        GroupWeeklyTopPerformer win1 = winFor(USER_A, YEAR, WEEK - 1);
        GroupWeeklyTopPerformer win2 = winFor(USER_A, YEAR, WEEK - 2);
        when(topPerformerRepo.findByWinnerUserIdAndGroupIdAndDimensionOrderByIsoYearDescIsoWeekDesc(
                USER_A, GROUP_ID, "EFFORT"))
                .thenReturn(List.of(win1, win2));

        when(memberRepo.findByGroupId(GROUP_ID)).thenReturn(List.of(memberFor(USER_A)));
        when(effortScoreCalculator.calculateEffortScore(USER_A, WEEK_START)).thenReturn(85);

        service.computeForGroup(GROUP_ID, YEAR, WEEK);

        verify(topPerformerRepo, never()).save(any());
    }

    @Test
    void metric_label_includes_sessions_and_prs() {
        when(memberRepo.findByGroupId(GROUP_ID)).thenReturn(List.of(memberFor(USER_A)));
        when(effortScoreCalculator.calculateEffortScore(USER_A, WEEK_START)).thenReturn(72);

        UserWeeklyStats stats = statsFor(USER_A, 5, 2);
        when(statsRepo.findByUserIdAndWeekStartDate(USER_A, WEEK_START)).thenReturn(Optional.of(stats));

        service.computeForGroup(GROUP_ID, YEAR, WEEK);

        verify(topPerformerRepo).save(argThat(tp ->
                tp.getMetricLabel() != null
                && tp.getMetricLabel().contains("72")
                && tp.getMetricLabel().contains("5 sessions")
                && tp.getMetricLabel().contains("2 PRs")));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static GroupMember memberFor(UUID userId) {
        GroupMember m = new GroupMember();
        m.setGroupId(GROUP_ID);
        m.setUserId(userId);
        m.setRole("MEMBER");
        return m;
    }

    private static UserWeeklyStats statsFor(UUID userId, int sessions, int prs) {
        UserWeeklyStats s = new UserWeeklyStats();
        s.setUserId(userId);
        s.setWeekStartDate(WEEK_START);
        s.setSessionsCount(sessions);
        s.setPrsHit(prs);
        s.setWeeklyGoalTarget(4);
        s.setTotalVolumeKg(new BigDecimal("1000"));
        return s;
    }

    private static GroupWeeklyTopPerformer winFor(UUID userId, int isoYear, int isoWeek) {
        GroupWeeklyTopPerformer tp = new GroupWeeklyTopPerformer();
        tp.setGroupId(GROUP_ID);
        tp.setWinnerUserId(userId);
        tp.setIsoYear(isoYear);
        tp.setIsoWeek(isoWeek);
        tp.setDimension("EFFORT");
        tp.setScoreValue(80);
        return tp;
    }
}
