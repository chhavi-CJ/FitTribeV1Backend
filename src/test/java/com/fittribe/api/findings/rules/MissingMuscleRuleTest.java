package com.fittribe.api.findings.rules;

import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import com.fittribe.api.findings.WeeklyReportMuscle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissingMuscleRuleTest {

    private final MissingMuscleRule rule =
            new MissingMuscleRule(TestFindingsConfig.create());

    @Test
    void firesOncePerMissingMuscle() {
        // Mirrors Harsh's live data: triceps and quads at 0, everything
        // else covered by at least one session.
        WeekData week = WeekDataFixture.baseline()
                .allMusclesTrained()
                .muscleSessions(WeeklyReportMuscle.TRICEPS, 0)
                .muscleSessions(WeeklyReportMuscle.LEGS_QUADS, 0)
                .build();

        List<Finding> out = rule.evaluate(week);

        assertEquals(2, out.size());
        Set<String> titles = out.stream().map(Finding::title).collect(Collectors.toSet());
        assertTrue(titles.contains("Triceps completely absent"), titles.toString());
        assertTrue(titles.contains("Quads completely absent"), titles.toString());
        for (Finding f : out) {
            assertEquals("missing_muscle", f.ruleId());
            assertEquals("RED", f.severity());
        }
    }

    @Test
    void doesNotFireWhenAllMusclesCovered() {
        WeekData week = WeekDataFixture.baseline()
                .allMusclesTrained()
                .build();

        assertTrue(rule.evaluate(week).isEmpty());
    }
}
