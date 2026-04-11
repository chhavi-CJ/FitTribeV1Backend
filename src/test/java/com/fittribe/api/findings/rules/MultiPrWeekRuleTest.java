package com.fittribe.api.findings.rules;

import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiPrWeekRuleTest {

    private final MultiPrWeekRule rule = new MultiPrWeekRule(TestFindingsConfig.create());

    @Test
    void firesAtThreePrs() {
        WeekData week = WeekDataFixture.baseline()
                .prCount(3).allMusclesTrained()
                .build();

        List<Finding> out = rule.evaluate(week);
        assertEquals(1, out.size());
        Finding f = out.get(0);
        assertEquals("multi_pr_week", f.ruleId());
        assertEquals("GREEN", f.severity());
        assertEquals("3 PRs this week", f.title());
    }

    @Test
    void doesNotFireBelowThreshold() {
        WeekData week = WeekDataFixture.baseline()
                .prCount(2).allMusclesTrained()
                .build();

        assertTrue(rule.evaluate(week).isEmpty());
    }

    @Test
    void doesNotFireInWeekOne() {
        WeekData week = WeekDataFixture.baseline()
                .weekOne().prCount(10).allMusclesTrained()
                .build();

        assertTrue(rule.evaluate(week).isEmpty());
    }
}
