package com.fittribe.api.findings.rules;

import com.fittribe.api.config.FindingsConfig;
import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Fires when three or more exercises this week came in significantly below
 * their {@code ai_planned_weights} target — defined as logged max &lt; 85% of
 * target. The intent: flag that the AI's plan is too aggressive overall, not
 * that the user is slacking on one particular lift.
 *
 * Exposes {@link #countWellBelowTarget(WeekData)} as package-private so
 * {@link SingleTooLightRule} can check the same threshold and stay quiet
 * when this rule would fire (dedup).
 *
 * Not eligible in week one — targets are recalibrated based on week-one
 * logged max, so there is nothing to critique yet.
 */
@Component
public class MultipleTooLightRule extends AbstractFindingsRule {

    /** "Well below target" = {@code logged max < 85% of target}. */
    private static final BigDecimal WELL_BELOW_RATIO = new BigDecimal("0.85");

    /** Minimum count of well-below-target exercises required to fire. */
    static final int MIN_COUNT = 3;

    public MultipleTooLightRule(FindingsConfig config) { super(config); }

    @Override public String getRuleId() { return "multiple_too_light"; }

    @Override
    public List<Finding> evaluate(WeekData week) {
        if (suppressedForWeekOne(week)) return List.of();

        int count = countWellBelowTarget(week);
        if (count < MIN_COUNT) return List.of();

        return List.of(buildFinding(Map.of("count", String.valueOf(count))));
    }

    /**
     * Shared count used by this rule and by {@link SingleTooLightRule} for
     * dedup. Counts exercises that have both a target and a logged max,
     * where logged max is strictly less than 85% of target.
     */
    static int countWellBelowTarget(WeekData week) {
        int count = 0;
        for (WeekData.TargetVsLogged t : week.targetExercises().values()) {
            if (t.targetKg() == null || t.loggedMaxKg() == null) continue;
            if (t.targetKg().signum() <= 0) continue;
            BigDecimal cutoff = t.targetKg().multiply(WELL_BELOW_RATIO);
            if (t.loggedMaxKg().compareTo(cutoff) < 0) count++;
        }
        return count;
    }
}
