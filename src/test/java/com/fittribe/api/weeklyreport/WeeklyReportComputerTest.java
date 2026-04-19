package com.fittribe.api.weeklyreport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.PendingRecalibration;
import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.FindingsGenerator;
import com.fittribe.api.findings.WeekData;
import com.fittribe.api.findings.WeekDataBuilder;
import com.fittribe.api.findings.WeeklyReportMuscle;
import com.fittribe.api.repository.PendingRecalibrationRepository;
import com.fittribe.api.repository.WeeklyReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WeeklyReportComputer}. The collaborators that
 * carry business logic ({@link WeekDataBuilder},
 * {@link FindingsGenerator}, {@link RecalibrationDetector},
 * {@link VerdictGenerator}) are replaced by hand-rolled concrete stubs
 * — Mockito's inline mock maker on JDK 25 can't instrument these
 * concrete classes (same limitation that bit {@code JobWorkerTest}).
 * The JPA repositories are interfaces and so can be mocked normally.
 *
 * <p>The tests focus on the computer's orchestration contract:
 * <ul>
 *   <li>Pipeline is called in the right order.</li>
 *   <li>The five JSONB payloads upserted into {@code weekly_reports}
 *       have the shape the frontend expects.</li>
 *   <li>Week-one baselines are populated only when {@code isWeekOne},
 *       and otherwise an empty array is written.</li>
 *   <li>Muscle coverage is emitted for all 8 tiles with the correct
 *       RED/AMBER/GREEN mapping.</li>
 *   <li>Recalibrations are fanned out to
 *       {@code pending_recalibrations} — one row per
 *       {@link Recalibration}, or zero inserts when the list is
 *       empty.</li>
 *   <li>A null verdict from {@link VerdictGenerator} is persisted as
 *       SQL NULL rather than bubbling as an error.</li>
 * </ul>
 */
class WeeklyReportComputerTest {

    private FakeWeekDataBuilder weekDataBuilder;
    private FakeFindingsGenerator findingsGenerator;
    private FakeRecalibrationDetector recalibrationDetector;
    private FakeVerdictGenerator verdictGenerator;
    private WeeklyReportRepository weeklyReportRepo;
    private PendingRecalibrationRepository pendingRecalibrationRepo;
    private PlatformTransactionManager txManager;
    private ObjectMapper objectMapper;
    private WeeklyReportComputer computer;

    private static final UUID USER = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000");
    private static final LocalDate WEEK_START = LocalDate.of(2026, 4, 6);
    private static final LocalDate WEEK_END   = LocalDate.of(2026, 4, 13);

    @BeforeEach
    void setUp() {
        weekDataBuilder      = new FakeWeekDataBuilder();
        findingsGenerator    = new FakeFindingsGenerator();
        recalibrationDetector = new FakeRecalibrationDetector();
        verdictGenerator     = new FakeVerdictGenerator();
        weeklyReportRepo     = mock(WeeklyReportRepository.class);
        pendingRecalibrationRepo = mock(PendingRecalibrationRepository.class);
        txManager            = mock(PlatformTransactionManager.class);
        objectMapper         = new ObjectMapper();

        // TransactionTemplate needs a non-null status from getTransaction().
        // SimpleTransactionStatus is a concrete no-op — Mockito on JDK 25
        // can't proxy the TransactionStatus interface chain.
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        computer = new WeeklyReportComputer(
                weekDataBuilder,
                findingsGenerator,
                recalibrationDetector,
                verdictGenerator,
                weeklyReportRepo,
                pendingRecalibrationRepo,
                objectMapper,
                txManager);
    }

    // ── Happy path, week one ────────────────────────────────────────────

