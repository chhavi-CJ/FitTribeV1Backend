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
 * Fires when one exercise's top set this week dropped more than 5% below the
 * same exercise's top set the previous week. If multiple exercises regressed,
 * the one with the biggest percentage drop wins — one finding per week, not
 * per exercise.
 *
 * Not eligible in week one (no previous week to compare against).
 *
 * Data sources from {@link WeekData}:
 * <ul>
 *   <li>{@code thisWeekTopSets} — from this week's exercises JSONB</li>
 *   <li>{@code previousWeekTopSets} — from set_logs in the prior 7 days</li>
 *   <li>{@code exerciseCatalog} — for the display name</li>
 * </ul>
 */
@Component
public class PrRegressionRule extends AbstractFindingsRule {

    /** Fire when {@code this_week_kg / prev_week_kg < 0.95} (drop strictly greater than 5%). */
    private static final BigDecimal REGRESSION_CUTOFF = new BigDecimal("0.95");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public PrRegressionRule(FindingsConfig config) { super(config); }

    @Override public String getRuleId() { return "pr_regression"; }

    @Override
    public List<Finding> evaluate(WeekData week) {
        if (suppressedForWeekOne(week)) return List.of();

        String worstExerciseId = null;
        BigDecimal worstDropPct = null;

        for (Map.Entry<String, WeekData.TopSet> e : week.thisWeekTopSets().entrySet()) {
            String exerciseId = e.getKey();
            WeekData.TopSet thisWeek = e.getValue();
            WeekData.TopSet prevWeek = week.previousWeekTopSets().get(exerciseId);
            if (thisWeek == null || prevWeek == null) continue;
            BigDecimal curKg = thisWeek.weightKg();
            BigDecimal prevKg = prevWeek.weightKg();
            if (curKg == null || prevKg == null || prevKg.signum() <= 0) continue;

            // Not a regression if we hit the 95% floor.
            if (curKg.compareTo(prevKg.multiply(REGRESSION_CUTOFF)) >= 0) continue;

            BigDecimal dropPct = prevKg.subtract(curKg)
                    .divide(prevKg, 4, RoundingMode.HALF_UP)
                    .multiply(HUNDRED);
            if (worstDropPct == null || dropPct.compareTo(worstDropPct) > 0) {
                worstDropPct = dropPct;
                worstExerciseId = exerciseId;
            }
        }

        if (worstExerciseId == null) return List.of();

        WeekData.ExerciseMeta meta = week.exerciseCatalog().get(worstExerciseId);
        String exerciseName = (meta != null && meta.name() != null) ? meta.name() : worstExerciseId;
        int pctInt = worstDropPct.setScale(0, RoundingMode.HALF_UP).intValue();

        return List.of(buildFinding(Map.of(
                "exercise_name", exerciseName,
                "pct", String.valueOf(pctInt))));
    }
}
