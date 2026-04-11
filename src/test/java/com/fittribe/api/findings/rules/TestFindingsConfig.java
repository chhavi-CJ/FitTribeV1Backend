package com.fittribe.api.findings.rules;

import com.fittribe.api.config.FindingsConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-rolled {@link FindingsConfig} for rule unit tests. Mirrors the copy
 * in {@code src/main/resources/findings-templates.yml} so rule tests do not
 * need a full Spring context.
 *
 * <h3>Drift warning</h3>
 * If the YAML changes, this class must be updated in lockstep. There is no
 * automated parity check today — if duplication causes friction, add one
 * via a SnakeYAML load or a parity test that boots {@link FindingsConfig}
 * once with {@code @SpringBootTest}.
 */
final class TestFindingsConfig {

    private TestFindingsConfig() {}

    static FindingsConfig create() {
        FindingsConfig c = new FindingsConfig();
        Map<String, FindingsConfig.FindingTemplate> templates = new LinkedHashMap<>();
        templates.put("pr_regression", t("RED", 90,
                "{exercise_name} went backwards",
                "Top set dropped {pct}% from last week. Worth checking sleep and recovery.",
                null, false));
        templates.put("push_pull_imbalance", t("RED", 85,
                "Push-pull imbalance",
                "{push_count}× push vs {pull_count}× pull. Sustained, this elevates shoulder injury risk.",
                null, true));
        templates.put("missing_muscle", t("RED", 95,
                "{muscle_name} completely absent",
                "You did not train {muscle_name} at all this week. Skipping a major muscle reduces overall growth.",
                null, true));
        templates.put("multiple_too_light", t("AMBER", 70,
                "Targets too aggressive",
                "{count} exercises came in well below target. We are recalibrating down.",
                null, false));
        templates.put("single_too_light", t("AMBER", 60,
                "{exercise_name} too conservative",
                "{logged_kg} kg vs {target_kg} kg target — {pct}% below plan. We will recalibrate.",
                null, false));
        templates.put("low_adherence", t("AMBER", 75,
                "Missed {missed_count} session{plural}",
                "What got in the way? Consistency is the single biggest predictor of progress.",
                null, true));
        templates.put("multi_pr_week", t("GREEN", 50,
                "{pr_count} PRs this week",
                "Best week so far.",
                "Solid progress.",
                false));
        templates.put("full_consistency", t("GREEN", 55,
                "Full consistency",
                "Every session, no misses.",
                null, true));
        c.setTemplates(templates);

        List<FindingsConfig.FallbackGreen> fallbacks = new ArrayList<>();
        fallbacks.add(fg("{sessions} sessions logged",
                "You showed up {sessions} times this week. That is the foundation.",
                "sessions >= 1"));
        fallbacks.add(fg("Volume logged",
                "You moved {kg} kg of iron this week. Real work.",
                "kg > 0"));
        fallbacks.add(fg("Effort recorded",
                "Every set you log makes the next week smarter.",
                "always"));
        c.setFallbackGreens(fallbacks);
        return c;
    }

    private static FindingsConfig.FindingTemplate t(
            String severity, int weight, String title, String detail,
            String detailAlt, boolean weekOneEligible) {
        FindingsConfig.FindingTemplate tmpl = new FindingsConfig.FindingTemplate();
        tmpl.setSeverity(severity);
        tmpl.setWeight(weight);
        tmpl.setTitle(title);
        tmpl.setDetail(detail);
        tmpl.setDetailAlt(detailAlt);
        tmpl.setWeekOneEligible(weekOneEligible);
        return tmpl;
    }

    private static FindingsConfig.FallbackGreen fg(
            String title, String detail, String requires) {
        FindingsConfig.FallbackGreen g = new FindingsConfig.FallbackGreen();
        g.setTitle(title);
        g.setDetail(detail);
        g.setRequires(requires);
        return g;
    }
}