    @Test
    void computesWeekOneAndWritesAllJsonbPayloads() throws Exception {
        // Arrange: week-one snapshot with one PR, one top-set, one
        // recalibration, one finding, and a verdict string.
        WeekData week = fakeWeek(true, "Harsh", 4, 4, new BigDecimal("2840.00"), 1,
                List.of(new WeekData.PrEntry("ex-bench", new BigDecimal("100.00"), new BigDecimal("95.00"), 5)),
                Map.of("ex-bench", new WeekData.TopSet(new BigDecimal("100.00"), 5)),
                Map.of(
                        WeeklyReportMuscle.CHEST,      2, // GREEN
                        WeeklyReportMuscle.BACK_LATS,  1, // AMBER
                        WeeklyReportMuscle.SHOULDERS,  0  // RED (implicit)
                ));
        weekDataBuilder.result = week;
        findingsGenerator.result = List.of(
                new Finding("low_adherence", "RED", 100, "Only 3 sessions",
                        "You missed one. Let's nail it next week."));
        recalibrationDetector.result = List.of(
                new Recalibration("ex-bench",
                        new BigDecimal("95.00"),
                        new BigDecimal("100.0"),
                        "Logged max exceeded target"));
        verdictGenerator.result = "Great consistency this week.";

        // Act
        computer.compute(USER, WEEK_START);

        // Assert pipeline order
        assertEquals(1, weekDataBuilder.calls.size());
        assertEquals(USER, weekDataBuilder.calls.get(0).userId);
        assertEquals(WEEK_START, weekDataBuilder.calls.get(0).weekStart);
        assertEquals(1, findingsGenerator.calls.size());
        assertEquals(1, recalibrationDetector.calls.size());
        assertEquals(1, verdictGenerator.calls.size());

        // Capture the upsert arguments
        ArgumentCaptor<String> personalRecordsJson = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> baselinesJson       = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> muscleCoverageJson  = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> findingsJson        = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> recalibrationsJson  = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> verdictArg          = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> isWeekOneArg       = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<BigDecimal> totalVolumeArg  = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<Integer> weekNumberArg      = ArgumentCaptor.forClass(Integer.class);

        verify(weeklyReportRepo).upsert(
                eq(USER),
                eq(WEEK_START),
                eq(WEEK_END),
                weekNumberArg.capture(),
                eq("Harsh"),
                eq(4),
                eq(4),
                totalVolumeArg.capture(),
                eq(1),
                verdictArg.capture(),
                personalRecordsJson.capture(),
                baselinesJson.capture(),
                muscleCoverageJson.capture(),
                findingsJson.capture(),
                recalibrationsJson.capture(),
                isWeekOneArg.capture(),
                eq(WeeklyReportComputer.SCHEMA_VERSION));

        assertEquals(1, weekNumberArg.getValue().intValue());
        assertEquals(0, totalVolumeArg.getValue().compareTo(new BigDecimal("2840")));
        assertEquals("Great consistency this week.", verdictArg.getValue());
        assertEquals(Boolean.TRUE, isWeekOneArg.getValue());

        // Personal records payload
        List<Map<String, Object>> prs = parseJsonArray(personalRecordsJson.getValue());
        assertEquals(1, prs.size());
        assertEquals("ex-bench", prs.get(0).get("exerciseId"));
        // 100.00 normalized → 100 (scale 0, no trailing zeros) → Jackson
        // emits as integer literal, not "1E+2".
        assertEquals(100, ((Number) prs.get(0).get("newMaxKg")).intValue());
        assertEquals(95,  ((Number) prs.get(0).get("previousMaxKg")).intValue());
        assertEquals(5,   ((Number) prs.get(0).get("reps")).intValue());

        // Baselines payload — week one, so one entry
        List<Map<String, Object>> baselines = parseJsonArray(baselinesJson.getValue());
        assertEquals(1, baselines.size());
        assertEquals("ex-bench", baselines.get(0).get("exerciseId"));
        assertEquals(100, ((Number) baselines.get(0).get("weightKg")).intValue());
        assertEquals(5,   ((Number) baselines.get(0).get("reps")).intValue());

        // Muscle coverage — all 8 tiles, in enum order, correct status
        List<Map<String, Object>> coverage = parseJsonArray(muscleCoverageJson.getValue());
        assertEquals(8, coverage.size(), "must emit all 8 tiles regardless of training");
        assertEquals("CHEST",     coverage.get(0).get("muscle"));
        assertEquals(2,           ((Number) coverage.get(0).get("sessions")).intValue());
        assertEquals("GREEN",     coverage.get(0).get("status"));
        assertEquals("BACK_LATS", coverage.get(1).get("muscle"));
        assertEquals(1,           ((Number) coverage.get(1).get("sessions")).intValue());
        assertEquals("AMBER",     coverage.get(1).get("status"));
        assertEquals("SHOULDERS", coverage.get(2).get("muscle"));
        assertEquals(0,           ((Number) coverage.get(2).get("sessions")).intValue());
        assertEquals("RED",       coverage.get(2).get("status"));
        // Unvisited tiles default to 0 sessions → RED
        for (int i = 3; i < 8; i++) {
            assertEquals(0, ((Number) coverage.get(i).get("sessions")).intValue());
            assertEquals("RED", coverage.get(i).get("status"));
        }

        // Findings payload mirrors the Finding record
        List<Map<String, Object>> findingsList = parseJsonArray(findingsJson.getValue());
        assertEquals(1, findingsList.size());
        assertEquals("low_adherence", findingsList.get(0).get("ruleId"));
        assertEquals("RED",           findingsList.get(0).get("severity"));
        assertEquals(100,             ((Number) findingsList.get(0).get("weight")).intValue());
        assertEquals("Only 3 sessions", findingsList.get(0).get("title"));

        // Recalibrations payload — mirrors Recalibration record
        List<Map<String, Object>> recals = parseJsonArray(recalibrationsJson.getValue());
        assertEquals(1, recals.size());
        assertEquals("ex-bench", recals.get(0).get("exerciseId"));
        assertEquals(95,  ((Number) recals.get(0).get("oldTargetKg")).intValue());
        assertEquals(100, ((Number) recals.get(0).get("newTargetKg")).intValue());

        // One pending_recalibrations row saved
        ArgumentCaptor<PendingRecalibration> pendingCap = ArgumentCaptor.forClass(PendingRecalibration.class);
        verify(pendingRecalibrationRepo).save(pendingCap.capture());
        PendingRecalibration saved = pendingCap.getValue();
        assertEquals(USER, saved.getUserId());
        assertEquals("ex-bench", saved.getExerciseId());
        assertEquals(0, saved.getOldTargetKg().compareTo(new BigDecimal("95")));
        assertEquals(0, saved.getNewTargetKg().compareTo(new BigDecimal("100")));
        assertEquals("Logged max exceeded target", saved.getReason());
    }

