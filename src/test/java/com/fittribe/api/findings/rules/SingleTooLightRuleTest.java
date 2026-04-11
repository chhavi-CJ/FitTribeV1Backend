package com.fittribe.api.findings.rules;

import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleTooLightRuleTest {

    private final SingleTooLightRule rule =
            new SingleTooLightRule(TestFindingsConfig.create());

    @Test
    void firesWhenOneExerciseIsModestlyBelowTarget() {
        // 87 / 100 = 0.87 → in [0.85, 0.90) → fires.
        WeekData week = WeekDataFixture.baseline()
                .exercise("bench-press", "Bench press")
                .target("bench-press", 100.0, 87.0)
                .build();

        List<Finding> out = rule.evaluate(week);

        assertEquals(1, out.size());
        Finding f = out.get(0);
        assertEquals("single_too_light", f.ruleId());
        assertEquals("AMBER", f.severity());
        assertEquals("Bench press too conservative", f.title());
        assertTrue(f.detail().contains("87 kg"), f.detail());
        assertTrue(f.detail().contains("100 kg"), f.detail());
        assertTrue(f.detail().contains("13%"), f.detail());
    }

    @Test
    void doesNotFireWhenCloseToTarget() {
        // 95 / 100 = 0.95 → above upper cutoff (0.90) → does not fire.
        WeekData week = WeekDataFixture.baseline()
                .exercise("bench-press", "Bench press")
                .target("bench-press", 100.0, 95.0)
                .build();

        assertTrue(rule.evaluate(week).isEmpty());
    }

    @Test
    void staysSilentWhenMultipleTooLightWouldFire() {
        // 3 exercises at < 85% target — MultipleTooLight takes over.
        WeekData week = WeekDataFixture.baseline()
                .exercise("bench-press", "Bench press")
                .target("bench-press", 100.0, 80.0)
                .target("squat", 100.0, 80.0)
                .target("overhead-press", 100.0, 80.0)
                .build();

        assertTrue(rule.evaluate(week).isEmpty());
    }

    @Test
    void doesNotFireInWeekOne() {
        WeekData week = WeekDataFixture.baseline()
                .weekOne()
                .exercise("bench-press", "Bench press")
                .target("bench-press", 100.0, 87.0)
                .build();

        assertTrue(rule.evaluate(week).isEmpty());
    }
}
