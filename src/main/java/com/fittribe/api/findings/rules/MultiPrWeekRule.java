package com.fittribe.api.findings.rules;

import com.fittribe.api.config.FindingsConfig;
import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Fires when the user set 3 or more personal records in a single week —
 * the "on fire" celebration finding.
 *
 * Not eligible in week one: every lift is effectively a "PR" in week one
 * (there's no history to beat), so the template is gated off to avoid
 * cheap praise.
 */
@Component
public class MultiPrWeekRule extends AbstractFindingsRule {

    /** Fire at 3 or more PRs. */
    private static final int MIN_PRS = 3;

    public MultiPrWeekRule(FindingsConfig config) { super(config); }

    @Override public String getRuleId() { return "multi_pr_week"; }

    @Override
    public List<Finding> evaluate(WeekData week) {
        if (suppressedForWeekOne(week)) return List.of();

        if (week.prCount() < MIN_PRS) return List.of();

        return List.of(buildFinding(Map.of(
                "pr_count", String.valueOf(week.prCount()))));
    }
}
