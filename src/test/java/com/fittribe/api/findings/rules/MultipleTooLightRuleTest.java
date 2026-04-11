package com.fittribe.api.findings.rules;

import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultipleTooLightRuleTest {

    private final MultipleTooLightRule rule =
            new MultipleTooLightRule(TestFindingsConfig.create());

    @Test
    void firesWhenThreeOrMoreExercisesAreWellBelowTarget() {
        // 80 < 100 * 0.85 = 85 → each exercise is well below target.
        WeekData week = WeekDataFixture.baseline()
                .target("bench-press", 100.0, 80.0)
                .target("squat", 100.0, 80.0)
                .target("overhead-press", 100.0, 80.0)
                .build();

        List<Finding> out = rule.evaluate(week);

        assertEquals(1, out.size());
        Finding f = out.get(0);
        assertEquals("multiple_too_light", f.ruleId());
        assertEquals("AMBER", f.severity());
        assertTrue(f.detail().contains("3 exercises"), f.detail());
    }

    @Test
    void doesNotFireWithFewerThanThree() {
        WeekData week = WeekDataFixture.baseline()
                .target("bench-press", 100.0, 80.0)
                .target("squat", 100.0, 80.0)
                .build();

        assertTrue(rule.evaluate(week).isEmpty());
    }

    @Test
    void doesNotFireInWeekOne() {
        WeekData week = WeekDataFixture.baseline()
                .weekOne()
                .target("bench-press", 100.0, 80.0)
                .target("squat", 100.0, 80.0)
                .target("overhead-press", 100.0, 80.0)
                .build();

        assertTrue(rule.evaluate(week).isEmpty());
    }
}
