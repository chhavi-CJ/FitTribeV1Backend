package com.fittribe.api.findings.rules;

import com.fittribe.api.config.FindingsConfig;
import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Fires when the user hit (or exceeded) their weekly session goal — the
 * simple "you showed up every time" GREEN finding.
 *
 * Eligible in week one: showing up is showing up.
 */
@Component
public class FullConsistencyRule extends AbstractFindingsRule {

    public FullConsistencyRule(FindingsConfig config) { super(config); }

    @Override public String getRuleId() { return "full_consistency"; }

    @Override
    public List<Finding> evaluate(WeekData week) {
        if (suppressedForWeekOne(week)) return List.of();

        if (!week.weeklyGoalHit()) return List.of();

        return List.of(buildFinding(Map.of()));
    }
}
