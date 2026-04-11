package com.fittribe.api.weeklyreport;

import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import com.fittribe.api.findings.WeeklyReportMuscle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RecalibrationDetector}. Pure-function tests —
 * no Spring, no database. Hand-rolled WeekData fixtures because
 * {@code WeekDataFixture} is package-private in
 * {@code com.fittribe.api.findings.rules}.
 *
 * <p>The detector re-scans {@link WeekData} to identify the affected
 * exercises whenever the corresponding rule id appears in the findings
 * list — see {@link RecalibrationDetector} class javadoc. The tests
 * below exercise each rule path, the dedupe / most-conservative-wins
 * behaviour, and the skip conditions.
 */
class RecalibrationDetectorTest {

    private final RecalibrationDetector detector = new RecalibrationDetector();

    // ── Empty input ──────────────────────────────────────────────────────

    @Test
    void emptyFindingsReturnsEmptyList() {
        WeekData week = weekWith(targets(tvl("bench", 100, 85.0)));
        assertTrue(detector.detect(List.of(), week).isEmpty());
    }

    @Test
    void nullFindingsReturnsEmptyList() {
        WeekData week = weekWith(targets(tvl("bench", 100, 85.0)));
        assertTrue(detector.detect(null, week).isEmpty());
    }

    @Test
    void findingsWithoutRecalibrationRulesReturnsEmptyList() {
        // full_consistency, multi_pr_week are not recalibration triggers
        WeekData week = weekWith(targets(tvl("bench", 100, 85.0)));
        List<Finding> findings = List.of(
                finding("full_consistency"),
                finding("multi_pr_week"));
        assertTrue(detector.detect(findings, week).isEmpty());
    }

    // ── single_too_light ─────────────────────────────────────────────────

    @Test
    void singleTooLightFlagsWorstInBandAndRoundsTo25Kg() {
        // Two exercises in the 85-90% band — rule picks the worst ratio.
        //  squat:  87 / 100  = 0.87
        //  bench:  89 / 100  = 0.89
        // Expected: squat wins; new_target = round(87, 2.5) = 87.5
        WeekData week = weekWith(targets(
                tvl("bench", 100, 89.0),
                tvl("squat", 100, 87.0)));

        List<Recalibration> out = detector.detect(
                List.of(finding("single_too_light")), week);

        assertEquals(1, out.size());
        Recalibration r = out.get(0);
        assertEquals("squat", r.exerciseId());
        // Scale comes from BigDecimal.valueOf(double) in the fixture — "100.0" scale 1
        assertEquals(new BigDecimal("100.0"), r.oldTargetKg());
        assertEquals(new BigDecimal("87.5"), r.newTargetKg());
        assertNotNull(r.reason());
        assertTrue(r.reason().contains("recalibrating down"),
                "reason should explain the adjustment: " + r.reason());
    }

    @Test
    void singleTooLightIgnoresExercisesOutsideBand() {
        // deadlift < 85%  (40 / 100 = 0.40)  — below band, ignored
        // row > 90%       (95 / 100 = 0.95)  — above band, ignored
        // Nothing falls inside [0.85, 0.90) → no recalibration.
        WeekData week = weekWith(targets(
                tvl("deadlift", 100, 40.0),
                tvl("row",      100, 95.0)));

        List<Recalibration> out = detector.detect(
                List.of(finding("single_too_light")), week);
        assertTrue(out.isEmpty());
    }

    @Test
    void singleTooLightSkipsExercisesWithNullTarget() {
        // One exercise has no plan target — detector must not NPE and
        // must ignore it. The other is in-band and gets picked.
        WeekData week = weekWith(targets(
                tvlNullTarget("custom-lift", 80.0),
                tvl("bench", 100, 88.0)));

        List<Recalibration> out = detector.detect(
                List.of(finding("single_too_light")), week);

        assertEquals(1, out.size());
        assertEquals("bench", out.get(0).exerciseId());
    }

    // ── multiple_too_light ───────────────────────────────────────────────

