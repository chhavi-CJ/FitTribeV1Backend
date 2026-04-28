package com.fittribe.api.group;

import com.fittribe.api.dto.group.LeaderboardEntryDto;
import com.fittribe.api.dto.group.LeaderboardResponseDto;
import com.fittribe.api.entity.User;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.service.EffortScoreCalculator;
import com.fittribe.api.service.GrinderCalculator;
import com.fittribe.api.service.LeaderboardService;
import com.fittribe.api.service.MostImprovedCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeaderboardServiceTest {

    private GroupMemberRepository  memberRepo;
    private UserRepository         userRepo;
    private EffortScoreCalculator  effortCalc;
    private MostImprovedCalculator mostImprovedCalc;
    private GrinderCalculator      grinderCalc;
    private LeaderboardService     service;

    private static final UUID      GROUP_ID = UUID.randomUUID();
    private static final UUID      USER_A   = UUID.randomUUID();
    private static final UUID      USER_B   = UUID.randomUUID();
    private static final UUID      USER_C   = UUID.randomUUID();
    // 2026-W17: Monday 2026-04-20
    private static final LocalDate WEEK_START = LocalDate.of(2026, 4, 20);

    @BeforeEach
    void setUp() {
        memberRepo       = mock(GroupMemberRepository.class);
        userRepo         = mock(UserRepository.class);
        effortCalc       = mock(EffortScoreCalculator.class);
        mostImprovedCalc = mock(MostImprovedCalculator.class);
        grinderCalc      = mock(GrinderCalculator.class);

        service = new LeaderboardService(memberRepo, userRepo, effortCalc, mostImprovedCalc, grinderCalc);

        // Default: three members in the group
        when(memberRepo.findUserIdsByGroupId(GROUP_ID))
                .thenReturn(List.of(USER_A, USER_B, USER_C));

        // Default: users with display names
        User ua = new User(); ua.setId(USER_A); ua.setDisplayName("Alice Sharma");
        User ub = new User(); ub.setId(USER_B); ub.setDisplayName("Bob Mehta");
        User uc = new User(); uc.setId(USER_C); uc.setDisplayName("Carol Singh");
        when(userRepo.findByIdIn(anyList())).thenReturn(List.of(ua, ub, uc));
    }

    // ── Effort ───────────────────────────────────────────────────────────────

    @Test
    void effort_entries_sorted_by_score_desc_with_correct_rank() {
        when(effortCalc.calculateEffortScore(USER_A, WEEK_START)).thenReturn(80);
        when(effortCalc.calculateEffortScore(USER_B, WEEK_START)).thenReturn(55);
        when(effortCalc.calculateEffortScore(USER_C, WEEK_START)).thenReturn(30);

        LeaderboardResponseDto dto = service.getLeaderboard(GROUP_ID, "effort", WEEK_START, USER_B);

        assertEquals("effort", dto.getType());
        assertEquals(2026, dto.getIsoYear());
        assertEquals(17, dto.getIsoWeek());

        List<LeaderboardEntryDto> entries = dto.getEntries();
        assertEquals(3, entries.size());
        assertEquals(USER_A, entries.get(0).getUserId());
        assertEquals(1, entries.get(0).getRank());
        assertEquals(80, entries.get(0).getScore());

        assertEquals(USER_B, entries.get(1).getUserId());
        assertEquals(2, entries.get(1).getRank());
        assertTrue(entries.get(1).isCurrentUser(), "USER_B is the currentUserId");

        assertEquals(USER_C, entries.get(2).getUserId());
        assertEquals(3, entries.get(2).getRank());
    }

    // ── Improvement ──────────────────────────────────────────────────────────

    @Test
    void improvement_ranked_entries_sorted_by_internal_pct_desc() {
        // USER_A: RANKED 50%, USER_B: RANKED 80%, USER_C: NEW_MEMBER
        when(mostImprovedCalc.calculate(USER_A, WEEK_START))
                .thenReturn(MostImprovedCalculator.MostImprovedResult.ranked(USER_A, 50, false, 50));
        when(mostImprovedCalc.calculate(USER_B, WEEK_START))
                .thenReturn(MostImprovedCalculator.MostImprovedResult.ranked(USER_B, 80, false, 80));
        when(mostImprovedCalc.calculate(USER_C, WEEK_START))
                .thenReturn(MostImprovedCalculator.MostImprovedResult.newMember(USER_C));

        LeaderboardResponseDto dto = service.getLeaderboard(GROUP_ID, "improvement", WEEK_START, USER_A);
        List<LeaderboardEntryDto> entries = dto.getEntries();

        // RANKED first (sorted by internalPct desc), then unranked at bottom
        assertEquals(USER_B, entries.get(0).getUserId());
        assertEquals(1, entries.get(0).getRank());
        assertNull(entries.get(0).getStatus(), "ranked entry status should be null per spec");

        assertEquals(USER_A, entries.get(1).getUserId());
        assertEquals(2, entries.get(1).getRank());

        // USER_C is NEW_MEMBER — no rank
        assertEquals(USER_C, entries.get(2).getUserId());
        assertNull(entries.get(2).getRank());
        assertNull(entries.get(2).getScore());
        assertEquals("NEW_MEMBER", entries.get(2).getStatus());
    }

    @Test
    void improvement_goal_not_hit_has_null_rank_and_comeback_is_excluded() {
        // USER_A: RANKED 30%, USER_B: GOAL_NOT_HIT, USER_C: ROUTE_TO_COMEBACK (excluded)
        when(mostImprovedCalc.calculate(USER_A, WEEK_START))
                .thenReturn(MostImprovedCalculator.MostImprovedResult.ranked(USER_A, 30, false, 30));
        when(mostImprovedCalc.calculate(USER_B, WEEK_START))
                .thenReturn(MostImprovedCalculator.MostImprovedResult.goalNotHit(USER_B));
        when(mostImprovedCalc.calculate(USER_C, WEEK_START))
                .thenReturn(MostImprovedCalculator.MostImprovedResult.routeToComeback(USER_C));

        LeaderboardResponseDto dto = service.getLeaderboard(GROUP_ID, "improvement", WEEK_START, USER_A);
        List<LeaderboardEntryDto> entries = dto.getEntries();

        // Only 2 visible entries (ROUTE_TO_COMEBACK excluded entirely)
        assertEquals(2, entries.size());
        assertEquals(USER_A, entries.get(0).getUserId());
        assertEquals(1, entries.get(0).getRank());

        assertEquals(USER_B, entries.get(1).getUserId());
        assertNull(entries.get(1).getRank(), "GOAL_NOT_HIT has no rank");
        assertEquals("GOAL_NOT_HIT", entries.get(1).getStatus());
        assertEquals("Hit goal to be ranked", entries.get(1).getScoreDisplay());
    }

    // ── Grinder ──────────────────────────────────────────────────────────────

    @Test
    void grinder_entries_sorted_by_sessions_desc() {
        when(grinderCalc.compute(USER_A)).thenReturn(
                new GrinderCalculator.GrinderResult(USER_A, 15, new BigDecimal("8000")));
        when(grinderCalc.compute(USER_B)).thenReturn(
                new GrinderCalculator.GrinderResult(USER_B, 20, new BigDecimal("12000")));
        when(grinderCalc.compute(USER_C)).thenReturn(
                new GrinderCalculator.GrinderResult(USER_C, 10, new BigDecimal("5000")));

        LeaderboardResponseDto dto = service.getLeaderboard(GROUP_ID, "grinder", WEEK_START, USER_C);

        List<LeaderboardEntryDto> entries = dto.getEntries();
        assertEquals(USER_B, entries.get(0).getUserId());
        assertEquals(20, entries.get(0).getScore());
        assertEquals(1, entries.get(0).getRank());
        assertEquals(USER_A, entries.get(1).getUserId());
        assertEquals(USER_C, entries.get(2).getUserId());
        assertTrue(entries.get(2).isCurrentUser());
    }

    // ── Validation ───────────────────────────────────────────────────────────

    @Test
    void invalid_type_throws_validation_error() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.getLeaderboard(GROUP_ID, "xyz", WEEK_START, USER_A));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    // ── currentUser flag ─────────────────────────────────────────────────────

    @Test
    void current_user_flag_set_on_matching_entry_only() {
        when(effortCalc.calculateEffortScore(USER_A, WEEK_START)).thenReturn(70);
        when(effortCalc.calculateEffortScore(USER_B, WEEK_START)).thenReturn(85);
        when(effortCalc.calculateEffortScore(USER_C, WEEK_START)).thenReturn(60);

        LeaderboardResponseDto dto = service.getLeaderboard(GROUP_ID, "effort", WEEK_START, USER_A);

        List<LeaderboardEntryDto> entries = dto.getEntries();
        long currentUserCount = entries.stream().filter(LeaderboardEntryDto::isCurrentUser).count();
        assertEquals(1, currentUserCount, "exactly one entry should be flagged as currentUser");
        assertTrue(entries.stream()
                .filter(LeaderboardEntryDto::isCurrentUser)
                .allMatch(e -> e.getUserId().equals(USER_A)));
    }
}
