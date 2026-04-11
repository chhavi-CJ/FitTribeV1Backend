package com.fittribe.api.findings.rules;

import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowAdherenceRuleTest {

    private final LowAdherenceRule rule =
            new LowAdherenceRule(TestFindingsConfig.create());

    @Test
    void firesAndPluralisesCorrectly() {
        // 2 missed → plural "sessions"
        WeekData weekMulti = WeekDataFixture.baseline()
                .sessionsGoal(4).sessionsLogged(2).allMusclesTrained()
                .build();

        List<Finding> out = rule.evaluate(weekMulti);
        assertEquals(1, out.size());
        Finding f = out.get(0);
        assertEquals("low_adherence", f.ruleId());
        assertEquals("AMBER", f.severity());
        assertEquals("Missed 2 sessions", f.title());

        // 1 missed → singular "session"
        WeekData weekOneMiss = WeekDataFixture.baseline()
                .sessionsGoal(4).sessionsLogged(3).allMusclesTrained()
                .build();
        assertEquals("Missed 1 session", rule.evaluate(weekOneMiss).get(0).title());
    }

    @Test
    void doesNotFireWhenGoalMet() {
        WeekData week = WeekDataFixture.baseline()
                .sessionsGoal(4).sessionsLogged(4).allMusclesTrained()
                .build();

        assertTrue(rule.evaluate(week).isEmpty());
    }
}