    // ── Later weeks omit baselines ──────────────────────────────────────

    @Test
    void laterWeekWritesEmptyBaselines() throws Exception {
        WeekData week = fakeWeek(false, "Harsh", 4, 4, new BigDecimal("3100"), 0,
                List.of(),
                Map.of("ex-bench", new WeekData.TopSet(new BigDecimal("100"), 5)),
                Map.of());
        weekDataBuilder.result = week;
        findingsGenerator.result = List.of();
        recalibrationDetector.result = List.of();
        verdictGenerator.result = "Keep going.";

        computer.compute(USER, WEEK_START);

        ArgumentCaptor<String> baselines = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> isWeekOne = ArgumentCaptor.forClass(Boolean.class);
        verify(weeklyReportRepo).upsert(
                eq(USER), any(), any(), anyInt(), anyString(),
                anyInt(), anyInt(), any(), anyInt(),
                anyString(),
                anyString(),            // personalRecords
                baselines.capture(),
                anyString(),            // muscleCoverage
                anyString(),            // findings
                anyString(),            // recalibrations
                isWeekOne.capture(),
                eq(WeeklyReportComputer.SCHEMA_VERSION));

        assertEquals(Boolean.FALSE, isWeekOne.getValue());
        assertEquals("[]", baselines.getValue(),
                "baselines must be empty array outside week one");
    }

    // ── Top-2 highlights capping ────────────────────────────────────────

