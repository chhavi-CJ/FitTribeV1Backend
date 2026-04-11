package com.fittribe.api.findings.rules;

import com.fittribe.api.config.FindingsConfig;
import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import com.fittribe.api.findings.WeeklyReportMuscle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fires ONCE PER muscle tile that got zero sessions this week. That can be
 * up to eight findings from a single rule; the downstream
 * {@code FindingsGenerator} (A2.2) trims the combined findings list to 4 by
 * severity + weight.
 *
 * The scope is all 8 weekly-report tiles — Chest, Back, Shoulders, Biceps,
 * Triceps, Quads (LEGS_QUADS), Hamstrings, Core. No "big 4" filtering.
 * "Noisy but honest" is the deliberate A2 decision: a user who skipped
 * core should still hear about core.
 *
 * Eligible in week one.
 */
@Component
public class MissingMuscleRule extends AbstractFindingsRule {

    /**
     * User-facing labels for each tile. "Quads" reads better than
     * "Legs quads" given "Hamstrings" sits next to it in the report.
     */
    private static final Map<WeeklyReportMuscle, String> TILE_DISPLAY_NAMES = Map.of(
            WeeklyReportMuscle.CHEST, "Chest",
            WeeklyReportMuscle.BACK_LATS, "Back",
            WeeklyReportMuscle.SHOULDERS, "Shoulders",
            WeeklyReportMuscle.BICEPS, "Biceps",
            WeeklyReportMuscle.TRICEPS, "Triceps",
            WeeklyReportMuscle.LEGS_QUADS, "Quads",
            WeeklyReportMuscle.HAMSTRINGS, "Hamstrings",
            WeeklyReportMuscle.CORE, "Core");

    public MissingMuscleRule(FindingsConfig config) { super(config); }

    @Override public String getRuleId() { return "missing_muscle"; }

    @Override
    public List<Finding> evaluate(WeekData week) {
        if (suppressedForWeekOne(week)) return List.of();

        List<Finding> out = new ArrayList<>();
        for (WeeklyReportMuscle tile : WeeklyReportMuscle.values()) {
            int count = week.sessionsByMuscle().getOrDefault(tile, 0);
            if (count != 0) continue;
            String name = TILE_DISPLAY_NAMES.getOrDefault(tile, tile.name());
            out.add(buildFinding(Map.of("muscle_name", name)));
        }
        return out;
    }
}
