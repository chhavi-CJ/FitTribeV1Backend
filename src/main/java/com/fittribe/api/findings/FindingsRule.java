package com.fittribe.api.findings;

import java.util.List;
import java.util.Map;

/**
 * A finding rule — evaluates a {@link WeekData} snapshot and emits zero or
 * more {@link Finding} objects. Rules must be stateless and must NOT reach
 * back into the database: everything they need is already on the WeekData.
 *
 * <h3>Return shape: {@code List<Finding>} — a deviation from the v1.0 plan</h3>
 * The Wynners v1.0 spec described {@code evaluate} as "{@code Finding or null}".
 * That works for 7 of the 8 A2.1 rules but not for {@code MissingMuscleRule},
 * which can fire up to 8 findings in one week — one per uncovered tile, per
 * the A2 decision "fire for all 8 tiles, no big 4 filtering". To avoid a
 * special case for one rule, every rule returns a {@code List<Finding>} and
 * uses {@link List#of()} to signal "did not fire". The
 * {@code AbstractFindingsRule.buildFinding} helper keeps the single-finding
 * case a one-liner (wrap in {@link List#of(Object)}).
 *
 * <h3>Week-one filtering</h3>
 * Some rules (regression, too-light, multi-PR) need prior-week data to make
 * sense and must stay silent in the user's first tracked week. That's a YAML
 * property ({@code week_one_eligible}) rather than Java — see
 * {@code src/main/resources/findings-templates.yml}. Implementations should
 * consult {@link #isWeekOneEligible()} at the top of {@code evaluate} and
 * bail out early if it's false and the week is week one.
 */
public interface FindingsRule {

    /**
     * Evaluate this rule against a week's data.
     * Return an empty list if the rule does not fire.
     */
    List<Finding> evaluate(WeekData week);

    /**
     * Stable rule identifier matching a key under
     * {@code fittribe.findings.templates} in the YAML.
     */
    String getRuleId();

    /**
     * Whether this rule is allowed to fire during the user's first tracked
     * week (when prior-week data is empty by definition). Read from the YAML
     * template, not hard-coded.
     */
    boolean isWeekOneEligible();

    /**
     * Simple {@code {token}}-style template interpolation. Null-safe on the
     * template (returns null), ignores unknown tokens in the vars map, leaves
     * unknown tokens in the template untouched (fail-open rather than throwing
     * so one bad variable does not blank out an otherwise valid finding).
     */
    default String interpolate(String template, Map<String, String> vars) {
        if (template == null) return null;
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }
}
