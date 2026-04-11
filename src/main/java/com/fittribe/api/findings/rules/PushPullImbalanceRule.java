package com.fittribe.api.findings.rules;

import com.fittribe.api.config.FindingsConfig;
import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Fires when a week's push and pull session counts are unbalanced by more
 * than 1.5× in either direction. The common case is quad/chest-heavy users
 * who under-train pulling (back/biceps) — we want to flag this before the
 * shoulder imbalance becomes chronic.
 *
 * Eligible in week one — an imbalance is visible even from the very first
 * week of data.
 *
 * <h3>Zero handling — small deviation from the v1.0 spec</h3>
 * The v1.0 spec said "fires if push/pull &gt; 1.5 or &lt; 0.67, and both
 * counts &gt; 0". Strict "both &gt; 0" gates out exactly the most extreme
 * case — {@code push=0 vs pull=4} — which is precisely when the finding is
 * most useful. So we relax it: fire if at least one is non-zero AND the
 * larger exceeds 1.5× the smaller. Zero-vs-zero returns empty
 * ({@code LowAdherenceRule} covers the "no sessions" story instead).
 */
@Component
public class PushPullImbalanceRule extends AbstractFindingsRule {

    public PushPullImbalanceRule(FindingsConfig config) { super(config); }

    @Override public String getRuleId() { return "push_pull_imbalance"; }

    @Override
    public List<Finding> evaluate(WeekData week) {
        if (suppressedForWeekOne(week)) return List.of();

        int push = week.pushSessionCount();
        int pull = week.pullSessionCount();
        if (push + pull == 0) return List.of();

        // Imbalance: larger > 1.5× smaller. Using doubles is fine — session
        // counts are small integers, no precision concerns.
        boolean imbalanced = push > 1.5 * pull || pull > 1.5 * push;
        if (!imbalanced) return List.of();

        return List.of(buildFinding(Map.of(
                "push_count", String.valueOf(push),
                "pull_count", String.valueOf(pull))));
    }
}
