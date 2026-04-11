package com.fittribe.api.findings.rules;

import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushPullImbalanceRuleTest {

    private final PushPullImbalanceRule rule =
            new PushPullImbalanceRule(TestFindingsConfig.create());

    @Test
    void firesWhenPushIsZeroAndPullIsNonZero() {
        // Mirrors Harsh's live data: 0 push vs 4 pull — the most extreme case.
        WeekData week = WeekDataFixture.baseline()
                .pushCount(0).pullCount(4).allMusclesTrained()
                .build();

        List<Finding> out = rule.evaluate(week);

        assertEquals(1, out.size());
        Finding f = out.get(0);
        assertEquals("push_pull_imbalance", f.ruleId());
        assertEquals("RED", f.severity());
        assertTrue(f.detail().contains("0× push"), f.detail());
        assertTrue(f.detail().contains("4× pull"), f.detail());
    }

    @Test
    void doesNotFireWhenBalanced() {
        // 2 vs 3 → ratio 1.5 exactly, not strictly greater than 1.5.
        WeekData week = WeekDataFixture.baseline()
                .pushCount(2).pullCount(3).allMusclesTrained()
                .build();

        assertTrue(rule.evaluate(week).isEmpty());
    }

    @Test
    void doesNotFireWhenNoSessions() {
        WeekData week = WeekDataFixture.baseline()
                .pushCount(0).pullCount(0).allMusclesTrained()
                .build();

        assertTrue(rule.evaluate(week).isEmpty());
    }
}
