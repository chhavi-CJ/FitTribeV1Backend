package com.fittribe.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.*;
import com.fittribe.api.repository.FeedItemRepository;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FeedEventWriterTest {

    private FeedItemRepository    feedRepo;
    private GroupMemberRepository memberRepo;
    private UserRepository        userRepo;
    private FeedEventWriter       writer;

    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID GROUP_A   = UUID.randomUUID();
    private static final UUID GROUP_B   = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        feedRepo   = mock(FeedItemRepository.class);
        memberRepo = mock(GroupMemberRepository.class);
        userRepo   = mock(UserRepository.class);

        writer = new FeedEventWriter(feedRepo, memberRepo, userRepo, new ObjectMapper());

        when(feedRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User user = new User();
        user.setDisplayName("Alice Smith");
        when(userRepo.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    // ── helpers ────────────────────────────────────────────────────────

    private GroupMember memberOf(UUID groupId) {
        GroupMember gm = new GroupMember();
        gm.setUserId(USER_ID);
        gm.setGroupId(groupId);
        return gm;
    }

    private WorkoutSession session(Integer durationMins, BigDecimal totalVolumeKg, Integer totalSets) {
        WorkoutSession s = new WorkoutSession();
        s.setUserId(USER_ID);
        s.setDurationMins(durationMins);
        s.setTotalVolumeKg(totalVolumeKg);
        s.setTotalSets(totalSets);
        return s;
    }

    private PrEvent pr(String exerciseId, String category, double deltaKg,
                       double newBestKg, int newBestReps) {
        PrEvent pr = new PrEvent();
        pr.setExerciseId(exerciseId);
        pr.setPrCategory(category);
        Map<String, Object> newBest = new LinkedHashMap<>();
        newBest.put("weight_kg", newBestKg);
        newBest.put("reps", newBestReps);
        Map<String, Object> vp = new LinkedHashMap<>();
        vp.put("delta_kg", deltaKg);
        vp.put("new_best", newBest);
        pr.setValuePayload(vp);
        return pr;
    }

    private PrEvent prMaxAttempt(String exerciseId, double weightKg, int reps) {
        PrEvent pr = new PrEvent();
        pr.setExerciseId(exerciseId);
        pr.setPrCategory("MAX_ATTEMPT");
        Map<String, Object> vp = new LinkedHashMap<>();
        vp.put("weight_kg", weightKg);
        vp.put("reps", reps);
        pr.setValuePayload(vp);
        return pr;
    }

    private List<FeedItem> captureAllSaves() {
        ArgumentCaptor<FeedItem> cap = ArgumentCaptor.forClass(FeedItem.class);
        verify(feedRepo, atLeastOnce()).save(cap.capture());
        return cap.getAllValues();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseEventData(FeedItem fi) {
        try {
            return new ObjectMapper().readValue(fi.getEventData(), Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // A. writeWorkoutFinished
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void workout_finished_writes_one_row_per_group() {
        when(memberRepo.findByUserId(USER_ID))
                .thenReturn(List.of(memberOf(GROUP_A), memberOf(GROUP_B)));

        writer.writeWorkoutFinished(session(45, new BigDecimal("847"), 12),
                List.of("CHEST", "BACK"), new BigDecimal("80"));

        List<FeedItem> saved = captureAllSaves();
        assertEquals(2, saved.size());
        assertTrue(saved.stream().anyMatch(f -> GROUP_A.equals(f.getGroupId())));
        assertTrue(saved.stream().anyMatch(f -> GROUP_B.equals(f.getGroupId())));
        saved.forEach(f -> assertEquals("WORKOUT_FINISHED", f.getType()));
    }

    @Test
    void workout_finished_body_format() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        writer.writeWorkoutFinished(session(30, new BigDecimal("500"), 10),
                List.of("LEGS"), null);

        FeedItem fi = captureAllSaves().get(0);
        assertEquals("Alice finished a workout · 30 min · 500 vol", fi.getBody());
    }

    @Test
    void workout_finished_volume_short_format_thousands() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        writer.writeWorkoutFinished(session(47, new BigDecimal("2840"), 14),
                List.of(), null);

        FeedItem fi = captureAllSaves().get(0);
        assertTrue(fi.getBody().contains("2.8k vol"),
                "Expected '2.8k vol' in body but got: " + fi.getBody());
    }

    @Test
    void workout_finished_volume_exact_thousand_no_decimal() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        writer.writeWorkoutFinished(session(60, new BigDecimal("12000"), 20),
                List.of(), null);

        FeedItem fi = captureAllSaves().get(0);
        assertTrue(fi.getBody().contains("12k vol"),
                "Expected '12k vol' in body but got: " + fi.getBody());
    }

    @Test
    void workout_finished_event_data_has_all_keys() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        WorkoutSession s = session(45, new BigDecimal("847"), 12);
        writer.writeWorkoutFinished(s, List.of("CHEST"), new BigDecimal("100"));

        Map<String, Object> data = parseEventData(captureAllSaves().get(0));
        assertTrue(data.containsKey("durationMins"));
        assertTrue(data.containsKey("totalVolumeKg"));
        assertTrue(data.containsKey("muscleGroups"));
        assertTrue(data.containsKey("topLiftKg"));
        assertTrue(data.containsKey("sets"));
    }

    @Test
    void workout_finished_zero_groups_no_save() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of());

        writer.writeWorkoutFinished(session(30, new BigDecimal("200"), 8),
                List.of(), null);

        verify(feedRepo, never()).save(any());
    }

    // ═══════════════════════════════════════════════════════════════════
    // B. writePrRoundup
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void pr_roundup_single_pr_body_with_kg() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        PrEvent p = pr("bench-press", "WEIGHT_PR", 2.5, 82.5, 8);
        writer.writePrRoundup(USER_ID, SESSION_ID, List.of(p),
                Map.of("bench-press", "Bench Press"));

        FeedItem fi = captureAllSaves().get(0);
        assertEquals("Alice hit a Bench Press PR — 82.5 kg", fi.getBody());
    }

    @Test
    void pr_roundup_single_pr_trailing_zero_stripped() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        PrEvent p = pr("squat", "WEIGHT_PR", 5.0, 100.0, 5);
        writer.writePrRoundup(USER_ID, SESSION_ID, List.of(p),
                Map.of("squat", "Squat"));

        FeedItem fi = captureAllSaves().get(0);
        // 100.0 should render as "100 kg" not "100.0 kg"
        assertEquals("Alice hit a Squat PR — 100 kg", fi.getBody());
    }

    @Test
    void pr_roundup_multi_pr_body() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        List<PrEvent> prs = List.of(
                pr("bench-press", "WEIGHT_PR", 2.5, 82.5, 8),
                pr("squat", "WEIGHT_PR", 5.0, 100.0, 5));

        writer.writePrRoundup(USER_ID, SESSION_ID, prs, Map.of(
                "bench-press", "Bench Press",
                "squat", "Squat"));

        FeedItem fi = captureAllSaves().get(0);
        assertEquals("Alice hit 2 PRs in their session", fi.getBody());
    }

    @Test
    void pr_roundup_lifts_array_populated() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        PrEvent p = pr("deadlift", "WEIGHT_PR", 5.0, 140.0, 3);
        writer.writePrRoundup(USER_ID, SESSION_ID, List.of(p),
                Map.of("deadlift", "Deadlift"));

        Map<String, Object> data = parseEventData(captureAllSaves().get(0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lifts = (List<Map<String, Object>>) data.get("lifts");
        assertNotNull(lifts);
        assertEquals(1, lifts.size());
        assertEquals("Deadlift", lifts.get(0).get("exerciseName"));
        assertEquals(5.0,  ((Number) lifts.get(0).get("deltaKg")).doubleValue(), 0.01);
        assertEquals(140.0, ((Number) lifts.get(0).get("newBestKg")).doubleValue(), 0.01);
        assertEquals(3,     ((Number) lifts.get(0).get("newBestReps")).intValue());
    }

    @Test
    void pr_roundup_max_attempt_category_nulls_in_lifts() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        PrEvent p = prMaxAttempt("bench-press", 80.0, 10);
        writer.writePrRoundup(USER_ID, SESSION_ID, List.of(p),
                Map.of("bench-press", "Bench Press"));

        Map<String, Object> data = parseEventData(captureAllSaves().get(0));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lifts = (List<Map<String, Object>>) data.get("lifts");
        assertNotNull(lifts);
        assertNull(lifts.get(0).get("newBestKg"),
                "MAX_ATTEMPT has no new_best object — newBestKg should be null");
        assertNull(lifts.get(0).get("newBestReps"),
                "MAX_ATTEMPT has no new_best object — newBestReps should be null");
    }

    @Test
    void pr_roundup_empty_list_no_save() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        writer.writePrRoundup(USER_ID, SESSION_ID, List.of(), Map.of());

        verify(feedRepo, never()).save(any());
    }

    @Test
    void pr_roundup_null_list_no_save() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        writer.writePrRoundup(USER_ID, SESSION_ID, null, Map.of());

        verify(feedRepo, never()).save(any());
    }

    @Test
    void pr_roundup_zero_groups_no_save() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of());

        writer.writePrRoundup(USER_ID, SESSION_ID,
                List.of(pr("bench-press", "WEIGHT_PR", 2.5, 80.0, 8)),
                Map.of("bench-press", "Bench Press"));

        verify(feedRepo, never()).save(any());
    }

    // ═══════════════════════════════════════════════════════════════════
    // C. writeTierLocked
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void tier_locked_body_gold() {
        GroupWeeklyCard card = goldCard(GROUP_A);

        writer.writeTierLocked(card);

        FeedItem fi = captureAllSaves().get(0);
        assertEquals("Group hit Gold tier — 12/10 sessions 🥇", fi.getBody());
    }

    @Test
    void tier_locked_body_silver() {
        GroupWeeklyCard card = new GroupWeeklyCard();
        card.setGroupId(GROUP_A);
        card.setFinalTier("SILVER");
        card.setSessionsLogged(8);
        card.setTargetSessions(10);

        writer.writeTierLocked(card);

        FeedItem fi = captureAllSaves().get(0);
        assertEquals("Group hit Silver tier — 8/10 sessions 🥈", fi.getBody());
    }

    @Test
    void tier_locked_body_bronze() {
        GroupWeeklyCard card = new GroupWeeklyCard();
        card.setGroupId(GROUP_A);
        card.setFinalTier("BRONZE");
        card.setSessionsLogged(7);
        card.setTargetSessions(10);

        writer.writeTierLocked(card);

        FeedItem fi = captureAllSaves().get(0);
        assertEquals("Group hit Bronze tier — 7/10 sessions 🥉", fi.getBody());
    }

    @Test
    void tier_locked_event_data_keys() {
        writer.writeTierLocked(goldCard(GROUP_A));

        Map<String, Object> data = parseEventData(captureAllSaves().get(0));
        assertTrue(data.containsKey("tier"));
        assertTrue(data.containsKey("sessionsLogged"));
        assertTrue(data.containsKey("targetSessions"));
        assertTrue(data.containsKey("overachiever"));
        assertTrue(data.containsKey("streakAtLock"));
    }

    @Test
    void tier_locked_saves_exactly_one_row() {
        writer.writeTierLocked(goldCard(GROUP_A));

        verify(feedRepo, times(1)).save(any());
    }

    @Test
    void tier_locked_null_user_id_on_feed_item() {
        writer.writeTierLocked(goldCard(GROUP_A));

        FeedItem fi = captureAllSaves().get(0);
        assertNull(fi.getUserId(), "TIER_LOCKED is a group event — userId should be null");
    }

    // ═══════════════════════════════════════════════════════════════════
    // D. writeTopPerformerCrowned
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void top_performer_body_effort() {
        GroupWeeklyTopPerformer tp = topPerformer("EFFORT", 42, "Most consistent week");

        writer.writeTopPerformerCrowned(tp);

        FeedItem fi = captureAllSaves().get(0);
        assertEquals("Alice was top performer — Effort · 42 pts", fi.getBody());
    }

    @Test
    void top_performer_body_most_improved() {
        GroupWeeklyTopPerformer tp = topPerformer("MOST_IMPROVED", 30, "10% volume jump");

        writer.writeTopPerformerCrowned(tp);

        FeedItem fi = captureAllSaves().get(0);
        assertEquals("Alice was top performer — Most Improved · 30 pts", fi.getBody());
    }

    @Test
    void top_performer_body_grinder() {
        GroupWeeklyTopPerformer tp = topPerformer("GRINDER", 55, "5 sessions this week");

        writer.writeTopPerformerCrowned(tp);

        FeedItem fi = captureAllSaves().get(0);
        assertEquals("Alice was top performer — Grinder · 55 pts", fi.getBody());
    }

    @Test
    void top_performer_event_data_keys() {
        writer.writeTopPerformerCrowned(topPerformer("EFFORT", 42, "label"));

        Map<String, Object> data = parseEventData(captureAllSaves().get(0));
        assertTrue(data.containsKey("winnerUserId"));
        assertTrue(data.containsKey("dimension"));
        assertTrue(data.containsKey("scoreValue"));
        assertTrue(data.containsKey("metricLabel"));
    }

    @Test
    void top_performer_saves_exactly_one_row() {
        writer.writeTopPerformerCrowned(topPerformer("EFFORT", 42, "label"));

        verify(feedRepo, times(1)).save(any());
    }

    // ═══════════════════════════════════════════════════════════════════
    // E. writeStatusChanged
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void status_changed_body_rest() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        writer.writeStatusChanged(USER_ID, LocalDate.now(), "REST", null);

        assertEquals("Alice set today as a rest day", captureAllSaves().get(0).getBody());
    }

    @Test
    void status_changed_body_travelling() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        writer.writeStatusChanged(USER_ID, LocalDate.now(), "TRAVELLING", "REST");

        assertTrue(captureAllSaves().get(0).getBody().contains("Travelling"));
    }

    @Test
    void status_changed_body_sick() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        writer.writeStatusChanged(USER_ID, LocalDate.now(), "SICK", null);

        assertTrue(captureAllSaves().get(0).getBody().contains("Sick"));
    }

    @Test
    void status_changed_body_busy() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        writer.writeStatusChanged(USER_ID, LocalDate.now(), "BUSY", "SICK");

        assertTrue(captureAllSaves().get(0).getBody().contains("Busy"));
    }

    @Test
    void status_changed_one_row_per_group() {
        when(memberRepo.findByUserId(USER_ID))
                .thenReturn(List.of(memberOf(GROUP_A), memberOf(GROUP_B)));

        writer.writeStatusChanged(USER_ID, LocalDate.now(), "REST", null);

        verify(feedRepo, times(2)).save(any());
    }

    @Test
    void status_changed_event_data_has_all_keys() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        writer.writeStatusChanged(USER_ID, LocalDate.now(), "REST", "BUSY");

        Map<String, Object> data = parseEventData(captureAllSaves().get(0));
        assertTrue(data.containsKey("date"));
        assertTrue(data.containsKey("newStatus"));
        assertTrue(data.containsKey("previousStatus"));
        assertEquals("REST", data.get("newStatus"));
        assertEquals("BUSY", data.get("previousStatus"));
    }

    @Test
    void status_changed_previous_status_null_is_persisted() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        writer.writeStatusChanged(USER_ID, LocalDate.now(), "REST", null);

        Map<String, Object> data = parseEventData(captureAllSaves().get(0));
        assertTrue(data.containsKey("previousStatus"));
        assertNull(data.get("previousStatus"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // F. Resilience
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void workout_finished_exception_swallowed_no_rethrow() {
        when(userRepo.findById(any())).thenThrow(new RuntimeException("DB down"));
        when(memberRepo.findByUserId(any())).thenReturn(List.of(memberOf(GROUP_A)));

        // Must not throw
        assertDoesNotThrow(() ->
                writer.writeWorkoutFinished(session(30, new BigDecimal("500"), 10),
                        List.of(), null));
    }

    @Test
    void pr_roundup_exception_swallowed_no_rethrow() {
        when(userRepo.findById(any())).thenThrow(new RuntimeException("DB down"));
        when(memberRepo.findByUserId(any())).thenReturn(List.of(memberOf(GROUP_A)));

        assertDoesNotThrow(() ->
                writer.writePrRoundup(USER_ID, SESSION_ID,
                        List.of(pr("bench-press", "WEIGHT_PR", 2.5, 80.0, 8)),
                        Map.of("bench-press", "Bench Press")));
    }

    @Test
    void tier_locked_exception_swallowed_no_rethrow() {
        when(feedRepo.save(any())).thenThrow(new RuntimeException("DB down"));

        assertDoesNotThrow(() -> writer.writeTierLocked(goldCard(GROUP_A)));
    }

    @Test
    void status_changed_unknown_status_falls_through_to_default() {
        when(memberRepo.findByUserId(USER_ID)).thenReturn(List.of(memberOf(GROUP_A)));

        writer.writeStatusChanged(USER_ID, LocalDate.now(), "VACATION", null);

        FeedItem fi = captureAllSaves().get(0);
        assertEquals("Alice updated their status", fi.getBody());
    }

    // ── private helpers ────────────────────────────────────────────────

    private GroupWeeklyCard goldCard(UUID groupId) {
        GroupWeeklyCard card = new GroupWeeklyCard();
        card.setGroupId(groupId);
        card.setFinalTier("GOLD");
        card.setSessionsLogged(12);
        card.setTargetSessions(10);
        card.setOverachiever(true);
        card.setStreakAtLock(3);
        return card;
    }

    private GroupWeeklyTopPerformer topPerformer(String dimension, int score, String metricLabel) {
        GroupWeeklyTopPerformer tp = new GroupWeeklyTopPerformer();
        tp.setGroupId(GROUP_A);
        tp.setWinnerUserId(USER_ID);
        tp.setDimension(dimension);
        tp.setScoreValue(score);
        tp.setMetricLabel(metricLabel);
        return tp;
    }
}
