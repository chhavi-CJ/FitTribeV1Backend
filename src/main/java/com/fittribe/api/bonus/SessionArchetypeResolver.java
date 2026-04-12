package com.fittribe.api.bonus;

import com.fittribe.api.healthcondition.HealthCondition;
import com.fittribe.api.service.RecoveryGateService.RecoveryState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Pure function service. Given a user's recovery state, profile signals,
 * and how many bonuses they've done this week, picks the archetype of
 * their bonus session.
 *
 * Safety rules are checked first and route users into ACCESSORY_CORE
 * which is filtered in Step 5 (BonusSessionService) to appropriate
 * exercises. The resolver never returns an "empty" or "skip" result —
 * every user who requests a bonus gets one; it's just gentler when
 * their data says so.
 *
 * Rule priority (first match wins):
 *   1. PREGNANCY                            → ACCESSORY_CORE
 *   2. HEART_CONDITION                      → ACCESSORY_CORE
 *   3. POSTNATAL                            → ACCESSORY_CORE
 *   4. aiContext has red-flag keyword       → ACCESSORY_CORE
 *   5. Fewer than 3 ready muscle groups     → ACCESSORY_CORE
 *   6. Acute injury + relevant area ready   → ACCESSORY_CORE
 *   7. Soft cap (bonusesThisWeek >= 2)      → ACCESSORY_CORE or WEAK_POINT_FOCUS
 *   8. BEGINNER + weeklyGoal <= 2           → cap at WEAK_POINT_FOCUS
 *   9. Standard — match recovery pattern to PUSH/PULL/LEGS/WEAK_POINT_FOCUS
 *
 * This service is stateless and thread-safe.
 */
@Service
public class SessionArchetypeResolver {

    private static final Logger log = LoggerFactory.getLogger(SessionArchetypeResolver.class);

    private static final Set<String> RED_FLAG_KEYWORDS = Set.of(
            "sick", "ill", "unwell", "injured", "injury",
            "exhausted", "no sleep", "didn't sleep",
            "period", "cramps", "migraine",
            "fever", "flu", "covid"
    );

    /**
     * Input bundle for resolution. Using a record keeps the signature
     * additive — new fields can be added without breaking existing tests.
     */
    public record ResolverInput(
            int weeklyGoal,
            String fitnessLevel,                    // "BEGINNER" | "INTERMEDIATE" | "ADVANCED"
            Set<HealthCondition> healthConditions,  // canonical enum values, never null
            String aiContext,                        // user's free text, may be null
            Map<String, RecoveryState> recoveryMap, // canonical muscle → state, never null
            int bonusesThisWeek
    ) {}

    public Archetype resolve(ResolverInput input) {
        Objects.requireNonNull(input, "ResolverInput must not be null");

        // Rule 1-3: Unconditional safety overrides for sensitive conditions
        if (input.healthConditions().contains(HealthCondition.PREGNANCY)) {
            log.debug("Archetype ACCESSORY_CORE: PREGNANCY condition");
            return Archetype.ACCESSORY_CORE;
        }
        if (input.healthConditions().contains(HealthCondition.HEART_CONDITION)) {
            log.debug("Archetype ACCESSORY_CORE: HEART_CONDITION");
            return Archetype.ACCESSORY_CORE;
        }
        if (input.healthConditions().contains(HealthCondition.POSTNATAL)) {
            log.debug("Archetype ACCESSORY_CORE: POSTNATAL");
            return Archetype.ACCESSORY_CORE;
        }

        // Rule 4: User-reported transient issues via aiContext
        if (hasRedFlagKeyword(input.aiContext())) {
            log.debug("Archetype ACCESSORY_CORE: aiContext red-flag keyword");
            return Archetype.ACCESSORY_CORE;
        }

        // Rule 5: Recovery gate — need at least 3 non-COOKED groups to train meaningfully
        long readyOrFreshCount = input.recoveryMap().values().stream()
                .filter(s -> s != RecoveryState.COOKED)
                .count();
        // FRESH groups not in the map are still FRESH — count untrained groups too
        int untrackedFreshCount = 11 - input.recoveryMap().size();
        long totalReady = readyOrFreshCount + untrackedFreshCount;

        if (totalReady < 3) {
            log.debug("Archetype ACCESSORY_CORE: only {} muscle groups ready", totalReady);
            return Archetype.ACCESSORY_CORE;
        }

        // Rule 6: Acute injury protection
        if (hasInjuryConflict(input)) {
            log.debug("Archetype ACCESSORY_CORE: acute injury protects affected region");
            return Archetype.ACCESSORY_CORE;
        }

        // Rule 7: Soft cap — past 2 bonuses, narrow options
        if (input.bonusesThisWeek() >= 2) {
            log.debug("Archetype narrowed by soft cap ({} bonuses)", input.bonusesThisWeek());
            return pickGentle(input);
        }

        // Rule 8: Beginner with low weekly commitment shouldn't do compound bonus sessions
        if ("BEGINNER".equalsIgnoreCase(input.fitnessLevel()) && input.weeklyGoal() <= 2) {
            log.debug("Archetype WEAK_POINT_FOCUS: beginner with weeklyGoal <= 2");
            return Archetype.WEAK_POINT_FOCUS;
        }

        // Rule 9: Standard — pick the compound archetype with the freshest muscles
        return pickStandard(input);
    }

