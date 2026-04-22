package com.fittribe.api.fitnesssummary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.SessionFeedback;
import com.fittribe.api.entity.UserFitnessSummary;
import com.fittribe.api.repository.SessionFeedbackRepository;
import com.fittribe.api.repository.UserFitnessSummaryRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FitnessSummaryService}.
 *
 * <p>JDK 25 + Mockito limitation: concrete Spring classes ({@code JdbcTemplate},
 * {@code FitnessSummaryService}) cannot be mocked via byte-buddy instrumentation.
 * Strategy:
 * <ul>
 *   <li>Pass {@code null} for {@code JdbcTemplate} in tests that don't exercise JDBC paths.</li>
 *   <li>Use {@link TestableJdbcTemplate} (a hand-rolled subclass) for nightly-job tests.</li>
 *   <li>{@code @Mock} is used only for Spring Data repository <em>interfaces</em>
 *       (which use JDK proxies and work on JDK 25).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FitnessSummaryServiceTest {

    // Interfaces → JDK-proxy mocks, work fine on JDK 25
    @Mock UserFitnessSummaryRepository  summaryRepo;
    @Mock WorkoutSessionRepository      sessionRepo;
    @Mock SessionFeedbackRepository     feedbackRepo;

    /** Real mapper — used for round-trip serialisation assertions. */
    final ObjectMapper realMapper = new ObjectMapper();

    /**
     * Service created with null JdbcTemplate.
     * Safe for all tests that exercise {@code modalRating}, {@code trendLabel},
     * and {@code getSummary} — none of those paths touch JDBC.
     */
    FitnessSummaryService service;

    @BeforeEach
    void setUp() {
        service = new FitnessSummaryService(
                null, summaryRepo, sessionRepo, feedbackRepo, realMapper);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. Threshold label tests (static factory methods on record types)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class MuscleVolumeLabelTests {

        @Test void zero_sets_is_low()      { assertEquals("low",      FitnessSummary.MuscleVolume.of(0).label()); }
        @Test void below_10_is_low()       { assertEquals("low",      FitnessSummary.MuscleVolume.of(5).label()); }
        @Test void nine_sets_is_low()      { assertEquals("low",      FitnessSummary.MuscleVolume.of(9).label()); }
        @Test void ten_sets_is_moderate()  { assertEquals("moderate", FitnessSummary.MuscleVolume.of(10).label()); }
        @Test void fifteen_is_moderate()   { assertEquals("moderate", FitnessSummary.MuscleVolume.of(15).label()); }
        @Test void twenty_is_moderate()    { assertEquals("moderate", FitnessSummary.MuscleVolume.of(20).label()); }
        @Test void twenty_one_is_high()    { assertEquals("high",     FitnessSummary.MuscleVolume.of(21).label()); }
        @Test void thirty_is_high()        { assertEquals("high",     FitnessSummary.MuscleVolume.of(30).label()); }

        @Test void of_sets_matches_label_field() {
            var v = FitnessSummary.MuscleVolume.of(12);
            assertEquals(12, v.sets());
            assertEquals("moderate", v.label());
        }
    }

    @Nested
    class WeeklyConsistencyLabelTests {

        @Test void well_below_50pct_is_low()   { assertEquals("low",  FitnessSummary.WeeklyConsistency.labelFor(1.0, 4)); }
        @Test void just_below_50pct_is_low()   { assertEquals("low",  FitnessSummary.WeeklyConsistency.labelFor(1.9, 4)); }
        @Test void exactly_50pct_is_fair()     { assertEquals("fair", FitnessSummary.WeeklyConsistency.labelFor(2.0, 4)); }
        @Test void sixty_pct_is_fair()         { assertEquals("fair", FitnessSummary.WeeklyConsistency.labelFor(2.4, 4)); }
        @Test void eighty_pct_is_good()        { assertEquals("good", FitnessSummary.WeeklyConsistency.labelFor(3.2, 4)); }
        @Test void exactly_100pct_is_good()    { assertEquals("good", FitnessSummary.WeeklyConsistency.labelFor(4.0, 4)); }
        @Test void exactly_110pct_is_good()    { assertEquals("good", FitnessSummary.WeeklyConsistency.labelFor(4.4, 4)); }
        @Test void above_110pct_is_high()      { assertEquals("high", FitnessSummary.WeeklyConsistency.labelFor(5.0, 4)); }
        @Test void zero_goal_is_unknown()      { assertEquals("unknown", FitnessSummary.WeeklyConsistency.labelFor(0.0, 0)); }

        @Test void goal_3_scales_correctly() {
            assertEquals("good", FitnessSummary.WeeklyConsistency.labelFor(2.4, 3)); // 80% → good
            assertEquals("low",  FitnessSummary.WeeklyConsistency.labelFor(1.2, 3)); // 40% → low
        }
    }

    @Nested
    class ProgressionLabelTests {

        @Test void zero_prs_is_stalled()  { assertEquals("stalled", FitnessSummary.PrActivity.labelFor(0)); }
        @Test void one_pr_is_slow()       { assertEquals("slow",    FitnessSummary.PrActivity.labelFor(1)); }
        @Test void two_prs_is_active()    { assertEquals("active",  FitnessSummary.PrActivity.labelFor(2)); }
        @Test void five_prs_is_active()   { assertEquals("active",  FitnessSummary.PrActivity.labelFor(5)); }
        @Test void ten_prs_is_active()    { assertEquals("active",  FitnessSummary.PrActivity.labelFor(10)); }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. modalRating() — package-private
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class ModalRatingTests {

        @Test
        void empty_list_returns_null() {
            assertNull(service.modalRating(List.of()));
        }

        @Test
        void single_entry_returns_that_rating() {
            assertEquals("good", service.modalRating(List.of(fb("GOOD"))));
        }

        @Test
        void most_common_wins() {
            assertEquals("hard",
                    service.modalRating(List.of(fb("HARD"), fb("HARD"), fb("GOOD"))));
        }

        @Test
        void tie_breaks_to_harder_rating() {
            // GOOD and HARD both at 1 — HARD has higher ordinal
            assertEquals("hard",
                    service.modalRating(List.of(fb("GOOD"), fb("HARD"))));
        }

        @Test
        void three_way_tie_resolves_to_hardest() {
            assertEquals("killed_me",
                    service.modalRating(List.of(fb("GOOD"), fb("HARD"), fb("KILLED_ME"))));
        }

        @Test
        void too_easy_wins_when_dominant() {
            assertEquals("too_easy",
                    service.modalRating(List.of(fb("TOO_EASY"), fb("TOO_EASY"), fb("HARD"))));
        }

        @Test
        void input_is_uppercased_before_lookup() {
            assertEquals("hard",
                    service.modalRating(List.of(fb("hard"), fb("hard"), fb("good"))));
        }

        @Test
        void unknown_rating_values_are_filtered_out() {
            assertEquals("good",
                    service.modalRating(List.of(fb("MEDIUM"), fb("good"))));
        }

        @Test
        void all_unknown_ratings_returns_null() {
            assertNull(service.modalRating(List.of(fb("MEDIUM"), fb("EXTREME"))));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. trendLabel() — package-private
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class TrendLabelTests {

        @Test void both_null_is_unknown()              { assertEquals("unknown",  service.trendLabel(null,       null)); }
        @Test void current_null_is_unknown()           { assertEquals("unknown",  service.trendLabel(null,       "good")); }
        @Test void previous_null_is_unknown()          { assertEquals("unknown",  service.trendLabel("hard",     null)); }
        @Test void same_rating_is_flat()               { assertEquals("flat",     service.trendLabel("good",     "good")); }
        @Test void all_four_same_is_flat()             { assertEquals("flat",     service.trendLabel("killed_me","killed_me")); }
        @Test void good_to_hard_is_climbing()          { assertEquals("climbing", service.trendLabel("hard",     "good")); }
        @Test void hard_to_killed_me_is_climbing()     { assertEquals("climbing", service.trendLabel("killed_me","hard")); }
        @Test void too_easy_to_good_is_climbing()      { assertEquals("climbing", service.trendLabel("good",     "too_easy")); }
        @Test void hard_to_good_is_dropping()          { assertEquals("dropping", service.trendLabel("good",     "hard")); }
        @Test void killed_me_to_hard_is_dropping()     { assertEquals("dropping", service.trendLabel("hard",     "killed_me")); }
        @Test void good_to_too_easy_is_dropping()      { assertEquals("dropping", service.trendLabel("too_easy", "good")); }
        @Test void bogus_current_is_unknown()          { assertEquals("unknown",  service.trendLabel("BOGUS",    "good")); }
        @Test void bogus_previous_is_unknown()         { assertEquals("unknown",  service.trendLabel("good",     "BOGUS")); }

        @Test
        void labels_produced_by_modalRating_are_lowercase_and_work_with_trendLabel() {
            // modalRating returns lowercase; trendLabel uppercases internally → no case mismatch
            assertEquals("climbing", service.trendLabel("hard", "good"));
            assertEquals("climbing", service.trendLabel("HARD", "GOOD"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. RPE trend < 2 threshold documentation
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class RpeTrendUnknownThresholdTest {

        @Test
        void zero_entries_gives_null_modal_which_maps_to_unknown_trend() {
            // computeRpeTrend guard: currentSize < 2 || previousSize < 2 → "unknown"
            // With 0 entries: modalRating returns null → trendLabel(null, null) = "unknown"
            assertNull(service.modalRating(List.of()));
            assertEquals("unknown", service.trendLabel(null, null));
        }

        @Test
        void one_entry_per_window_returns_non_null_modal_but_service_would_return_unknown() {
            // modalRating works with 1 entry — the < 2 guard is at computeRpeTrend level
            assertNotNull(service.modalRating(List.of(fb("HARD"))));
            // The guard ensures the service returns "unknown" before modalRating is ever called
        }

        @Test
        void two_entries_produce_valid_modal_sufficient_for_trend_computation() {
            String modal = service.modalRating(List.of(fb("GOOD"), fb("HARD")));
            assertNotNull(modal);
            assertEquals("hard", modal); // tie → harder wins
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. getSummary()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class GetSummaryTests {

        @Test
        void returns_empty_when_no_row_exists() {
            UUID userId = UUID.randomUUID();
            when(summaryRepo.findById(userId)).thenReturn(Optional.empty());
            assertTrue(service.getSummary(userId).isEmpty());
        }

        @Test
        void returns_deserialized_summary_when_row_exists() throws Exception {
            UUID userId = UUID.randomUUID();
            FitnessSummary original = fullSummary();
            String json = realMapper.writeValueAsString(original);

            UserFitnessSummary entity = new UserFitnessSummary();
            entity.setUserId(userId);
            entity.setSummary(json);

            when(summaryRepo.findById(userId)).thenReturn(Optional.of(entity));

            Optional<FitnessSummary> result = service.getSummary(userId);
            assertTrue(result.isPresent());
            FitnessSummary got = result.get();
            assertEquals(original.version(), got.version());
            assertEquals(original.rpeTrend().trendLabel(), got.rpeTrend().trendLabel());
            assertEquals(original.prActivity().progressionLabel(), got.prActivity().progressionLabel());
        }

        @Test
        void round_trip_preserves_all_fields() throws Exception {
            UUID userId = UUID.randomUUID();
            FitnessSummary full = fullSummary();
            String json = realMapper.writeValueAsString(full);

            UserFitnessSummary entity = new UserFitnessSummary();
            entity.setUserId(userId);
            entity.setSummary(json);

            when(summaryRepo.findById(userId)).thenReturn(Optional.of(entity));

            FitnessSummary got = service.getSummary(userId).orElseThrow();

            assertEquals(1, got.mainLiftStrength().size());
            assertEquals("bench-press", got.mainLiftStrength().get(0).exerciseId());
            assertEquals(60.0, got.mainLiftStrength().get(0).maxKg());
            assertEquals("moderate", got.muscleGroupVolume().get("Chest").label());
            assertEquals("good",     got.weeklyConsistency().consistencyLabel());
            assertEquals("climbing", got.rpeTrend().trendLabel());
            assertEquals("active",   got.prActivity().progressionLabel());
            assertEquals(2, (int) got.lastTrainedByMuscle().get("Chest"));
        }

        @Test
        void returns_empty_when_json_is_malformed() {
            UUID userId = UUID.randomUUID();
            UserFitnessSummary entity = new UserFitnessSummary();
            entity.setUserId(userId);
            entity.setSummary("not-valid-json{{{");

            when(summaryRepo.findById(userId)).thenReturn(Optional.of(entity));

            assertTrue(service.getSummary(userId).isEmpty(),
                    "Malformed JSON must degrade to empty Optional, not throw");
        }

        @Test
        void returns_empty_when_summary_field_is_null() {
            UUID userId = UUID.randomUUID();
            UserFitnessSummary entity = new UserFitnessSummary();
            entity.setUserId(userId);
            entity.setSummary(null);

            when(summaryRepo.findById(userId)).thenReturn(Optional.of(entity));

            assertTrue(service.getSummary(userId).isEmpty(),
                    "Null JSON must degrade to empty Optional, not throw");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 6. runNightlyJob() — fault-isolation
    //    Uses TestableJdbcTemplate (hand-rolled subclass, not Mockito mock)
    //    because JdbcTemplate is a concrete class that can't be mocked on JDK 25.
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class NightlyJobIsolationTests {

        @Test
        void one_failing_user_does_not_abort_batch_processed_5_succeeded_4_failed_1() {
            List<UUID> userIds = List.of(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID());
            UUID failingId = userIds.get(2);

            FailingForOneService svc = makeFailingService(userIds, failingId);
            NightlyJobResult result = svc.runNightlyJob(Instant.now());

            assertEquals(5, result.processed(), "All 5 users must be attempted");
            assertEquals(4, result.succeeded());
            assertEquals(1, result.failed());
        }

        @Test
        void failure_of_first_user_does_not_skip_remaining_users() {
            List<UUID> userIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            FailingForOneService svc = makeFailingService(userIds, userIds.get(0));
            NightlyJobResult result = svc.runNightlyJob(Instant.now());

            assertEquals(3, result.processed());
            assertEquals(2, result.succeeded());
            assertEquals(1, result.failed());
        }

        @Test
        void failure_of_last_user_still_counts_all_previous_successes() {
            List<UUID> userIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            FailingForOneService svc = makeFailingService(userIds, userIds.get(2));
            NightlyJobResult result = svc.runNightlyJob(Instant.now());

            assertEquals(3, result.processed());
            assertEquals(2, result.succeeded());
            assertEquals(1, result.failed());
        }

        @Test
        void all_users_succeed_when_none_throw() {
            List<UUID> userIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            AlwaysSucceedsService svc = makeAlwaysSucceeds(userIds);
            NightlyJobResult result = svc.runNightlyJob(Instant.now());

            assertEquals(3, result.processed());
            assertEquals(3, result.succeeded());
            assertEquals(0, result.failed());
        }

        @Test
        void empty_active_user_list_returns_all_zeros() {
            AlwaysSucceedsService svc = makeAlwaysSucceeds(List.of());
            NightlyJobResult result = svc.runNightlyJob(Instant.now());

            assertEquals(0, result.processed());
            assertEquals(0, result.succeeded());
            assertEquals(0, result.failed());
        }

        // Helpers to build the test services with TestableJdbcTemplate

        private FailingForOneService makeFailingService(List<UUID> userIds, UUID failId) {
            // Inline mock of UserFitnessSummaryRepository (interface → JDK proxy, works on JDK 25)
            UserFitnessSummaryRepository fakeRepo =
                    Mockito.mock(UserFitnessSummaryRepository.class);
            return new FailingForOneService(
                    failId, new TestableJdbcTemplate(userIds),
                    fakeRepo, null, null, realMapper);
        }

        private AlwaysSucceedsService makeAlwaysSucceeds(List<UUID> userIds) {
            UserFitnessSummaryRepository fakeRepo =
                    Mockito.mock(UserFitnessSummaryRepository.class);
            return new AlwaysSucceedsService(
                    new TestableJdbcTemplate(userIds),
                    fakeRepo, null, null, realMapper);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    static SessionFeedback fb(String rating) {
        SessionFeedback f = new SessionFeedback();
        f.setRating(rating);
        return f;
    }

    static FitnessSummary minimalSummary() {
        return new FitnessSummary(
                1, List.of(), Map.of(),
                new FitnessSummary.WeeklyConsistency(3.0, 3, 4, "good"),
                new FitnessSummary.RpeTrend(null, null, "unknown",
                        new FitnessSummary.SampleSize(0, 0)),
                new FitnessSummary.PrActivity(0, null, "stalled"),
                Map.of());
    }

    static FitnessSummary fullSummary() {
        return new FitnessSummary(
                1,
                List.of(new FitnessSummary.MainLiftEntry(
                        "bench-press", 60.0, 8, "2026-04-20", "Chest")),
                Map.of("Chest", FitnessSummary.MuscleVolume.of(15),
                       "Back",  FitnessSummary.MuscleVolume.of(12)),
                new FitnessSummary.WeeklyConsistency(3.5, 3, 4, "good"),
                new FitnessSummary.RpeTrend("hard", "good", "climbing",
                        new FitnessSummary.SampleSize(3, 3)),
                new FitnessSummary.PrActivity(2, 5, "active"),
                Map.of("Chest", 2, "Back", 5));
    }

    // ── Hand-rolled JdbcTemplate subclass ─────────────────────────────

    /**
     * Extends {@link JdbcTemplate} without a DataSource.
     * Overrides {@code queryForList(String, Class, Object...)} to return a
     * fixed list of user IDs — the only JDBC call made by
     * {@code FitnessSummaryService.findActiveUserIds}.
     *
     * <p>Regular Java subclassing (not byte-buddy) — works on JDK 25.
     */
    static class TestableJdbcTemplate extends JdbcTemplate {
        private final List<UUID> activeUsers;

        TestableJdbcTemplate(List<UUID> activeUsers) {
            super(); // no DataSource needed — we override the specific method used
            this.activeUsers = activeUsers;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> queryForList(String sql, Class<T> elementType,
                                        @Nullable Object... args) {
            if (elementType == UUID.class) {
                return (List<T>) activeUsers;
            }
            throw new UnsupportedOperationException(
                    "TestableJdbcTemplate: unexpected queryForList call for type " + elementType);
        }
    }

    // ── Fake service subclasses ───────────────────────────────────────

    /** Throws for one specific userId; all others return a minimal valid summary. */
    private static class FailingForOneService extends FitnessSummaryService {
        private final UUID failUserId;

        FailingForOneService(UUID failUserId, JdbcTemplate jdbc,
                             UserFitnessSummaryRepository summaryRepo,
                             WorkoutSessionRepository sessionRepo,
                             SessionFeedbackRepository feedbackRepo,
                             ObjectMapper mapper) {
            super(jdbc, summaryRepo, sessionRepo, feedbackRepo, mapper);
            this.failUserId = failUserId;
        }

        @Override
        public FitnessSummary computeSummary(UUID userId, Instant asOf) {
            if (userId.equals(failUserId)) {
                throw new RuntimeException("Simulated failure for userId=" + userId);
            }
            return minimalSummary();
        }
    }

    /** Always returns a minimal valid summary for any userId. */
    private static class AlwaysSucceedsService extends FitnessSummaryService {

        AlwaysSucceedsService(JdbcTemplate jdbc,
                              UserFitnessSummaryRepository summaryRepo,
                              WorkoutSessionRepository sessionRepo,
                              SessionFeedbackRepository feedbackRepo,
                              ObjectMapper mapper) {
            super(jdbc, summaryRepo, sessionRepo, feedbackRepo, mapper);
        }

        @Override
        public FitnessSummary computeSummary(UUID userId, Instant asOf) {
            return minimalSummary();
        }
    }
}
