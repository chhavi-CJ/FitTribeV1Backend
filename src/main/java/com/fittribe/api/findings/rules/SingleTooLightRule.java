package com.fittribe.api.findings.rules;

import com.fittribe.api.config.FindingsConfig;
import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Fires when one or two exercises are modestly below target — 10-15%
 * (i.e., logged max in {@code [0.85 * target, 0.90 * target)}). Picks the
 * exercise with the worst ratio within that band as the single finding
 * for the week.
 *
 * <h3>Dedup with MultipleTooLight</h3>
 * If the {@link MultipleTooLightRule} would fire (3+ exercises &lt; 85% of
 * target), this rule stays silent — the RED "Targets too aggressive"
 * message replaces a per-exercise AMBER warning.
 *
 * Not eligible in week one — nothing to calibrate against.
 */
@Component
public class SingleTooLightRule extends AbstractFindingsRule {

    /** Inclusive lower bound of the "modestly below" band: {@code logged / target >= 0.85}. */
    private static final BigDecimal LOWER_RATIO = new BigDecimal("0.85");
    /** Exclusive upper bound: {@code logged / target < 0.90}. */
    private static final BigDecimal UPPER_RATIO = new BigDecimal("0.90");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public SingleTooLightRule(FindingsConfig config) { super(config); }

    @Override public String getRuleId() { return "single_too_light"; }

    @Override
    public List<Finding> evaluate(WeekData week) {
        if (suppressedForWeekOne(week)) return List.of();

        // Dedup — if MultipleTooLight would fire, stay silent.
        if (MultipleTooLightRule.countWellBelowTarget(week) >= MultipleTooLightRule.MIN_COUNT) {
            return List.of();
        }

        WeekData.TargetVsLogged worst = null;
        BigDecimal worstRatio = null;

        for (WeekData.TargetVsLogged t : week.targetExercises().values()) {
            if (t.targetKg() == null || t.loggedMaxKg() == null) continue;
            if (t.targetKg().signum() <= 0) continue;
            BigDecimal ratio = t.loggedMaxKg().divide(t.targetKg(), 4, RoundingMode.HALF_UP);
            if (ratio.compareTo(LOWER_RATIO) < 0) continue;
            if (ratio.compareTo(UPPER_RATIO) >= 0) continue;
            if (worstRatio == null || ratio.compareTo(worstRatio) < 0) {
                worstRatio = ratio;
                worst = t;
            }
        }

        if (worst == null) return List.of();

        int pctBelow = HUNDRED.subtract(worstRatio.multiply(HUNDRED))
                .setScale(0, RoundingMode.HALF_UP).intValue();

        WeekData.ExerciseMeta meta = week.exerciseCatalog().get(worst.exerciseId());
        String exerciseName = (meta != null && meta.name() != null)
                ? meta.name() : worst.exerciseId();

        return List.of(buildFinding(Map.of(
                "exercise_name", exerciseName,
                "logged_kg", formatKg(worst.loggedMaxKg()),
                "target_kg", formatKg(worst.targetKg()),
                "pct", String.valueOf(pctBelow))));
    }

    /** Render a kg value without trailing zeros so "60.0" shows as "60". */
    private static String formatKg(BigDecimal kg) {
        if (kg == null) return "0";
        BigDecimal stripped = kg.stripTrailingZeros();
        if (stripped.scale() < 0) stripped = stripped.setScale(0);
        return stripped.toPlainString();
    }
}