    @Test
    void personalRecordsCapToTop2Entries() throws Exception {
        // Multiple PRs + baselines; only top 2 should be retained
        WeekData week = fakeWeek(false, "Harsh", 5, 4, new BigDecimal("5000"), 5,
                List.of(
                        // PRs (previousMaxKg != null)
                        new WeekData.PrEntry("bench-press", new BigDecimal("110"), new BigDecimal("100"), 5),    // delta 10
                        new WeekData.PrEntry("squat", new BigDecimal("160"), new BigDecimal("150"), 6),           // delta 10
                        new WeekData.PrEntry("deadlift", new BigDecimal("190"), new BigDecimal("180"), 3),        // delta 10
                        new WeekData.PrEntry("row", new BigDecimal("120"), new BigDecimal("110"), 4),             // delta 10
                        new WeekData.PrEntry("tricep-pushdowns", new BigDecimal("60"), new BigDecimal("55"), 8)   // delta 5
                ),
                Map.of(),
                Map.of());
        weekDataBuilder.result = week;
        findingsGenerator.result = List.of();
        recalibrationDetector.result = List.of();
        verdictGenerator.result = "Excellent week.";

        computer.compute(USER, WEEK_START);

        ArgumentCaptor<String> personalRecordsJson = ArgumentCaptor.forClass(String.class);
        verify(weeklyReportRepo).upsert(
                eq(USER), any(), any(), anyInt(), anyString(),
                anyInt(), anyInt(), any(), anyInt(),
                anyString(),
                personalRecordsJson.capture(),
                anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), anyInt());

