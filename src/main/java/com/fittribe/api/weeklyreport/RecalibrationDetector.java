package com.fittribe.api.weeklyreport;

import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives the set of plan-weight recalibrations implied by a week's
 * findings (Wynners A3.2 + v1.1 §A3.2).
 *
 * <p>Pure function — takes a list of {@link Finding}s and the frozen
 * {@link WeekData} they were generated from, returns zero or more
 * {@link Recalibration}s. No database access, no Spring context
 * dependencies beyond being a bean for the downstream
 * {@code WeeklyReportComputer} to inject. Kept independent from the
 * {@code FindingsRule} classes so the detection logic can be unit
 * tested without spinning up the rule engine.
 *
 * <h3>Why re-scan WeekData rather than read exercise ids off findings</h3>
 * {@link Finding} only carries {@code ruleId}, {@code severity},
 * {@code weight}, {@code title}, and {@code detail}. It does NOT
 * carry a structured list of affected exercise ids, because the
 * findings are primarily user-facing copy and adding a payload field
 * would make them harder to template. So the detector is told
 * <em>which kinds of problems fired</em> (via the rule ids in the
 * findings list) and re-derives <em>which exercises were affected</em>
 * directly from {@link WeekData}. This means the detection thresholds
 * must stay in sync with the rules in
 * {@code com.fittribe.api.findings.rules} — the constants here
 * deliberately match those rules byte-for-byte.
 *
 * <h3>Detection logic, rule by rule</h3>
 * <ul>
 *   <li><b>{@code single_too_light}</b> → emit one recalibration for
 *       the single exercise the rule would have flagged: the one with
 *       the worst ratio in the {@code [0.85, 0.90)} band. New target
 *       = logged max rounded to the nearest 2.5 kg.</li>
 *   <li><b>{@code multiple_too_light}</b> → emit one recalibration
 *       for every exercise in the batch (ratio strictly below 0.85).
 *       Same rounding.</li>
 *   <li><b>{@code pr_regression}</b> → emit one recalibration for
 *       every exercise whose top set dropped more than 5% week-over-
 *       week. New target = previous week's top set weight (the last
 *       confirmed working weight) — no rounding, honour exactly what
 *       the user hit. Skipped for exercises that don't have a plan
 *       target (custom workouts have nothing to recalibrate from).</li>
 * </ul>
 *
 * <h3>Skip conditions</h3>
 * An exercise is dropped silently if
 * {@code WeekData.targetExercises[id].targetKg} is null. That's the
 * "custom workout, no plan" case — there's no old target to recalibrate
 * from and nothing to persist into {@code pending_recalibrations}.
 *
 * <h3>Dedupe</h3>
 * When the same exercise would be flagged by two different finding
 * types (e.g. {@code multiple_too_light} and {@code pr_regression}
 * both firing), we keep the <em>most conservative</em> candidate — the
 * one with the lowest {@code newTargetKg}. A user who both hit the
 * wall and got a regression drop should be backed off to the lower of
 * the two recommendations, not the higher one.
 */
@Component
public class RecalibrationDetector {

    // ── Thresholds — must match the corresponding rule classes ──────────

    /** "Well below target" — matches {@code MultipleTooLightRule}. */
    private static final BigDecimal WELL_BELOW_RATIO = new BigDecimal("0.85");

    /** Inclusive lower bound of the too-light band — matches {@code SingleTooLightRule}. */
    private static final BigDecimal SINGLE_LOWER_RATIO = new BigDecimal("0.85");

    /** Exclusive upper bound of the too-light band — matches {@code SingleTooLightRule}. */
    private static final BigDecimal SINGLE_UPPER_RATIO = new BigDecimal("0.90");

    /** Regression cutoff — matches {@code PrRegressionRule}. */
    private static final BigDecimal REGRESSION_CUTOFF = new BigDecimal("0.95");

    /** Rounding increment for too-light recalibrations. */
    private static final BigDecimal ROUND_STEP = new BigDecimal("2.5");

    // ── Rule id constants — avoid typos in the dispatch below ───────────

    private static final String RULE_SINGLE_TOO_LIGHT  = "single_too_light";
    private static final String RULE_MULTIPLE_TOO_LIGHT = "multiple_too_light";
    private static final String RULE_PR_REGRESSION     = "pr_regression";

    /**
     * Derive the list of recalibrations implied by {@code findings} and
     * {@code week}. See class-level javadoc for semantics. Never throws
     * on empty input.
     */
    public List<Recalibration> detect(List<Finding> findings, WeekData week) {
        if (findings == null || findings.isEmpty() || week == null) {
            return List.of();
        }

        Set<String> firedRules = new HashSet<>();
        for (Finding f : findings) {
            if (f != null && f.ruleId() != null) firedRules.add(f.ruleId());
        }

        // LinkedHashMap so iteration order (and thus the output order)
        // is deterministic — handy for both logs and tests.
        Map<String, Recalibration> byExercise = new LinkedHashMap<>();

        if (firedRules.contains(RULE_SINGLE_TOO_LIGHT)) {
            for (Recalibration r : collectSingleTooLight(week)) merge(byExercise, r);
        }
        if (firedRules.contains(RULE_MULTIPLE_TOO_LIGHT)) {
            for (Recalibration r : collectMultipleTooLight(week)) merge(byExercise, r);
        }
        if (firedRules.contains(RULE_PR_REGRESSION)) {
            for (Recalibration r : collectPrRegression(week)) merge(byExercise, r);
        }

        return List.copyOf(byExercise.values());
    }

    // ── Per-rule collection ─────────────────────────────────────────────