    @Test
    void multipleTooLightFlagsAllExercisesBelow85Percent() {
        // Three "well below" — ratio < 0.85 — and one that's fine.
        //   bench:    80 / 100  = 0.80  → in batch, new = 80.0
        //   squat:    82 / 100  = 0.82  → in batch, new = 82.5
        //   deadlift: 63 / 100  = 0.63  → in batch, new = 62.5
        //   row:      90 / 100  = 0.90  → NOT in batch
        WeekData week = weekWith(targets(
                tvl("bench",    100, 80.0),
                tvl("squat",    100, 82.0),
                tvl("deadlift", 100, 63.0),
                tvl("row",      100, 90.0)));

        List<Recalibration> out = detector.detect(
                List.of(finding("multiple_too_light")), week);

        assertEquals(3, out.size());
        Map<String, BigDecimal> byId = new LinkedHashMap<>();
        out.forEach(r -> byId.put(r.exerciseId(), r.newTargetKg()));
        assertEquals(new BigDecimal("80.0"), byId.get("bench"));
        assertEquals(new BigDecimal("82.5"), byId.get("squat"));
        assertEquals(new BigDecimal("62.5"), byId.get("deadlift"));
        assertTrue(!byId.containsKey("row"));
    }

    @Test
    void multipleTooLightSkipsNullTargetAndNullLogged() {
        WeekData week = weekWith(targets(
                tvlNullTarget("custom",   70.0),  // no target, skipped
                tvl("bench",    100, 70.0),       // 0.70, in batch
                tvl("deadlift", 100, null)));     // no logged max, skipped

        List<Recalibration> out = detector.detect(
                List.of(finding("multiple_too_light")), week);

        assertEquals(1, out.size());
        assertEquals("bench", out.get(0).exerciseId());
    }

    // ── pr_regression ────────────────────────────────────────────────────

    @Test
    void prRegressionFlagsAllRegressedExercisesWithPreviousWeight() {
        // bench: 80 vs 100 last week → 20% drop, pr_regression fires
        // squat: 85 vs 100 last week → 15% drop, pr_regression fires
        // row:   98 vs 100 last week → 2% drop, NOT a regression
        Map<String, WeekData.TargetVsLogged> targets = targets(
                tvl("bench", 100, 80.0),
                tvl("squat", 110, 85.0),
                tvl("row",   70,  98.0));
        Map<String, WeekData.TopSet> thisWeek = topSets(
                topSet("bench", 80.0,  5),
                topSet("squat", 85.0,  5),
                topSet("row",   98.0,  5));
        Map<String, WeekData.TopSet> prevWeek = topSets(
                topSet("bench", 100.0, 5),
                topSet("squat", 100.0, 5),
                topSet("row",   100.0, 5));
        WeekData week = weekWith(targets, thisWeek, prevWeek);

        List<Recalibration> out = detector.detect(
                List.of(finding("pr_regression")), week);

        assertEquals(2, out.size());
        Map<String, Recalibration> byId = new LinkedHashMap<>();
        out.forEach(r -> byId.put(r.exerciseId(), r));

        // New target = previous week weight (exact, unrounded).
        // Scales all come from BigDecimal.valueOf(double) → "N.0" scale 1.
        assertEquals(new BigDecimal("100.0"), byId.get("bench").newTargetKg());
        assertEquals(new BigDecimal("100.0"), byId.get("bench").oldTargetKg());
        assertEquals(new BigDecimal("100.0"), byId.get("squat").newTargetKg());
        assertEquals(new BigDecimal("110.0"), byId.get("squat").oldTargetKg());

        assertTrue(byId.get("bench").reason()
                        .contains("backing off to last confirmed working weight"),
                "reason should match spec: " + byId.get("bench").reason());
    }

    @Test
    void prRegressionSkipsExerciseWithNoPlanTarget() {
        // Regressed exercise with no target — nothing to recalibrate
        // from. Skipped, even though it's a clear regression.
        WeekData week = weekWith(
                targets(tvlNullTarget("freestyle", 80.0)),
                topSets(topSet("freestyle", 80.0, 5)),
                topSets(topSet("freestyle", 100.0, 5)));

        List<Recalibration> out = detector.detect(
                List.of(finding("pr_regression")), week);
        assertTrue(out.isEmpty());
    }

