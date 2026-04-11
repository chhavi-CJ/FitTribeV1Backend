package com.fittribe.api.findings.rules;

import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullConsistencyRuleTest {

    private final FullConsistencyRule rule =
            new FullConsistencyRule(TestFindingsConfig.create());

    @Test
    void firesWhenGoalHit() {
        WeekData week = WeekDataFixture.baseline()
                .sessionsGoal(4).sessionsLogged(4).allMusclesTrained()
                .build();

        List<Finding> out = rule.evaluate(week);
        assertEquals(1, out.size());
        Finding f = out.get(0);
        assertEquals("full_consistency", f.ruleId());
        assertEquals("GREEN", f.severity());
        assertEquals("Full consistency", f.title());
    }

    @Test
    void doesNotFireWhenShortOfGoal() {
        WeekData week = WeekDataFixture.baseline()
                .sessionsGoal(4).sessionsLogged(3).allMusclesTrained()
                .build();

        assertTrue(rule.evaluate(week).isEmpty());
    }
}
