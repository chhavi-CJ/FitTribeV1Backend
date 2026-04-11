package com.fittribe.api.findings.rules;

import com.fittribe.api.config.FindingsConfig;
import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Fires when the user logged fewer sessions than their weekly goal.
 * One finding per week with {@code missed_count = goal - logged}.
 *
 * Eligible in week one — missing sessions is an equally valid signal
 * regardless of whether the user is a veteran or a brand-new account.
 */
@Component
public class LowAdherenceRule extends AbstractFindingsRule {

    public LowAdherenceRule(FindingsConfig config) { super(config); }

    @Override public String getRuleId() { return "low_adherence"; }

    @Override
    public List<Finding> evaluate(WeekData week) {
        if (suppressedForWeekOne(week)) return List.of();

        int missed = week.sessionsGoal() - week.sessionsLogged();
        if (missed <= 0) return List.of();

        return List.of(buildFinding(Map.of(
                "missed_count", String.valueOf(missed),
                "plural", missed == 1 ? "" : "s")));
    }
}