    @Test
    void prRegressionIgnoresExerciseWithNoPreviousWeek() {
        // First time doing the lift — no prev week to compare.
        WeekData week = weekWith(
                targets(tvl("bench", 100, 80.0)),
                topSets(topSet("bench", 80.0, 5)),
                topSets()); // no prev week

        List<Recalibration> out = detector.detect(
                List.of(finding("pr_regression")), week);
        assertTrue(out.isEmpty());
    }

    // ── Dedupe across finding types ──────────────────────────────────────

    @Test
    void dedupeAcrossFindingTypesKeepsLowestNewTarget() {
        // bench flagged by BOTH pr_regression and multiple_too_light:
        //   pr_regression says: back off to prev week  = 100 kg
        //   multiple_too_light says: round logged max  =  80 kg (82/2.5→33→82.5 wait recalc)
        // Using logged max = 75 so rounded target = 75.0 (75 / 2.5 = 30 → 75)
        // Lower wins → multiple_too_light candidate (75.0) should survive.
        WeekData week = weekWith(
                targets(tvl("bench", 110, 75.0)),
                topSets(topSet("bench", 75.0, 5)),
                topSets(topSet("bench", 100.0, 5)));

        List<Recalibration> out = detector.detect(
                List.of(finding("pr_regression"),
                        finding("multiple_too_light")),
                week);

        assertEquals(1, out.size());
        Recalibration r = out.get(0);
        assertEquals("bench", r.exerciseId());
        // Rounded logged max wins over prev-week backoff (75 < 100)
        assertEquals(new BigDecimal("75.0"), r.newTargetKg());
        assertEquals(new BigDecimal("110.0"),  r.oldTargetKg());
        assertTrue(r.reason().contains("well below target"),
                "reason should come from the surviving candidate: " + r.reason());
    }

    @Test
    void dedupeKeepsPrRegressionWhenItIsMoreConservative() {
        // Contrived: the too-light candidate would round UP to 87.5
        // (logged = 86), while the pr_regression candidate backs off
        // to a previous week of 80 kg — pr_regression is more
        // conservative.
        WeekData week = weekWith(
                targets(tvl("row", 100, 86.0)),
                topSets(topSet("row", 86.0, 5)),
                topSets(topSet("row", 80.0, 5)));
        // topSets: this week 86, prev week 80 — 86/80 = 1.075 → NOT a regression.
        // So pr_regression wouldn't fire for this case. Flip it:
        WeekData flipped = weekWith(
                targets(tvl("row", 100, 86.0)),
                topSets(topSet("row", 70.0, 5)),    // this week 70
                topSets(topSet("row", 80.0, 5)));   // prev week 80 → 70/80=0.875, >5% drop
        List<Recalibration> out = detector.detect(
                List.of(finding("single_too_light"),
                        finding("pr_regression")),
                flipped);

        // single_too_light evaluates ratio = 86/100 = 0.86 → in band → candidate newTarget = 87.5
        // pr_regression new target = 80.0 (prev week weight)
        // Lower wins → pr_regression should survive with newTarget=80.0
        assertEquals(1, out.size());
        assertEquals("row", out.get(0).exerciseId());
        assertEquals(new BigDecimal("80.0"), out.get(0).newTargetKg());
    }

    // ── Rounding edge cases ──────────────────────────────────────────────

    @Test
    void roundingSnapsToNearest2Point5Kg() {
        // 47.3 / 2.5 = 18.92 → HALF_UP → 19 → 47.5
        // 46.24 / 2.5 = 18.496 → HALF_UP → 18 → 45.0
        // 46.25 / 2.5 = 18.5 → HALF_UP → 19 → 47.5
        WeekData week = weekWith(targets(
                tvl("a", 55, 47.3),   // 0.86 — in band
                tvl("b", 55, 46.24),  // 0.84 — well-below batch
                tvl("c", 55, 46.25))); // 0.84 — well-below batch

        List<Recalibration> batch = detector.detect(
                List.of(finding("multiple_too_light")), week);
        // Only b and c are well-below (a is in-band, not in batch).
        assertEquals(2, batch.size());
        Map<String, BigDecimal> byId = new LinkedHashMap<>();
        batch.forEach(r -> byId.put(r.exerciseId(), r.newTargetKg()));
        assertEquals(new BigDecimal("45.0"), byId.get("b"));
        assertEquals(new BigDecimal("47.5"), byId.get("c"));

        // And a would be picked by single_too_light and rounded to 47.5
        List<Recalibration> single = detector.detect(
                List.of(finding("single_too_light")), week);
        // The worst ratio in [0.85,0.90) wins. 46.25/55 = 0.8409 → below band, excluded.
        // So only 47.3/55 = 0.86 remains.
        assertEquals(1, single.size());
        assertEquals("a", single.get(0).exerciseId());
        assertEquals(new BigDecimal("47.5"), single.get(0).newTargetKg());
    }