    private boolean hasRedFlagKeyword(String aiContext) {
        if (aiContext == null || aiContext.isBlank()) return false;
        String lower = aiContext.toLowerCase(Locale.ROOT);
        return RED_FLAG_KEYWORDS.stream().anyMatch(lower::contains);
    }

    /**
     * Injury is considered conflicting when the muscle group commonly
     * recruited in compound training for that area is recovered — so
     * the user might push through pain if we hand them a PUSH/PULL/LEGS.
     */
    private boolean hasInjuryConflict(ResolverInput input) {
        Set<HealthCondition> conditions = input.healthConditions();
        Map<String, RecoveryState> rec = input.recoveryMap();

        if (conditions.contains(HealthCondition.BACK_PAIN)) {
            // Back pain: protect from pulls and heavy legs
            RecoveryState backState = rec.getOrDefault("BACK", RecoveryState.FRESH);
            RecoveryState legsState = rec.getOrDefault("LEGS", RecoveryState.FRESH);
            if (backState != RecoveryState.COOKED || legsState != RecoveryState.COOKED) return true;
        }
        if (conditions.contains(HealthCondition.SHOULDER_INJURY)) {
            RecoveryState shoulderState = rec.getOrDefault("SHOULDERS", RecoveryState.FRESH);
            if (shoulderState != RecoveryState.COOKED) return true;
        }
        if (conditions.contains(HealthCondition.JOINT_ISSUES)) {
            // Joint issues: protect from legs
            RecoveryState legsState = rec.getOrDefault("LEGS", RecoveryState.FRESH);
            if (legsState != RecoveryState.COOKED) return true;
        }
        return false;
    }

    /** Gentle pick for soft-capped users. */
    private Archetype pickGentle(ResolverInput input) {
        // If any accessory-eligible area has been untouched this week, bias toward it
        boolean armsAvailable =
                input.recoveryMap().getOrDefault("BICEPS", RecoveryState.FRESH) != RecoveryState.COOKED
             && input.recoveryMap().getOrDefault("TRICEPS", RecoveryState.FRESH) != RecoveryState.COOKED;
        return armsAvailable ? Archetype.WEAK_POINT_FOCUS : Archetype.ACCESSORY_CORE;
    }

    /**
     * Standard pick: match the freshest compound pattern.
     *
     * PUSH  needs CHEST + SHOULDERS + TRICEPS all non-COOKED
     * PULL  needs BACK + BICEPS       all non-COOKED
     * LEGS  needs LEGS + HAMSTRINGS + GLUTES all non-COOKED
     * Otherwise fall back to WEAK_POINT_FOCUS (isolation session for whatever's fresh)
     */
    private Archetype pickStandard(ResolverInput input) {
        Map<String, RecoveryState> rec = input.recoveryMap();

        boolean pushReady = notCooked(rec, "CHEST") && notCooked(rec, "SHOULDERS") && notCooked(rec, "TRICEPS");
        boolean pullReady = notCooked(rec, "BACK")  && notCooked(rec, "BICEPS");
        boolean legsReady = notCooked(rec, "LEGS")  && notCooked(rec, "HAMSTRINGS") && notCooked(rec, "GLUTES");

        // Prefer whichever has the most FRESH (vs merely READY) muscles
        int pushScore = freshnessScore(rec, "CHEST", "SHOULDERS", "TRICEPS");
        int pullScore = freshnessScore(rec, "BACK", "BICEPS");
        int legsScore = freshnessScore(rec, "LEGS", "HAMSTRINGS", "GLUTES");

        if (pushReady && pushScore >= pullScore && pushScore >= legsScore) return Archetype.PUSH;
        if (pullReady && pullScore >= legsScore) return Archetype.PULL;
        if (legsReady) return Archetype.LEGS;
        if (pushReady) return Archetype.PUSH;
        if (pullReady) return Archetype.PULL;

        return Archetype.WEAK_POINT_FOCUS;
    }

    private boolean notCooked(Map<String, RecoveryState> rec, String muscle) {
        RecoveryState state = rec.get(muscle);
        return state == null || state != RecoveryState.COOKED;
    }

    private int freshnessScore(Map<String, RecoveryState> rec, String... muscles) {
        int score = 0;
        for (String m : muscles) {
            RecoveryState s = rec.getOrDefault(m, RecoveryState.FRESH);
            if (s == RecoveryState.FRESH) score += 2;
            else if (s == RecoveryState.READY) score += 1;
        }
        return score;
    }
}
