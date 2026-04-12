package com.fittribe.api.bonus;

import com.fittribe.api.healthcondition.HealthCondition;
import com.fittribe.api.service.RecoveryGateService.RecoveryState;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.fittribe.api.bonus.SessionArchetypeResolver.ResolverInput;
import static org.junit.jupiter.api.Assertions.*;

class SessionArchetypeResolverTest {

    private final SessionArchetypeResolver resolver = new SessionArchetypeResolver();

    @Test
    void rule1_pregnancy_forcesAccessoryCore() {
        var input = build(Set.of(HealthCondition.PREGNANCY), allFresh(), 0, 3, "INTERMEDIATE", null);
        assertEquals(Archetype.ACCESSORY_CORE, resolver.resolve(input));
    }

    @Test
    void rule2_heartCondition_forcesAccessoryCore() {
        var input = build(Set.of(HealthCondition.HEART_CONDITION), allFresh(), 0, 5, "ADVANCED", null);
        assertEquals(Archetype.ACCESSORY_CORE, resolver.resolve(input));
    }

    @Test
    void rule3_postnatal_forcesAccessoryCore() {
        var input = build(Set.of(HealthCondition.POSTNATAL), allFresh(), 0, 4, "INTERMEDIATE", null);
        assertEquals(Archetype.ACCESSORY_CORE, resolver.resolve(input));
    }

    @Test
    void rule4_redFlagKeywordInAiContext_forcesAccessoryCore() {
        var input = build(Set.of(), allFresh(), 0, 4, "INTERMEDIATE", "having bad cramps today");
        assertEquals(Archetype.ACCESSORY_CORE, resolver.resolve(input));
    }

    @Test
    void rule4_redFlagKeywordIsCaseInsensitive() {
        var input = build(Set.of(), allFresh(), 0, 4, "INTERMEDIATE", "Feeling SICK");
        assertEquals(Archetype.ACCESSORY_CORE, resolver.resolve(input));
    }

    @Test
    void rule5_fewerThan3ReadyGroups_forcesAccessoryCore() {
        Map<String, RecoveryState> rec = new HashMap<>();
        for (String m : List.of("CHEST","BACK","SHOULDERS","BICEPS","TRICEPS",
                                "LEGS","HAMSTRINGS","GLUTES","CALVES")) {
            rec.put(m, RecoveryState.COOKED);
        }
        // 2 groups ready (CORE, FULL_BODY absent = implicit FRESH = 2 untracked); cooked count = 9
        var input = build(Set.of(), rec, 0, 5, "INTERMEDIATE", null);
        assertEquals(Archetype.ACCESSORY_CORE, resolver.resolve(input));
    }

    @Test
    void rule6_backPainAndLegsReady_forcesAccessoryCore() {
        Map<String, RecoveryState> rec = allFresh();
        var input = build(Set.of(HealthCondition.BACK_PAIN), rec, 0, 4, "INTERMEDIATE", null);
        assertEquals(Archetype.ACCESSORY_CORE, resolver.resolve(input));
    }

    @Test
    void rule6_shoulderInjuryAndShouldersReady_forcesAccessoryCore() {
        var input = build(Set.of(HealthCondition.SHOULDER_INJURY), allFresh(), 0, 4, "INTERMEDIATE", null);
        assertEquals(Archetype.ACCESSORY_CORE, resolver.resolve(input));
    }

    @Test
    void rule6_jointIssuesAndLegsReady_forcesAccessoryCore() {
        var input = build(Set.of(HealthCondition.JOINT_ISSUES), allFresh(), 0, 4, "INTERMEDIATE", null);
        assertEquals(Archetype.ACCESSORY_CORE, resolver.resolve(input));
    }

    @Test
    void rule7_softCapAt2Bonuses_narrows() {
        var input = build(Set.of(), allFresh(), 2, 5, "INTERMEDIATE", null);
        Archetype result = resolver.resolve(input);
        assertTrue(result == Archetype.ACCESSORY_CORE || result == Archetype.WEAK_POINT_FOCUS,
                "Expected narrowed archetype, got " + result);
    }

    @Test
    void rule8_beginnerLowWeeklyGoal_capsAtWeakPointFocus() {
        var input = build(Set.of(), allFresh(), 0, 2, "BEGINNER", null);
        assertEquals(Archetype.WEAK_POINT_FOCUS, resolver.resolve(input));
    }

    @Test
    void rule9_standardCase_allFresh_picksCompound() {
        var input = build(Set.of(), allFresh(), 0, 5, "INTERMEDIATE", null);
        Archetype result = resolver.resolve(input);
        assertTrue(
                result == Archetype.PUSH || result == Archetype.PULL || result == Archetype.LEGS,
                "Expected compound archetype, got " + result);
    }

    @Test
    void rule9_chestAndShouldersCooked_avoidsPush() {
        Map<String, RecoveryState> rec = allFresh();
        rec.put("CHEST", RecoveryState.COOKED);
        rec.put("SHOULDERS", RecoveryState.COOKED);
        var input = build(Set.of(), rec, 0, 5, "INTERMEDIATE", null);
        assertNotEquals(Archetype.PUSH, resolver.resolve(input));
    }

    @Test
    void ruleOrdering_pregnancyBeatsEverythingElse() {
        // Pregnancy user is also BEGINNER weeklyGoal=2 and has red-flag aiContext
        // All three would route to ACCESSORY_CORE or WEAK_POINT_FOCUS, but pregnancy fires first
        var input = build(
                Set.of(HealthCondition.PREGNANCY),
                allFresh(), 0, 2, "BEGINNER", "feeling sick");
        assertEquals(Archetype.ACCESSORY_CORE, resolver.resolve(input));
    }

    @Test
    void emptyHealthConditions_andFullyFreshRecovery_picksCompound() {
        var input = build(Set.of(), Map.of(), 0, 4, "INTERMEDIATE", null);
        Archetype result = resolver.resolve(input);
        assertTrue(
                result == Archetype.PUSH || result == Archetype.PULL || result == Archetype.LEGS,
                "Expected compound archetype for blank-slate user, got " + result);
    }

    // ── helpers ─────────────────────────────────────────────────────

    private ResolverInput build(Set<HealthCondition> conditions,
                                 Map<String, RecoveryState> rec,
                                 int bonuses,
                                 int weeklyGoal,
                                 String level,
                                 String aiCtx) {
        return new ResolverInput(weeklyGoal, level, conditions, aiCtx, rec, bonuses);
    }

    private Map<String, RecoveryState> allFresh() {
        // Empty map works because untracked muscles are treated as FRESH,
        // but make the intent explicit for readability in some tests.
        return new HashMap<>();
    }
}
