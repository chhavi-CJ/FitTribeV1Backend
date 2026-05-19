package com.fittribe.api.matching;

import com.fittribe.api.entity.Archetype;
import com.fittribe.api.entity.MatchingProfile;
import com.fittribe.api.entity.PartnerGenderPref;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link HardRuleChecker}.
 *
 * <p>Pure-logic checker — no Spring context, no DB. Gender values use the
 * codebase's uppercase convention ("MALE"/"FEMALE"); the case-insensitive
 * test deliberately mixes case to prove that behaviour.
 */
class HardRuleCheckerTest {

    private static MatchingProfile profile(Archetype archetype) {
        MatchingProfile p = new MatchingProfile();
        p.setUserId(UUID.randomUUID());
        p.setArchetype(archetype);
        return p;
    }

    // ── Gender compatibility ─────────────────────────────────────────

    @Test
    void both_any_always_compatible() {
        assertTrue(HardRuleChecker.genderCompatible(
                "MALE", PartnerGenderPref.ANY, "FEMALE", PartnerGenderPref.ANY));
        assertTrue(HardRuleChecker.genderCompatible(
                null, PartnerGenderPref.ANY, "MALE", PartnerGenderPref.ANY));
    }

    @Test
    void either_side_same_requires_match() {
        assertTrue(HardRuleChecker.genderCompatible(
                "MALE", PartnerGenderPref.SAME, "MALE", PartnerGenderPref.ANY));
        assertFalse(HardRuleChecker.genderCompatible(
                "MALE", PartnerGenderPref.SAME, "FEMALE", PartnerGenderPref.ANY));
        assertFalse(HardRuleChecker.genderCompatible(
                "MALE", PartnerGenderPref.ANY, "FEMALE", PartnerGenderPref.SAME));
    }

    @Test
    void both_same_must_match() {
        assertTrue(HardRuleChecker.genderCompatible(
                "MALE", PartnerGenderPref.SAME, "MALE", PartnerGenderPref.SAME));
        assertFalse(HardRuleChecker.genderCompatible(
                "MALE", PartnerGenderPref.SAME, "FEMALE", PartnerGenderPref.SAME));
    }

    @Test
    void case_insensitive_match() {
        assertTrue(HardRuleChecker.genderCompatible(
                "MALE", PartnerGenderPref.SAME, "male", PartnerGenderPref.SAME));
    }

    @Test
    void null_gender_with_same_pref_fails_safe() {
        assertFalse(HardRuleChecker.genderCompatible(
                null, PartnerGenderPref.SAME, "MALE", PartnerGenderPref.ANY));
    }

    @Test
    void rejects_null_pref() {
        assertThrows(IllegalArgumentException.class, () ->
                HardRuleChecker.genderCompatible(
                        "MALE", null, "MALE", PartnerGenderPref.ANY));
    }

    // ── Group has non-fragile ────────────────────────────────────────

    @Test
    void all_fragile_group_fails() {
        assertFalse(HardRuleChecker.groupHasNonFragileMember(List.of(
                profile(Archetype.STRIVER),
                profile(Archetype.RETURNER),
                profile(Archetype.SOCIAL_BUTTERFLY))));
    }

    @Test
    void one_anchor_passes() {
        assertTrue(HardRuleChecker.groupHasNonFragileMember(List.of(
                profile(Archetype.STRIVER),
                profile(Archetype.RETURNER),
                profile(Archetype.ANCHOR))));
    }

    @Test
    void one_grinder_passes() {
        assertTrue(HardRuleChecker.groupHasNonFragileMember(List.of(
                profile(Archetype.STRIVER),
                profile(Archetype.RETURNER),
                profile(Archetype.GRINDER))));
    }

    @Test
    void one_seeker_passes() {
        // SEEKER counts as non-fragile — can stabilize despite being pre-identity.
        assertTrue(HardRuleChecker.groupHasNonFragileMember(List.of(
                profile(Archetype.STRIVER),
                profile(Archetype.RETURNER),
                profile(Archetype.SEEKER))));
    }

    @Test
    void rejects_empty_group() {
        assertThrows(IllegalArgumentException.class,
                () -> HardRuleChecker.groupHasNonFragileMember(List.of()));
    }

    // ── Returner stabilizer ──────────────────────────────────────────

    @Test
    void group_without_returner_passes_returner_rule() {
        // Rule only fires when a RETURNER is present.
        assertTrue(HardRuleChecker.returnerHasStabilizer(List.of(
                profile(Archetype.STRIVER),
                profile(Archetype.SOCIAL_BUTTERFLY),
                profile(Archetype.SEEKER))));
    }

    @Test
    void returner_with_anchor_passes() {
        assertTrue(HardRuleChecker.returnerHasStabilizer(List.of(
                profile(Archetype.RETURNER),
                profile(Archetype.ANCHOR),
                profile(Archetype.STRIVER))));
    }

    @Test
    void returner_with_grinder_passes() {
        assertTrue(HardRuleChecker.returnerHasStabilizer(List.of(
                profile(Archetype.RETURNER),
                profile(Archetype.GRINDER),
                profile(Archetype.SOCIAL_BUTTERFLY))));
    }

    @Test
    void returner_without_stabilizer_fails() {
        assertFalse(HardRuleChecker.returnerHasStabilizer(List.of(
                profile(Archetype.RETURNER),
                profile(Archetype.STRIVER),
                profile(Archetype.SOCIAL_BUTTERFLY))));
        // SEEKER is non-fragile for rule 1 but NOT a stabilizer for this rule.
        assertFalse(HardRuleChecker.returnerHasStabilizer(List.of(
                profile(Archetype.RETURNER),
                profile(Archetype.SEEKER),
                profile(Archetype.SEEKER))));
    }

    @Test
    void two_returners_with_anchor_passes() {
        assertTrue(HardRuleChecker.returnerHasStabilizer(List.of(
                profile(Archetype.RETURNER),
                profile(Archetype.RETURNER),
                profile(Archetype.ANCHOR))));
    }

    // ── All group rules together ─────────────────────────────────────

    @Test
    void all_fragile_with_returner_fails_both() {
        assertFalse(HardRuleChecker.allGroupRulesPass(List.of(
                profile(Archetype.RETURNER),
                profile(Archetype.STRIVER),
                profile(Archetype.SOCIAL_BUTTERFLY))));
    }

    @Test
    void healthy_mixed_group_passes_all() {
        assertTrue(HardRuleChecker.allGroupRulesPass(List.of(
                profile(Archetype.ANCHOR),
                profile(Archetype.RETURNER),
                profile(Archetype.STRIVER),
                profile(Archetype.SEEKER))));
    }
}
