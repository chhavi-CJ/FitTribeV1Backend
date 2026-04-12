package com.fittribe.api.bonus;

import com.fittribe.api.config.AiPrompts;
import com.fittribe.api.entity.User;
import com.fittribe.api.healthcondition.HealthCondition;
import com.fittribe.api.service.RecoveryGateService.RecoveryState;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the user prompt string for bonus session AI generation.
 *
 * Assembles the AiPrompts.BONUS_EXERCISE_USER template with real values
 * from the user profile, resolved archetype, and recovery state. Keeps
 * prompt-building logic isolated and testable without any AI dependency.
 *
 * Safe defaults: null fields in User (displayName, weightKg, etc.) fall
 * back to neutral placeholders matching how PlanService.generateTodaysPlan
 * handles the same nullables.
 */
@Component
public class BonusSessionPromptBuilder {

    /**
     * Build the user prompt from all required inputs.
     *
     * @param user               the user (fields may be null — handled defensively)
     * @param archetype          chosen by SessionArchetypeResolver
     * @param archetypeRationale short human-readable reason the archetype was picked
     * @param recoveryMap        per-muscle recovery state from RecoveryGateService
     * @param bonusesThisWeek    count of bonuses already completed this week
     * @return the assembled prompt ready to send to OpenAI
     */
    public String build(User user,
                        Archetype archetype,
                        String archetypeRationale,
                        Map<String, RecoveryState> recoveryMap,
                        int bonusesThisWeek) {

        return AiPrompts.BONUS_EXERCISE_USER
                .replace("{weeklyGoal}", String.valueOf(
                        user.getWeeklyGoal() != null ? user.getWeeklyGoal() : 3))
                .replace("{name}", user.getDisplayName() != null
                        ? user.getDisplayName() : "Athlete")
                .replace("{gender}", user.getGender() != null
                        ? user.getGender() : "Not specified")
                .replace("{weightKg}", user.getWeightKg() != null
                        ? user.getWeightKg().toString() : "70")
                .replace("{heightCm}", user.getHeightCm() != null
                        ? user.getHeightCm().toString() : "Not specified")
                .replace("{fitnessLevel}", user.getFitnessLevel() != null
                        ? user.getFitnessLevel() : "INTERMEDIATE")
                .replace("{goal}", user.getGoal() != null
                        ? user.getGoal() : "BUILD_MUSCLE")
                .replace("{currentStreak}", String.valueOf(
                        user.getStreak() != null ? user.getStreak() : 0))
                .replace("{healthConditions}", formatHealthConditions(user.getHealthConditions()))
                .replace("{aiContext}", formatAiContext(user.getAiContext()))
                .replace("{archetype}", archetype.name())
                .replace("{bonusesThisWeek}", String.valueOf(bonusesThisWeek))
                .replace("{archetypeRationale}", archetypeRationale != null
                        ? archetypeRationale : "Standard selection based on recovery state")
                .replace("{recoveryBlock}", formatRecoveryBlock(recoveryMap));
    }

    private String formatHealthConditions(String[] conditions) {
        if (conditions == null || conditions.length == 0) return "None reported";
        return Arrays.stream(conditions)
                .map(HealthCondition::fromCanonical)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    private String formatAiContext(String aiContext) {
        if (aiContext == null || aiContext.isBlank()) return "";
        return "PERSONAL CONTEXT: " + aiContext;
    }

    /**
     * Format recovery map into a compact block for the prompt.
     * Only includes muscles present in the map (implicit FRESH for others).
     * Example output:
     *   CHEST: COOKED (trained recently)
     *   BACK: READY (ok to train, prefer if fresh options limited)
     *   SHOULDERS: FRESH
     */
    private String formatRecoveryBlock(Map<String, RecoveryState> recoveryMap) {
        if (recoveryMap == null || recoveryMap.isEmpty()) {
            return "All muscle groups fresh — no training in the last 14 days.";
        }
        return recoveryMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ": " + e.getValue().name())
                .collect(Collectors.joining("\n"));
    }
}