    /**
     * Mirror {@code SingleTooLightRule.evaluate} — pick the single
     * exercise with the worst ratio in the {@code [0.85, 0.90)} band.
     * Null targets or null logged maxes are skipped (there's nothing
     * to compute a ratio from).
     */
    private static List<Recalibration> collectSingleTooLight(WeekData week) {
        WeekData.TargetVsLogged worst = null;
        BigDecimal worstRatio = null;

        for (WeekData.TargetVsLogged t : week.targetExercises().values()) {
            if (t.targetKg() == null || t.loggedMaxKg() == null) continue;
            if (t.targetKg().signum() <= 0) continue;

            BigDecimal ratio = t.loggedMaxKg().divide(t.targetKg(), 4, RoundingMode.HALF_UP);
            if (ratio.compareTo(SINGLE_LOWER_RATIO) < 0) continue;
            if (ratio.compareTo(SINGLE_UPPER_RATIO) >= 0) continue;

            if (worstRatio == null || ratio.compareTo(worstRatio) < 0) {
                worstRatio = ratio;
                worst = t;
            }
        }

        if (worst == null) return List.of();

        BigDecimal newTarget = roundTo25(worst.loggedMaxKg());
        String reason = "Logged max " + formatKg(worst.loggedMaxKg())
                + "kg vs target " + formatKg(worst.targetKg())
                + "kg — recalibrating down";
        return List.of(new Recalibration(
                worst.exerciseId(), worst.targetKg(), newTarget, reason));
    }

    /**
     * Mirror {@code MultipleTooLightRule.countWellBelowTarget} — emit
     * a recalibration for every exercise with {@code logged < 0.85 *
     * target}. Deliberately does <em>not</em> require the count to hit
     * {@code MIN_COUNT}: the caller already decided the multiple-too-
     * light finding fired, so we just enumerate the batch.
     */
    private static List<Recalibration> collectMultipleTooLight(WeekData week) {
        List<Recalibration> out = new ArrayList<>();
        for (WeekData.TargetVsLogged t : week.targetExercises().values()) {
            if (t.targetKg() == null || t.loggedMaxKg() == null) continue;
            if (t.targetKg().signum() <= 0) continue;

            BigDecimal cutoff = t.targetKg().multiply(WELL_BELOW_RATIO);
            if (t.loggedMaxKg().compareTo(cutoff) >= 0) continue;

            BigDecimal newTarget = roundTo25(t.loggedMaxKg());
            String reason = "Logged max " + formatKg(t.loggedMaxKg())
                    + "kg well below target " + formatKg(t.targetKg())
                    + "kg — recalibrating down";
            out.add(new Recalibration(
                    t.exerciseId(), t.targetKg(), newTarget, reason));
        }
        return out;
    }

    /**
     * Mirror {@code PrRegressionRule} — emit a recalibration for every
     * exercise whose top set dropped strictly more than 5% week-over-
     * week. New target is the previous week's confirmed top set weight,
     * not the logged max — the intent is to back off to a known-working
     * weight, not down to what they managed under whatever conditions
     * caused the regression.
     */
    private static List<Recalibration> collectPrRegression(WeekData week) {
        List<Recalibration> out = new ArrayList<>();
        for (Map.Entry<String, WeekData.TopSet> e : week.thisWeekTopSets().entrySet()) {
            String exerciseId = e.getKey();
            WeekData.TopSet thisWeek = e.getValue();
            WeekData.TopSet prevWeek = week.previousWeekTopSets().get(exerciseId);
            if (thisWeek == null || prevWeek == null) continue;

            BigDecimal curKg = thisWeek.weightKg();
            BigDecimal prevKg = prevWeek.weightKg();
            if (curKg == null || prevKg == null || prevKg.signum() <= 0) continue;

            // Not a regression if we stayed within the 95% floor.
            if (curKg.compareTo(prevKg.multiply(REGRESSION_CUTOFF)) >= 0) continue;

            // Must have a plan target to recalibrate from. Custom
            // workouts with no target get skipped — nothing to update.
            WeekData.TargetVsLogged t = week.targetExercises().get(exerciseId);
            if (t == null || t.targetKg() == null) continue;

            String reason = "Top set dropped from " + formatKg(prevKg)
                    + "kg to " + formatKg(curKg)
                    + "kg — backing off to last confirmed working weight";
            out.add(new Recalibration(exerciseId, t.targetKg(), prevKg, reason));
        }
        return out;
    }

    // ── Dedupe + helpers ────────────────────────────────────────────────

    /**
     * Merge {@code candidate} into {@code acc}, keeping whichever
     * recalibration has the lower {@code newTargetKg} when the same
     * exercise is already present. "Most conservative wins" — the
     * user gets backed off to the lower of two recommendations.
     */
    private static void merge(Map<String, Recalibration> acc, Recalibration candidate) {
        Recalibration existing = acc.get(candidate.exerciseId());
        if (existing == null
                || candidate.newTargetKg().compareTo(existing.newTargetKg()) < 0) {
            acc.put(candidate.exerciseId(), candidate);
        }
    }

    /**
     * Round a kg value to the nearest multiple of 2.5. Standard
     * half-up tiebreak. Not used for pr_regression recalibrations —
     * see class-level javadoc.
     */
    private static BigDecimal roundTo25(BigDecimal kg) {
        return kg.divide(ROUND_STEP, 0, RoundingMode.HALF_UP).multiply(ROUND_STEP);
    }

    /** Same kg-pretty-printer the rules use — "60.0" shows as "60". */
    private static String formatKg(BigDecimal kg) {
        if (kg == null) return "0";
        BigDecimal stripped = kg.stripTrailingZeros();
        if (stripped.scale() < 0) stripped = stripped.setScale(0);
        return stripped.toPlainString();
    }
}
