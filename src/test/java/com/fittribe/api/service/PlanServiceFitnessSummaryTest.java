package com.fittribe.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.config.AiPrompts;
import com.fittribe.api.fitnesssummary.FitnessSummary;
import com.fittribe.api.fitnesssummary.FitnessSummaryService;
import com.fittribe.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PlanService#buildFitnessSummaryBlock} (package-private).
 *
 * <p>Three test groups:
 * <ol>
 *   <li><b>Block content</b> — with a summary present: correct sections, correct values</li>
 *   <li><b>Fallback text</b> — without a summary row: correct fallback text returned</li>
 *   <li><b>Muscle-group filtering</b> — only exercises for target muscles appear in
 *       the strength-reference section</li>
 *   <li><b>Persona fixtures</b> — 5 representative users verify end-to-end block shape</li>
 * </ol>
 *
 * <p>Also verifies that {@link AiPrompts#DAILY_EXERCISE_USER} still contains the
 * {@code {fitnessSummaryBlock}} placeholder so a future prompt edit cannot silently
 * drop the signal.
 */
@ExtendWith(MockitoExtension.class)
class PlanServiceFitnessSummaryTest {

    // ── PlanService constructor dependencies ──────────────────────────

    @Mock UserRepository              userRepo;
    @Mock UserPlanRepository          planRepo;
    @Mock ExerciseRepository          exerciseRepo;
    @Mock WorkoutSessionRepository    sessionRepo;
    @Mock SetLogRepository            setLogRepo;
    @Mock AiInsightRepository         insightRepo;
    @Mock SessionFeedbackRepository   feedbackRepo;
    @Mock SplitTemplateDayRepository  splitTemplateDayRepo;
    @Mock DailyPlanGeneratedRepository dailyPlanRepo;
    @Mock UserDayStatusRepository     dayStatusRepo;

    // FitnessSummaryService is a concrete class — not mockable on JDK 25.
    // Use a hand-rolled fake instead.
    FakeFitnessSummaryService fitnessSummaryService = new FakeFitnessSummaryService();

    final ObjectMapper mapper = new ObjectMapper();

    PlanService planService;

    @BeforeEach
    void setUp() {
        planService = new PlanService(
                userRepo, planRepo, exerciseRepo, sessionRepo, setLogRepo,
                insightRepo, feedbackRepo, splitTemplateDayRepo, dailyPlanRepo,
                dayStatusRepo, fitnessSummaryService, mapper);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. AiPrompts template guard
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void daily_exercise_user_prompt_contains_fitnessSummaryBlock_placeholder() {
        assertTrue(AiPrompts.DAILY_EXERCISE_USER.contains("{fitnessSummaryBlock}"),
                "AiPrompts.DAILY_EXERCISE_USER must contain {fitnessSummaryBlock}. " +
                "If it was removed, re-add the placeholder and the HOW TO USE block.");
    }

    @Test
    void daily_exercise_user_prompt_contains_how_to_use_block() {
        assertTrue(AiPrompts.DAILY_EXERCISE_USER.contains("HOW TO USE THE FITNESS SUMMARY"),
                "The prompt must contain the 'HOW TO USE THE FITNESS SUMMARY' guidance block.");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. Fallback — no fitness summary row
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class FallbackTextTests {

        @Test
        void returns_fallback_when_no_summary_exists() {
            UUID userId = UUID.randomUUID();
            // no stub → fake returns Optional.empty()

            String block = planService.buildFitnessSummaryBlock(userId, List.of("Chest"));

            assertTrue(block.contains("No historical data yet"),
                    "Block should contain fallback text for new users");
        }

        @Test
        void fallback_text_mentions_fitness_level_defaults() {
            UUID userId = UUID.randomUUID();

            String block = planService.buildFitnessSummaryBlock(userId, List.of("Chest"));

            assertTrue(block.contains("fitness level defaults"),
                    "Fallback should direct AI to use fitness-level defaults");
        }

        @Test
        void fallback_is_returned_even_with_empty_muscle_group_list() {
            UUID userId = UUID.randomUUID();

            String block = planService.buildFitnessSummaryBlock(userId, List.of());

            assertTrue(block.contains("No historical data yet"));
        }

        @Test
        void fallback_is_returned_when_muscle_group_list_is_null() {
            UUID userId = UUID.randomUUID();

            String block = planService.buildFitnessSummaryBlock(userId, null);

            assertTrue(block.contains("No historical data yet"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. Block content — with a fitness summary
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class BlockContentTests {

        @Test
        void block_contains_strength_reference_section() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, chestBackSummary());

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest", "Back"));

            assertTrue(block.contains("Strength reference"),
                    "Block should contain 'Strength reference' section header");
        }

        @Test
        void block_contains_weekly_volume_section() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, chestBackSummary());

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest", "Back"));

            assertTrue(block.contains("Weekly volume"),
                    "Block should contain 'Weekly volume' section");
        }

        @Test
        void block_contains_last_trained_section() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, chestBackSummary());

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest", "Back"));

            assertTrue(block.contains("Last trained"),
                    "Block should contain 'Last trained' section");
        }

        @Test
        void block_contains_recovery_and_progression_section() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, chestBackSummary());

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest", "Back"));

            assertTrue(block.contains("Recovery & progression"),
                    "Block should contain 'Recovery & progression' section");
        }

        @Test
        void block_contains_exercise_name_for_target_muscle() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, chestBackSummary());

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest", "Back"));

            // bench-press is in chestBackSummary with muscle=Chest
            assertTrue(block.contains("bench-press"),
                    "Block should list bench-press exercise in the strength reference");
        }

        @Test
        void block_contains_max_kg_for_exercise() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, chestBackSummary());

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest"));

            assertTrue(block.contains("60.0kg"),
                    "Block should show the max kg logged for bench-press");
        }

        @Test
        void block_contains_volume_label_for_target_muscle() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, chestBackSummary());

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest"));

            assertTrue(block.contains("Chest=moderate"),
                    "Block should show 'Chest=moderate' in the volume section");
        }

        @Test
        void block_contains_rpe_trend_label() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, chestBackSummary());

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest"));

            assertTrue(block.contains("climbing"),
                    "Block should show the 'climbing' RPE trend label");
        }

        @Test
        void block_contains_progression_label() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, chestBackSummary());

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest"));

            assertTrue(block.contains("active"),
                    "Block should show the 'active' progression label");
        }

        @Test
        void block_contains_pr_count() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, chestBackSummary());

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest"));

            assertTrue(block.contains("3 PRs"),
                    "Block should show '3 PRs in last 4 weeks'");
        }

        @Test
        void block_contains_consistency_goal() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, chestBackSummary());

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest"));

            assertTrue(block.contains("goal of 4"),
                    "Block should show the weekly goal in the consistency line");
        }

        @Test
        void bodyweight_exercise_without_max_kg_is_excluded_from_strength_reference() {
            UUID userId = UUID.randomUUID();
            FitnessSummary summaryWithBodyweight = new FitnessSummary(
                    1,
                    List.of(
                            new FitnessSummary.MainLiftEntry("bench-press", 60.0, 8, "2026-04-20", "Chest"),
                            new FitnessSummary.MainLiftEntry("pull-ups", null, 10, "2026-04-20", "Back") // no kg
                    ),
                    Map.of(),
                    new FitnessSummary.WeeklyConsistency(3.0, 3, 4, "good"),
                    new FitnessSummary.RpeTrend(null, null, "unknown", new FitnessSummary.SampleSize(0, 0)),
                    new FitnessSummary.PrActivity(0, null, "stalled"),
                    Map.of());

            fitnessSummaryService.stub(userId, summaryWithBodyweight);

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest", "Back"));

            assertTrue(block.contains("bench-press"),
                    "Weighted exercise should appear");
            assertFalse(block.contains("pull-ups"),
                    "Bodyweight exercise (null maxKg) should be excluded from strength reference");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. Muscle-group filtering
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    class MuscleGroupFilteringTests {

        @Test
        void only_target_muscle_exercises_appear_in_strength_reference() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, fiveMusclesSummary());

            // Target only Chest + Back
            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest", "Back"));

            assertTrue(block.contains("bench-press"), "Chest exercise must appear");
            assertTrue(block.contains("barbell-row"), "Back exercise must appear");
            assertFalse(block.contains("squat"),       "Legs exercise must NOT appear (not in target)");
            assertFalse(block.contains("lateral-raises"), "Shoulders exercise must NOT appear");
            assertFalse(block.contains("bicep-curl"),  "Arms exercise must NOT appear");
        }

        @Test
        void non_canonical_target_name_still_matches_via_canonicalize() {
            UUID userId = UUID.randomUUID();
            FitnessSummary summary = new FitnessSummary(
                    1,
                    List.of(new FitnessSummary.MainLiftEntry(
                            "bench-press", 70.0, 5, "2026-04-20", "Chest")),
                    Map.of("Chest", FitnessSummary.MuscleVolume.of(12)),
                    new FitnessSummary.WeeklyConsistency(3.0, 3, 4, "good"),
                    new FitnessSummary.RpeTrend(null, null, "unknown",
                            new FitnessSummary.SampleSize(0, 0)),
                    new FitnessSummary.PrActivity(0, null, "stalled"),
                    Map.of("Chest", 3));

            fitnessSummaryService.stub(userId, summary);

            // "Upper Chest" canonicalises to "Chest" — should still match
            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Upper Chest"));

            assertTrue(block.contains("bench-press"),
                    "'Upper Chest' in targetMuscles should resolve to 'Chest' and match");
        }

        @Test
        void empty_target_list_shows_all_exercises_in_strength_reference() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, fiveMusclesSummary());

            String block = planService.buildFitnessSummaryBlock(userId, List.of());

            // With no filter, all exercises with kg should appear
            assertTrue(block.contains("bench-press"));
            assertTrue(block.contains("barbell-row"));
            assertTrue(block.contains("squat"));
        }

        @Test
        void target_muscles_filter_applies_to_volume_section_too() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, fiveMusclesSummary());

            // Target only Chest
            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest"));

            assertTrue(block.contains("Chest="),
                    "Chest volume should appear");
            assertFalse(block.contains("Legs="),
                    "Legs volume should NOT appear when not in target");
            assertFalse(block.contains("Back="),
                    "Back volume should NOT appear when not in target");
        }

        @Test
        void target_muscles_filter_applies_to_last_trained_section_too() {
            UUID userId = UUID.randomUUID();
            fitnessSummaryService.stub(userId, fiveMusclesSummary());

            // Target only Back
            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Back"));

            // Back=7 is in the lastTrained map
            assertTrue(block.contains("Back=7"), "Back days must appear in 'Last trained'");
            // Chest=2 must NOT appear since we only targeted Back
            assertFalse(block.contains("Chest=2"),
                    "Chest last-trained must NOT appear when only Back is targeted");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. Persona fixture tests
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Five representative users — each tests a distinct signal combination
     * that should influence AI weight/volume suggestions.
     *
     * <p>These are intentionally standalone (no OpenAI call) — they verify
     * that the block correctly surfaces the signals the AI prompt rules rely on.
     */
    @Nested
    class PersonaFixtureTests {

        /** Persona 1 — Raj: cold start, no history. */
        @Test
        void persona_raj_cold_start_returns_fallback_text() {
            UUID userId = UUID.randomUUID();
            // no stub → fake returns Optional.empty()

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest", "Shoulders", "Triceps"));

            assertTrue(block.contains("No historical data yet"),
                    "Raj has no history → fallback text expected");
            assertTrue(block.contains("fitness level defaults"),
                    "Fallback must guide AI to use fitness-level defaults");
        }

        /**
         * Persona 2 — Priya: beginner, ~2 weeks of data.
         * Expects: low volume labels (new user), no PRs, consistency "low".
         */
        @Test
        void persona_priya_beginner_2wk_shows_low_volume_and_stalled_progression() {
            UUID userId = UUID.randomUUID();
            FitnessSummary summary = new FitnessSummary(
                    1,
                    List.of(
                            new FitnessSummary.MainLiftEntry(
                                    "bench-press", 20.0, 10, "2026-04-19", "Chest"),
                            new FitnessSummary.MainLiftEntry(
                                    "lat-pulldown", 25.0, 10, "2026-04-17", "Back")
                    ),
                    Map.of("Chest", FitnessSummary.MuscleVolume.of(6),
                           "Back",  FitnessSummary.MuscleVolume.of(6)),
                    new FitnessSummary.WeeklyConsistency(1.5, 1, 4, "low"),
                    new FitnessSummary.RpeTrend(null, null, "unknown",
                            new FitnessSummary.SampleSize(1, 0)),
                    new FitnessSummary.PrActivity(0, null, "stalled"),
                    Map.of("Chest", 3, "Back", 5));

            fitnessSummaryService.stub(userId, summary);

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest", "Back", "Shoulders", "Triceps"));

            assertTrue(block.contains("Chest=low"),    "Priya chest volume should be 'low'");
            assertTrue(block.contains("stalled"),      "Priya progression should be 'stalled'");
            assertTrue(block.contains("low"),          "Priya consistency should be 'low'");
            assertTrue(block.contains("unknown"),      "Priya RPE trend should be 'unknown' (< 2 samples)");
            assertTrue(block.contains("bench-press"),  "bench-press in target muscles must appear");
        }

        /**
         * Persona 3 — Arjun: PPL split, RPE flat, Chest volume moderate.
         * Expects: flat RPE trend, Chest=moderate volume.
         */
        @Test
        void persona_arjun_ppl_flat_rpe_moderate_chest_volume() {
            UUID userId = UUID.randomUUID();
            FitnessSummary summary = new FitnessSummary(
                    1,
                    List.of(new FitnessSummary.MainLiftEntry(
                            "bench-press", 65.0, 8, "2026-04-20", "Chest")),
                    Map.of("Chest", FitnessSummary.MuscleVolume.of(14)),
                    new FitnessSummary.WeeklyConsistency(3.0, 3, 4, "good"),
                    new FitnessSummary.RpeTrend("good", "good", "flat",
                            new FitnessSummary.SampleSize(4, 4)),
                    new FitnessSummary.PrActivity(1, 12, "slow"),
                    Map.of("Chest", 2));

            fitnessSummaryService.stub(userId, summary);

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Chest", "Front Delts", "Triceps"));

            assertTrue(block.contains("Chest=moderate"), "Chest volume should be 'moderate'");
            assertTrue(block.contains("flat"),           "RPE trend should be 'flat'");
            assertTrue(block.contains("65.0kg"),         "Max weight should appear in strength ref");
        }

        /**
         * Persona 4 — Sneha: saturated back, climbing RPE.
         * Key rule from spec: climbing RPE + high Back volume → AI should bias to LOWER end.
         * Verifies the signals the AI rules act on are present in the block.
         */
        @Test
        void persona_sneha_saturated_climbing_rpe_high_back_volume() {
            UUID userId = UUID.randomUUID();
            FitnessSummary summary = new FitnessSummary(
                    1,
                    List.of(
                            new FitnessSummary.MainLiftEntry(
                                    "barbell-row", 60.0, 6, "2026-04-21", "Back"),
                            new FitnessSummary.MainLiftEntry(
                                    "lat-pulldown", 55.0, 8, "2026-04-19", "Back")
                    ),
                    Map.of("Back", FitnessSummary.MuscleVolume.of(24)),
                    new FitnessSummary.WeeklyConsistency(4.5, 4, 4, "high"),
                    new FitnessSummary.RpeTrend("hard", "good", "climbing",
                            new FitnessSummary.SampleSize(4, 4)),
                    new FitnessSummary.PrActivity(3, 3, "active"),
                    Map.of("Back", 1));

            fitnessSummaryService.stub(userId, summary);

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Back", "Rear Delts", "Biceps"));

            // Signals the AI prompt rules act on:
            // "If rpeTrend.trendLabel is 'climbing': reduce exercises by 1"
            assertTrue(block.contains("climbing"),   "RPE trend must be 'climbing' in block");
            // "If a muscle shows volume 'high': bias toward LOWER end"
            assertTrue(block.contains("Back=high"),  "Back volume must be 'high'");
            // Verify strength reference for Back is present
            assertTrue(block.contains("barbell-row"), "barbell-row should appear for Back day");
        }

        /**
         * Persona 5 — Suresh: back pain context, low volume summary.
         * Health conditions are a user profile concern (not in the fitness summary),
         * but the low volume summary should bias toward fewer/lighter exercises.
         */
        @Test
        void persona_suresh_low_volume_summary_shows_low_labels() {
            UUID userId = UUID.randomUUID();
            FitnessSummary summary = new FitnessSummary(
                    1,
                    List.of(new FitnessSummary.MainLiftEntry(
                            "lat-pulldown", 40.0, 10, "2026-04-18", "Back")),
                    Map.of("Back",  FitnessSummary.MuscleVolume.of(7),
                           "Chest", FitnessSummary.MuscleVolume.of(4)),
                    new FitnessSummary.WeeklyConsistency(1.5, 1, 3, "low"),
                    new FitnessSummary.RpeTrend(null, null, "unknown",
                            new FitnessSummary.SampleSize(1, 0)),
                    new FitnessSummary.PrActivity(0, null, "stalled"),
                    Map.of("Back", 4, "Chest", 7));

            fitnessSummaryService.stub(userId, summary);

            String block = planService.buildFitnessSummaryBlock(
                    userId, List.of("Back", "Biceps"));

            assertTrue(block.contains("Back=low"),    "Back volume should be 'low'");
            assertTrue(block.contains("stalled"),     "Progression should be 'stalled'");
            assertTrue(block.contains("low"),         "Consistency should be 'low'");
            assertTrue(block.contains("lat-pulldown"),"lat-pulldown should appear for Back day");
            // Chest is not in target muscles — its volume must not appear
            assertFalse(block.contains("Chest=low"),
                    "Chest volume must not appear when Chest is not in target muscles");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Fixture builders
    // ═══════════════════════════════════════════════════════════════════

    /** Chest + Back summary used by most single-group tests. */
    private static FitnessSummary chestBackSummary() {
        return new FitnessSummary(
                1,
                List.of(
                        new FitnessSummary.MainLiftEntry(
                                "bench-press", 60.0, 8, "2026-04-20", "Chest"),
                        new FitnessSummary.MainLiftEntry(
                                "barbell-row", 55.0, 6, "2026-04-18", "Back")
                ),
                Map.of("Chest", FitnessSummary.MuscleVolume.of(15),
                       "Back",  FitnessSummary.MuscleVolume.of(12)),
                new FitnessSummary.WeeklyConsistency(3.5, 3, 4, "good"),
                new FitnessSummary.RpeTrend("hard", "good", "climbing",
                        new FitnessSummary.SampleSize(4, 4)),
                new FitnessSummary.PrActivity(3, 5, "active"),
                Map.of("Chest", 2, "Back", 4));
    }

    /** Summary with exercises for 5 different muscle groups — for filter tests. */
    private static FitnessSummary fiveMusclesSummary() {
        return new FitnessSummary(
                1,
                List.of(
                        new FitnessSummary.MainLiftEntry(
                                "bench-press",   60.0, 8,  "2026-04-20", "Chest"),
                        new FitnessSummary.MainLiftEntry(
                                "barbell-row",   55.0, 6,  "2026-04-18", "Back"),
                        new FitnessSummary.MainLiftEntry(
                                "squat",         80.0, 5,  "2026-04-17", "Legs"),
                        new FitnessSummary.MainLiftEntry(
                                "lateral-raises", 10.0, 15, "2026-04-16", "Shoulders"),
                        new FitnessSummary.MainLiftEntry(
                                "bicep-curl",    15.0, 12, "2026-04-15", "Arms")
                ),
                Map.of("Chest",     FitnessSummary.MuscleVolume.of(15),
                       "Back",      FitnessSummary.MuscleVolume.of(12),
                       "Legs",      FitnessSummary.MuscleVolume.of(10),
                       "Shoulders", FitnessSummary.MuscleVolume.of(8),
                       "Arms",      FitnessSummary.MuscleVolume.of(6)),
                new FitnessSummary.WeeklyConsistency(4.0, 4, 4, "good"),
                new FitnessSummary.RpeTrend("good", "good", "flat",
                        new FitnessSummary.SampleSize(4, 4)),
                new FitnessSummary.PrActivity(2, 7, "active"),
                Map.of("Chest",     2,
                       "Back",      7,
                       "Legs",      4,
                       "Shoulders", 5,
                       "Arms",      3));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Hand-rolled fake (FitnessSummaryService is concrete — not mockable on JDK 25)
    // ═══════════════════════════════════════════════════════════════════

    private static class FakeFitnessSummaryService extends FitnessSummaryService {

        private final Map<UUID, FitnessSummary> answers = new HashMap<>();

        FakeFitnessSummaryService() {
            super(null, null, null, null, new ObjectMapper());
        }

        /** Pre-load a summary for a given userId. */
        void stub(UUID userId, FitnessSummary summary) {
            answers.put(userId, summary);
        }

        @Override
        public Optional<FitnessSummary> getSummary(UUID userId) {
            return Optional.ofNullable(answers.get(userId));
        }
    }
}
