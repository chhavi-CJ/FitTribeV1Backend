package com.fittribe.api.bonus;

import com.fittribe.api.entity.User;
import com.fittribe.api.service.RecoveryGateService.RecoveryState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BonusSessionPromptBuilderTest {

    private BonusSessionPromptBuilder builder;
    private User user;

    @BeforeEach
    void setUp() {
        builder = new BonusSessionPromptBuilder();
        user = new User();
        user.setDisplayName("Chhavi");
        user.setGender("female");
        user.setWeightKg(new BigDecimal("55"));
        user.setHeightCm(new BigDecimal("162"));
        user.setFitnessLevel("INTERMEDIATE");
        user.setGoal("BUILD_MUSCLE");
        user.setWeeklyGoal(4);
        user.setStreak(12);
        user.setHealthConditions(new String[0]);
        user.setAiContext(null);
    }

    @Test
    void build_insertsAllUserProfileFields() {
        String prompt = builder.build(user, Archetype.PUSH, "Chest fresh",
                Map.of("CHEST", RecoveryState.FRESH), 0);

        assertTrue(prompt.contains("Chhavi"));
        assertTrue(prompt.contains("female"));
        assertTrue(prompt.contains("55"));
        assertTrue(prompt.contains("162"));
        assertTrue(prompt.contains("INTERMEDIATE"));
        assertTrue(prompt.contains("BUILD_MUSCLE"));
        assertTrue(prompt.contains("weekly goal of 4"));
        assertTrue(prompt.contains("streak of 12"));
    }

    @Test
    void build_includesArchetypeAndRationale() {
        String prompt = builder.build(user, Archetype.ACCESSORY_CORE,
                "Pregnancy condition forces safe archetype",
                Map.of(), 0);
        assertTrue(prompt.contains("Archetype: ACCESSORY_CORE"));
        assertTrue(prompt.contains("Pregnancy condition"));
    }

    @Test
    void build_formatsRecoveryBlockReadably() {
        Map<String, RecoveryState> rec = new HashMap<>();
        rec.put("CHEST", RecoveryState.COOKED);
        rec.put("BACK", RecoveryState.READY);

        String prompt = builder.build(user, Archetype.LEGS, "Legs fresh", rec, 0);
        assertTrue(prompt.contains("BACK: READY"));
        assertTrue(prompt.contains("CHEST: COOKED"));
    }

    @Test
    void build_emptyRecoveryMap_usesFreshDefaultMessage() {
        String prompt = builder.build(user, Archetype.PUSH, "All fresh", Map.of(), 0);
        assertTrue(prompt.contains("All muscle groups fresh"));
    }

    @Test
    void build_formatsHealthConditionsFromCanonicalStrings() {
        user.setHealthConditions(new String[]{"PREGNANCY", "SHOULDER_INJURY"});
        String prompt = builder.build(user, Archetype.ACCESSORY_CORE,
                "Pregnancy condition", Map.of(), 0);
        assertTrue(prompt.contains("PREGNANCY"));
        assertTrue(prompt.contains("SHOULDER_INJURY"));
    }

    @Test
    void build_emptyHealthConditions_saysNoneReported() {
        String prompt = builder.build(user, Archetype.PUSH, "Fresh", Map.of(), 0);
        assertTrue(prompt.contains("None reported"));
    }

    @Test
    void build_dropsUnknownCanonicalValues() {
        user.setHealthConditions(new String[]{"GIBBERISH", "PREGNANCY"});
        String prompt = builder.build(user, Archetype.ACCESSORY_CORE,
                "Pregnancy", Map.of(), 0);
        assertTrue(prompt.contains("PREGNANCY"));
        assertFalse(prompt.contains("GIBBERISH"));
    }

    @Test
    void build_includesAiContextWhenPresent() {
        user.setAiContext("Training for a beach trip next month");
        String prompt = builder.build(user, Archetype.PUSH, "Fresh", Map.of(), 0);
        assertTrue(prompt.contains("PERSONAL CONTEXT: Training for a beach trip next month"));
    }

    @Test
    void build_omitsAiContextWhenNullOrBlank() {
        user.setAiContext(null);
        String prompt = builder.build(user, Archetype.PUSH, "Fresh", Map.of(), 0);
        assertFalse(prompt.contains("PERSONAL CONTEXT"));

        user.setAiContext("   ");
        prompt = builder.build(user, Archetype.PUSH, "Fresh", Map.of(), 0);
        assertFalse(prompt.contains("PERSONAL CONTEXT"));
    }

    @Test
    void build_nullFields_fallBackToSafeDefaults() {
        User empty = new User();
        empty.setHealthConditions(new String[0]);
        String prompt = builder.build(empty, Archetype.WEAK_POINT_FOCUS,
                "Soft capped", Map.of(), 2);
        assertTrue(prompt.contains("Athlete"));
        assertTrue(prompt.contains("Not specified"));
        assertTrue(prompt.contains("INTERMEDIATE"));
        assertTrue(prompt.contains("BUILD_MUSCLE"));
    }

    @Test
    void build_includesBonusesThisWeek() {
        String prompt = builder.build(user, Archetype.ACCESSORY_CORE,
                "Soft capped", Map.of(), 2);
        assertTrue(prompt.contains("Bonuses already this week: 2"));
    }

    @Test
    void build_includesAllStaticPromptSections() {
        String prompt = builder.build(user, Archetype.PUSH, "Fresh", Map.of(), 0);
        // Spot-check that key prompt sections survived the replace chain
        assertTrue(prompt.contains("ARCHETYPE RULES"));
        assertTrue(prompt.contains("SAFETY HARD RULES"));
        assertTrue(prompt.contains("WEIGHT SUGGESTION RULES"));
        assertTrue(prompt.contains("EXERCISE CATALOG"));
        assertTrue(prompt.contains("CELEBRATORY TONE RULES"));
    }
}
