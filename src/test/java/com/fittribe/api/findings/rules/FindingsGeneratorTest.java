package com.fittribe.api.findings.rules;

import com.fittribe.api.config.FindingsConfig;
import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.FindingsGenerator;
import com.fittribe.api.findings.FindingsRule;
import com.fittribe.api.findings.WeekData;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link FindingsGenerator}. The generator is exercised with
 * stub {@link FindingsRule}s (see {@link #fakeRule}) rather than the real
 * rule beans so the tests stay focused on the orchestration contract:
 * week-one filtering, severity+weight sort, trim-to-4, and the always-one-
 * green backfill. Real-rule behaviour is already covered by the per-rule
 * tests in this package.
 */
class FindingsGeneratorTest {

    private final FindingsConfig config = TestFindingsConfig.create();

    // ── 1. Empty week returns a fallback green ──────────────────────────

    @Test
    void emptyWeekReturnsFallbackGreen() {
        WeekData week = WeekDataFixture.baseline()
                .sessionsGoal(4).sessionsLogged(0).totalKgVolume(0)
                .build();

        FindingsGenerator gen = new FindingsGenerator(List.of(), config);
        List<Finding> out = gen.generate(week);

        assertEquals(1, out.size(), "empty week with no rules should yield exactly one fallback");
        Finding f = out.get(0);
        assertEquals("fallback_green", f.ruleId());
        assertEquals("GREEN", f.severity());
        // 0 sessions + 0 kg → only the "always" fallback matches
        assertEquals("Effort recorded", f.title());
    }

    // ── 2. Five RED findings trim to four, no green injection ───────────

    @Test
    void fiveRedFindingsTrimToFourNoGreenInjection() {
        FindingsRule r = fakeRule("multi_red", true,
                red("a", 95), red("b", 90), red("c", 85), red("d", 80), red("e", 75));

        FindingsGenerator gen = new FindingsGenerator(List.of(r), config);
        List<Finding> out = gen.generate(WeekDataFixture.baseline().build());

        assertEquals(4, out.size());
        for (Finding f : out) assertEquals("RED", f.severity());
        // Top 4 kept in weight order, the w=75 finding dropped
        assertEquals(List.of("a", "b", "c", "d"),
                out.stream().map(Finding::ruleId).toList());
    }

    // ── 3. Three RED + no green → padded with a fallback green ──────────

    @Test
    void threeRedFindingsGetFallbackGreenPadding() {
        FindingsRule r = fakeRule("three_red", true,
                red("a", 95), red("b", 90), red("c", 85));

        WeekData week = WeekDataFixture.baseline()
                .sessionsGoal(4).sessionsLogged(3).totalKgVolume(1200)
                .build();

        FindingsGenerator gen = new FindingsGenerator(List.of(r), config);
        List<Finding> out = gen.generate(week);

        assertEquals(4, out.size());
        assertEquals("RED",   out.get(0).severity());
        assertEquals("RED",   out.get(1).severity());
        assertEquals("RED",   out.get(2).severity());
        assertEquals("GREEN", out.get(3).severity());
        assertEquals("fallback_green", out.get(3).ruleId());
        // sessions >= 1 wins over kg > 0 because it is listed first in the YAML
        assertEquals("3 sessions logged", out.get(3).title());
    }

    // ── 4. Week 1 mode skips ineligible rules ───────────────────────────

    @Test
    void weekOneSkipsIneligibleRules() {
        FindingsRule eligible   = fakeRule("week_one_ok",   true,  red("ok", 90));
        FindingsRule ineligible = fakeRule("week_one_skip", false, red("skip", 95));

        WeekData week = WeekDataFixture.baseline()
                .weekOne().sessionsGoal(4).sessionsLogged(1).totalKgVolume(500)
                .build();

        FindingsGenerator gen = new FindingsGenerator(
                List.of(eligible, ineligible), config);
        List<Finding> out = gen.generate(week);

        // The w=95 RED from the ineligible rule must NOT appear
        assertFalse(out.stream().anyMatch(f -> "skip".equals(f.ruleId())),
                "week-one-ineligible rule should be suppressed in week one");
        assertTrue(out.stream().anyMatch(f -> "ok".equals(f.ruleId())));
    }

    // ── 5. Findings sorted by severity then weight ──────────────────────

    @Test
    void findingsAreSortedBySeverityThenWeight() {
        // Intentionally emit out-of-order so the sort has work to do.
        FindingsRule r = fakeRule("mixed", true,
                green("g1", 60),
                red("r1", 80),
                amber("a1", 70),
                red("r2", 95),
                amber("a2", 50));

        FindingsGenerator gen = new FindingsGenerator(List.of(r), config);
        List<Finding> out = gen.generate(WeekDataFixture.baseline().build());

        assertEquals(4, out.size());
        // RED w=95, RED w=80, AMBER w=70, AMBER w=50 — green w=60 drops off
        assertEquals("r2", out.get(0).ruleId());
        assertEquals("r1", out.get(1).ruleId());
        assertEquals("a1", out.get(2).ruleId());
        assertEquals("a2", out.get(3).ruleId());
        // And the GREEN got squeezed out — no backfill because slot 4 is full
        assertFalse(out.stream().anyMatch(f -> "GREEN".equals(f.severity())));
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static Finding red(String id, int weight)   { return new Finding(id, "RED",   weight, "t", "d"); }
    private static Finding amber(String id, int weight) { return new Finding(id, "AMBER", weight, "t", "d"); }
    private static Finding green(String id, int weight) { return new Finding(id, "GREEN", weight, "t", "d"); }

    /**
     * A minimal FindingsRule that emits a pre-built list. The generator only
     * looks at week-one eligibility and {@link FindingsRule#evaluate(WeekData)},
     * so we don't need a FindingsConfig-backed rule here.
     */
    private static FindingsRule fakeRule(String id, boolean weekOneEligible, Finding... findings) {
        List<Finding> out = Arrays.asList(findings);
        return new FindingsRule() {
            @Override public List<Finding> evaluate(WeekData week) { return out; }
            @Override public String getRuleId() { return id; }
            @Override public boolean isWeekOneEligible() { return weekOneEligible; }
            @Override public String interpolate(String template, Map<String, String> vars) { return template; }
        };
    }
}
