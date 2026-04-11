package com.fittribe.api.findings.rules;

import com.fittribe.api.config.FindingsConfig;
import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.FindingsRule;
import com.fittribe.api.findings.WeekData;

import java.util.Map;

/**
 * Package-private base class that wires each rule to its {@link FindingsConfig}
 * template and provides the shared template-lookup + finding-construction
 * helpers. Keeps the 8 concrete rule classes focused on "is my condition
 * true?" logic.
 *
 * <h3>Template resolution</h3>
 * {@link #template()} throws {@link IllegalStateException} if the YAML is
 * missing an entry for this rule — we want that to surface at test / boot
 * time, not at request time for one unlucky user.
 *
 * <h3>Week-one handling</h3>
 * {@link #isWeekOneEligible()} reads straight from the template so the YAML
 * stays the single source of truth. {@link #suppressedForWeekOne(WeekData)}
 * is the convenience guard every rule calls at the top of {@code evaluate}.
 */
abstract class AbstractFindingsRule implements FindingsRule {

    protected final FindingsConfig config;

    protected AbstractFindingsRule(FindingsConfig config) {
        this.config = config;
    }

    protected FindingsConfig.FindingTemplate template() {
        FindingsConfig.FindingTemplate t = config.getTemplates().get(getRuleId());
        if (t == null) {
            throw new IllegalStateException(
                    "Findings template not found for rule: " + getRuleId()
                            + " — check findings-templates.yml");
        }
        return t;
    }

    @Override
    public boolean isWeekOneEligible() {
        return template().isWeekOneEligible();
    }

    /**
     * Returns true if this rule must not fire for the given week because
     * it requires prior-week data and the user is in week one.
     */
    protected boolean suppressedForWeekOne(WeekData week) {
        return week.isWeekOne() && !isWeekOneEligible();
    }

    /** Build a Finding from this rule's template + variable bindings (primary detail). */
    protected Finding buildFinding(Map<String, String> vars) {
        return buildFinding(vars, false);
    }

    /**
     * Build a Finding. If {@code useAltDetail} is true and {@code detail_alt}
     * is set on the template, the alt is used instead of the primary detail
     * (currently only {@code multi_pr_week} defines one).
     */
    protected Finding buildFinding(Map<String, String> vars, boolean useAltDetail) {
        FindingsConfig.FindingTemplate t = template();
        String detailTemplate = (useAltDetail && t.getDetailAlt() != null)
                ? t.getDetailAlt() : t.getDetail();
        return new Finding(
                getRuleId(),
                t.getSeverity(),
                t.getWeight(),
                interpolate(t.getTitle(), vars),
                interpolate(detailTemplate, vars));
    }
}