    // ── Fixture helpers ──────────────────────────────────────────────────

    private static Finding finding(String ruleId) {
        // Severity / weight / copy are irrelevant — detector only reads ruleId.
        return new Finding(ruleId, "AMBER", 60, "t", "d");
    }

    private static WeekData.TargetVsLogged tvl(String id, double target, Double logged) {
        return new WeekData.TargetVsLogged(
                id,
                BigDecimal.valueOf(target),
                logged == null ? null : BigDecimal.valueOf(logged));
    }

    private static WeekData.TargetVsLogged tvlNullTarget(String id, Double logged) {
        return new WeekData.TargetVsLogged(
                id,
                null,
                logged == null ? null : BigDecimal.valueOf(logged));
    }

    /** Build a LinkedHashMap of exerciseId → TargetVsLogged preserving insertion order. */
    private static Map<String, WeekData.TargetVsLogged> targets(WeekData.TargetVsLogged... entries) {
        Map<String, WeekData.TargetVsLogged> out = new LinkedHashMap<>();
        for (WeekData.TargetVsLogged t : entries) out.put(t.exerciseId(), t);
        return out;
    }

    /**
     * {@link WeekData.TopSet} doesn't carry an exerciseId, so we wrap
     * (id, TopSet) pairs and unpack them in {@link #topSets(TopSetEntry...)}.
     */
    private record TopSetEntry(String id, WeekData.TopSet set) {}

    /** Construct one {@link TopSetEntry}. Kept short — used as varargs. */
    private static TopSetEntry topSet(String id, double kg, int reps) {
        return new TopSetEntry(id, new WeekData.TopSet(BigDecimal.valueOf(kg), reps));
    }

    /** Build a LinkedHashMap of exerciseId → TopSet from a list of pairs. */
    private static Map<String, WeekData.TopSet> topSets(TopSetEntry... entries) {
        Map<String, WeekData.TopSet> out = new LinkedHashMap<>();
        for (TopSetEntry e : entries) out.put(e.id, e.set);
        return out;
    }

    private WeekData weekWith(Map<String, WeekData.TargetVsLogged> targets) {
        return weekWith(targets, Map.of(), Map.of());
    }

    private WeekData weekWith(
            Map<String, WeekData.TargetVsLogged> targets,
            Map<String, WeekData.TopSet> thisWeek,
            Map<String, WeekData.TopSet> prevWeek) {
        Map<WeeklyReportMuscle, Integer> muscles = new EnumMap<>(WeeklyReportMuscle.class);
        for (WeeklyReportMuscle m : WeeklyReportMuscle.values()) muscles.put(m, 0);
        return new WeekData(
                UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"),
                LocalDate.of(2026, 4, 6),
                LocalDate.of(2026, 4, 13),
                2, false, "Asha",
                4, 4, true, BigDecimal.valueOf(2840), 0,
                Collections.unmodifiableList(new ArrayList<>()),
                Collections.unmodifiableMap(new LinkedHashMap<>()),
                Collections.unmodifiableMap(muscles),
                0, 0, 0,
                Collections.unmodifiableMap(thisWeek),
                Collections.unmodifiableMap(prevWeek),
                Collections.unmodifiableList(new ArrayList<>()),
                Collections.unmodifiableMap(targets),
                Collections.unmodifiableMap(new LinkedHashMap<>()));
    }
}
