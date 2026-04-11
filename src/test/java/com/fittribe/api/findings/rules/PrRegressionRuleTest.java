package com.fittribe.api.findings.rules;

import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrRegressionRuleTest {

    private final PrRegressionRule rule = new PrRegressionRule(TestFindingsConfig.create());

    @Test
    void firesWhenTopSetDropsMoreThanFivePercent() {
        // 80 kg vs 100 kg last week = 20% drop — well over the 5% cutoff.
        WeekData week = WeekDataFixture.baseline()
                .exercise("bench-press", "Bench press")
                .thisWeekTop("bench-press", 80.0, 5)
                .previousWeekTop("bench-press", 100.0, 5)
                .build();

        List<Finding> out = rule.evaluate(week);

        assertEquals(1, out.size());
        Finding f = out.get(0);
        assertEquals("pr_regression", f.ruleId());
        assertEquals("RED", f.severity());
        assertEquals("Bench press went backwards", f.title());
        assertTrue(f.detail().contains("20%"),
                "detail should report 20% drop, got: " + f.detail());
    }

    @Test
    void doesNotFireWhenDropIsWithinTolerance() {
        // 96 / 100 = 0.96 → only a 4% drop, under the 5% cutoff.
        WeekData week = WeekDataFixture.baseline()
                .exercise("bench-press", "Bench press")
                .thisWeekTop("bench-press", 96.0, 5)
                .previousWeekTop("bench-press", 100.0, 5)
                .build();

        assertTrue(rule.evaluate(week).isEmpty());
    }

    @Test
    void doesNotFireInWeekOne() {
        WeekData week = WeekDataFixture.baseline()
                .weekOne()
                .exercise("bench-press", "Bench press")
                .thisWeekTop("bench-press", 50.0, 5)
                .previousWeekTop("bench-press", 100.0, 5)
                .build();

        assertTrue(rule.evaluate(week).isEmpty());
    }
}