        List<Map<String, Object>> prs = parseJsonArray(personalRecordsJson.getValue());
        assertEquals(2, prs.size(), "personal records must be capped to 2 entries");
        assertTrue(prs.stream().allMatch(p -> p.containsKey("type")), "all entries must have type field");
    }

    @Test
    void prTypeAssignedCorrectly() throws Exception {
        // Mix of PRs and BASELINEs
        WeekData week = fakeWeek(true, "Harsh", 3, 4, new BigDecimal("1500"), 2,
                List.of(
                        new WeekData.PrEntry("bench-press", new BigDecimal("100"), new BigDecimal("95"), 5),  // PR
                        new WeekData.PrEntry("lat-pulldown", new BigDecimal("80"), null, 8)                   // BASELINE
                ),
                Map.of(),
                Map.of());
        weekDataBuilder.result = week;
        findingsGenerator.result = List.of();
        recalibrationDetector.result = List.of();
        verdictGenerator.result = null;

        computer.compute(USER, WEEK_START);

        ArgumentCaptor<String> personalRecordsJson = ArgumentCaptor.forClass(String.class);
        verify(weeklyReportRepo).upsert(
                eq(USER), any(), any(), anyInt(), anyString(),
                anyInt(), anyInt(), any(), anyInt(),
                any(),  // verdict can be null
                personalRecordsJson.capture(),
                anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), anyInt());

        List<Map<String, Object>> prs = parseJsonArray(personalRecordsJson.getValue());
        assertEquals(2, prs.size());
        Map<String, Object> benchEntry = prs.stream()
                .filter(p -> "bench-press".equals(p.get("exerciseId")))
                .findFirst().orElse(null);
        assertNotNull(benchEntry);
        assertEquals("PR", benchEntry.get("type"));
        Map<String, Object> pullEntry = prs.stream()
                .filter(p -> "lat-pulldown".equals(p.get("exerciseId")))
                .findFirst().orElse(null);
        assertNotNull(pullEntry);
        assertEquals("BASELINE", pullEntry.get("type"));
    }

    @Test
    void prRankingByWeightDelta() throws Exception {
        // PRs ranked by weight delta DESC
        WeekData week = fakeWeek(false, "Harsh", 4, 4, new BigDecimal("3000"), 3,
                List.of(
                        new WeekData.PrEntry("bench-press", new BigDecimal("110"), new BigDecimal("100"), 5),      // delta 10
                        new WeekData.PrEntry("squat", new BigDecimal("170"), new BigDecimal("150"), 4),             // delta 20
                        new WeekData.PrEntry("tricep-pushdowns", new BigDecimal("60"), new BigDecimal("55"), 10)    // delta 5
                ),
                Map.of(),
                Map.of());
        weekDataBuilder.result = week;
        findingsGenerator.result = List.of();
        recalibrationDetector.result = List.of();
        verdictGenerator.result = "Strong week.";

        computer.compute(USER, WEEK_START);

        ArgumentCaptor<String> personalRecordsJson = ArgumentCaptor.forClass(String.class);
        verify(weeklyReportRepo).upsert(
                eq(USER), any(), any(), anyInt(), anyString(),
                anyInt(), anyInt(), any(), anyInt(),
                anyString(),
                personalRecordsJson.capture(),
                anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), anyInt());

        List<Map<String, Object>> prs = parseJsonArray(personalRecordsJson.getValue());
        assertEquals(2, prs.size());
        // Top 2: squat (delta 20) and bench (delta 10)
        assertEquals("squat", prs.get(0).get("exerciseId"), "first should be squat (highest delta)");
        assertEquals("bench-press", prs.get(1).get("exerciseId"), "second should be bench (second highest delta)");
    }

    @Test
    void baselineRankingByWeight() throws Exception {
        // BASELINEs ranked by newMaxKg DESC
        WeekData week = fakeWeek(true, "Harsh", 4, 4, new BigDecimal("2500"), 0,
                List.of(
                        new WeekData.PrEntry("bench-press", new BigDecimal("80"), null, 5),     // BASELINE
                        new WeekData.PrEntry("squat", new BigDecimal("120"), null, 4),          // BASELINE
                        new WeekData.PrEntry("lat-pulldown", new BigDecimal("100"), null, 8)    // BASELINE
                ),
                Map.of(),
                Map.of());
        weekDataBuilder.result = week;
        findingsGenerator.result = List.of();
        recalibrationDetector.result = List.of();
        verdictGenerator.result = null;

        computer.compute(USER, WEEK_START);

        ArgumentCaptor<String> personalRecordsJson = ArgumentCaptor.forClass(String.class);
        verify(weeklyReportRepo).upsert(
                eq(USER), any(), any(), anyInt(), anyString(),
                anyInt(), anyInt(), any(), anyInt(),
                any(),  // verdict can be null
                personalRecordsJson.capture(),
                anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), anyInt());

        List<Map<String, Object>> prs = parseJsonArray(personalRecordsJson.getValue());
        assertEquals(2, prs.size());
        // Top 2 baselines: squat (120) and lat-pulldown (100)
        assertEquals("squat", prs.get(0).get("exerciseId"), "first baseline should be squat (120 kg)");
        assertEquals("lat-pulldown", prs.get(1).get("exerciseId"), "second should be lat-pulldown (100 kg)");
    }

    @Test
    void compoundTiebreakerInRanking() throws Exception {
        // When scores are equal, compound lifts rank before isolation
        WeekData week = fakeWeek(false, "Harsh", 4, 4, new BigDecimal("3000"), 4,
                List.of(
                        new WeekData.PrEntry("bench-press", new BigDecimal("110"), new BigDecimal("100"), 5),      // compound, delta 10
                        new WeekData.PrEntry("bicep-curl", new BigDecimal("40"), new BigDecimal("30"), 10),         // isolation, delta 10
                        new WeekData.PrEntry("squat", new BigDecimal("160"), new BigDecimal("150"), 4),             // compound, delta 10
                        new WeekData.PrEntry("lateral-raises", new BigDecimal("25"), new BigDecimal("15"), 12)      // isolation, delta 10
                ),
                Map.of(),
                Map.of());
        weekDataBuilder.result = week;
        findingsGenerator.result = List.of();
        recalibrationDetector.result = List.of();
        verdictGenerator.result = "Balanced week.";

        computer.compute(USER, WEEK_START);

        ArgumentCaptor<String> personalRecordsJson = ArgumentCaptor.forClass(String.class);
        verify(weeklyReportRepo).upsert(
                eq(USER), any(), any(), anyInt(), anyString(),
                anyInt(), anyInt(), any(), anyInt(),
                anyString(),
                personalRecordsJson.capture(),
                anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), anyInt());

        List<Map<String, Object>> prs = parseJsonArray(personalRecordsJson.getValue());
        assertEquals(2, prs.size());
        // All have delta 10, so tiebreaker applies: compounds rank first
        String first = (String) prs.get(0).get("exerciseId");
        String second = (String) prs.get(1).get("exerciseId");
        assertTrue(
                (first.equals("bench-press") || first.equals("squat")) &&
                        (second.equals("bench-press") || second.equals("squat")),
                "both top 2 should be compounds (bench and squat) due to tiebreaker"
        );
    }

    // ── Null verdict is persisted as SQL NULL ──────────────────────────

    @Test
    void nullVerdictIsPersisted() {
        WeekData week = fakeWeek(true, "Harsh", 4, 4, new BigDecimal("1000"), 0,
                List.of(), Map.of(), Map.of());
        weekDataBuilder.result = week;
        findingsGenerator.result = List.of();
        recalibrationDetector.result = List.of();
        verdictGenerator.result = null; // VerdictGenerator may return null on failure

        computer.compute(USER, WEEK_START);

        ArgumentCaptor<String> verdict = ArgumentCaptor.forClass(String.class);
        verify(weeklyReportRepo).upsert(
                eq(USER), any(), any(), anyInt(), anyString(),
                anyInt(), anyInt(), any(), anyInt(),
                verdict.capture(),
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyBoolean(),
                anyInt());

        assertNull(verdict.getValue(), "null verdict must be passed through as null");
    }

    // ── No recalibrations → no pending_recalibrations inserts ──────────

    @Test
    void noRecalibrationsMeansNoPendingInserts() {
        WeekData week = fakeWeek(false, "Harsh", 3, 4, new BigDecimal("800"), 0,
                List.of(), Map.of(), Map.of());
        weekDataBuilder.result = week;
        findingsGenerator.result = List.of();
        recalibrationDetector.result = List.of();
        verdictGenerator.result = "Almost there.";

        computer.compute(USER, WEEK_START);

        verify(weeklyReportRepo).upsert(
                eq(USER), any(), any(), anyInt(), anyString(),
                anyInt(), anyInt(), any(), anyInt(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyBoolean(),
                anyInt());
        verify(pendingRecalibrationRepo, never()).save(any());
    }

    // ── Multiple recalibrations → fan out to N inserts ─────────────────

    @Test
    void multipleRecalibrationsFanOutToMultipleInserts() {
        WeekData week = fakeWeek(true, "Harsh", 4, 4, new BigDecimal("2500"), 0,
                List.of(), Map.of(), Map.of());
        weekDataBuilder.result = week;
        findingsGenerator.result = List.of();
        recalibrationDetector.result = List.of(
                new Recalibration("ex-bench", new BigDecimal("80"), new BigDecimal("85"), "r1"),
                new Recalibration("ex-squat", new BigDecimal("100"), new BigDecimal("105"), "r2"),
                new Recalibration("ex-row",   new BigDecimal("60"),  new BigDecimal("65"),  "r3"));
        verdictGenerator.result = "Solid week.";

        computer.compute(USER, WEEK_START);

        ArgumentCaptor<PendingRecalibration> cap = ArgumentCaptor.forClass(PendingRecalibration.class);
        verify(pendingRecalibrationRepo, org.mockito.Mockito.times(3)).save(cap.capture());
        List<PendingRecalibration> saved = cap.getAllValues();
        assertEquals("ex-bench", saved.get(0).getExerciseId());
        assertEquals("ex-squat", saved.get(1).getExerciseId());
        assertEquals("ex-row",   saved.get(2).getExerciseId());
    }

    // ── Muscle coverage thresholds ──────────────────────────────────────

    @Test
    void coverageStatusMapping() {
        assertEquals("GREEN", WeeklyReportComputer.statusForCoverage(5));
        assertEquals("GREEN", WeeklyReportComputer.statusForCoverage(2));
        assertEquals("AMBER", WeeklyReportComputer.statusForCoverage(1));
        assertEquals("RED",   WeeklyReportComputer.statusForCoverage(0));
    }

    // ── BigDecimal normalization ────────────────────────────────────────

    @Test
    void normalizeKgStripsTrailingZerosAndAvoidsScientific() {
        // 100.00 → 100 (scale 0, strip trailing zeros)
        assertEquals(0, WeeklyReportComputer.normalizeKg(new BigDecimal("100.00"))
                .compareTo(new BigDecimal("100")));
        // 87.5 stays 87.5 (no trailing zero to strip)
        assertEquals(0, WeeklyReportComputer.normalizeKg(new BigDecimal("87.5"))
                .compareTo(new BigDecimal("87.5")));
        // 100 stays 100 (already integer)
        assertEquals(0, WeeklyReportComputer.normalizeKg(new BigDecimal("100"))
                .compareTo(new BigDecimal("100")));
        // null passes through
        assertNull(WeeklyReportComputer.normalizeKg(null));
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private WeekData fakeWeek(
            boolean isWeekOne,
            String firstName,
            int sessionsLogged,
            int sessionsGoal,
            BigDecimal totalVolume,
            int prCount,
            List<WeekData.PrEntry> personalRecords,
            Map<String, WeekData.TopSet> thisWeekTopSets,
            Map<WeeklyReportMuscle, Integer> sessionsByMuscle) {

        Map<WeeklyReportMuscle, Integer> coverage = new EnumMap<>(WeeklyReportMuscle.class);
        coverage.putAll(sessionsByMuscle);

        return new WeekData(
                USER,
                WEEK_START,
                WEEK_END,
                isWeekOne ? 1 : 2,
                isWeekOne,
                firstName,
                sessionsLogged,
                sessionsGoal,
                sessionsLogged >= sessionsGoal,
                totalVolume,
                prCount,
                List.of(),                                                  // sessions
                Collections.unmodifiableMap(new HashMap<>()),               // setsByExercise
                Collections.unmodifiableMap(coverage),                      // sessionsByMuscle
                0, 0, 0,                                                    // push/pull/legs counts
                Collections.unmodifiableMap(new LinkedHashMap<>(thisWeekTopSets)),
                Collections.unmodifiableMap(new HashMap<>()),               // previousWeekTopSets
                List.copyOf(personalRecords),                               // personalRecords
                Collections.unmodifiableMap(new HashMap<>()),               // targetExercises
                Collections.unmodifiableMap(new HashMap<>()));              // exerciseCatalog
    }

    private List<Map<String, Object>> parseJsonArray(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    }

    // ── Hand-rolled fakes for unmockable concrete collaborators ────────
    //
    // Mockito's inline mock maker on JDK 25 can't instrument classes
    // with certain internal structures (ObjectMapper, TransactionStatus,
    // and the weeklyreport/findings classes have all tripped this in
    // earlier stages). Rather than fight the mock maker, each
    // collaborator has a tiny concrete subclass here that records
    // invocations and returns a pre-set canned result.

    static final class FakeWeekDataBuilder extends WeekDataBuilder {
        WeekData result;
        final List<Call> calls = new ArrayList<>();
        FakeWeekDataBuilder() { super(null, null, null, null, null); }
        @Override
        public WeekData build(UUID userId, LocalDate weekStart) {
            calls.add(new Call(userId, weekStart));
            return result;
        }
        static final class Call {
            final UUID userId;
            final LocalDate weekStart;
            Call(UUID userId, LocalDate weekStart) {
                this.userId = userId;
                this.weekStart = weekStart;
            }
        }
    }

    static final class FakeFindingsGenerator extends FindingsGenerator {
        List<Finding> result = List.of();
        final List<WeekData> calls = new ArrayList<>();
        FakeFindingsGenerator() { super(List.of(), null); }
        @Override
        public List<Finding> generate(WeekData week) {
            calls.add(week);
            return result;
        }
    }

    static final class FakeRecalibrationDetector extends RecalibrationDetector {
        List<Recalibration> result = List.of();
        final List<WeekData> calls = new ArrayList<>();
        @Override
        public List<Recalibration> detect(List<Finding> findings, WeekData week) {
            calls.add(week);
            return result;
        }
    }

    static final class FakeVerdictGenerator extends VerdictGenerator {
        String result;
        final List<WeekData> calls = new ArrayList<>();
        FakeVerdictGenerator() { super("", null); }
        @Override
        public String generate(WeekData week, List<Finding> findings) {
            calls.add(week);
            return result;
        }
    }
}
