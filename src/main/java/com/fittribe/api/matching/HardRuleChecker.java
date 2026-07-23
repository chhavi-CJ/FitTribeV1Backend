package com.fittribe.api.matching;

import com.fittribe.api.entity.Archetype;
import com.fittribe.api.entity.MatchingProfile;
import com.fittribe.api.entity.PartnerGenderPref;

import java.util.List;
import java.util.Set;

/**
 * Pure-logic checker for hard rules in Conscious Matching (PRD §5–§6).
 *
 * Two types of rule:
 * <ul>
 *   <li><b>Pairwise rules</b> applied when scoring candidates against a
 *       seed — currently just gender preference compatibility.</li>
 *   <li><b>Group-level rules</b> applied to a tentatively-formed group —
 *       the all-fragile prohibition and the RETURNER stabilizer rule.</li>
 * </ul>
 *
 * <p>A pairwise rule failure means a single candidate is excluded.
 * A group-level rule failure means the {@link GroupBuilder} must
 * backtrack (swap, downsize, or defer).
 */
public final class HardRuleChecker {

    private HardRuleChecker() {}

    /** Stabilizers — types that anchor and steady a group. */
    private static final Set<Archetype> STABILIZERS = Set.of(
            Archetype.ANCHOR, Archetype.GRINDER);

    /** Fragile types — risky in self-paired groups (per PRD §6). */
    private static final Set<Archetype> FRAGILE = Set.of(
            Archetype.STRIVER, Archetype.RETURNER, Archetype.SOCIAL_BUTTERFLY);

    // ── Pairwise rules ───────────────────────────────────────────────

    /**
     * Pairwise gender preference compatibility. Symmetric — if either
     * party requires SAME, both must be the same gender. ANY by either
     * party widens to all.
     *
     * @param genderA gender of profile A (case-insensitive equality test;
     *        nulls compared as not-equal).
     * @param prefA   profile A's partner gender preference.
     * @param genderB gender of profile B.
     * @param prefB   profile B's partner gender preference.
     * @return true if the pair is gender-compatible.
     */
    public static boolean genderCompatible(
            String genderA, PartnerGenderPref prefA,
            String genderB, PartnerGenderPref prefB) {
        if (prefA == null || prefB == null) {
            throw new IllegalArgumentException("partner gender pref is null");
        }
        boolean anyoneRequiresSame =
                prefA == PartnerGenderPref.SAME || prefB == PartnerGenderPref.SAME;
        if (!anyoneRequiresSame) return true;   // both ANY — any pair is fine

        // Either side requires SAME — both genders must be present and equal.
        if (genderA == null || genderB == null) return false;
        return genderA.equalsIgnoreCase(genderB);
    }

    // ── Group-level rules ────────────────────────────────────────────

    /**
     * Group-level: a group must NOT be composed entirely of fragile
     * types. At least one ANCHOR, GRINDER, or SEEKER must be present.
     * (PRD §6: "Two fragile types alone reinforce each other's weakness.")
     */
    public static boolean groupHasNonFragileMember(List<MatchingProfile> group) {
        if (group == null || group.isEmpty()) {
            throw new IllegalArgumentException("group is null or empty");
        }
        for (MatchingProfile p : group) {
            if (!FRAGILE.contains(p.getArchetype())) return true;
        }
        return false;
    }

    /**
     * Group-level: if the group contains any RETURNER, it must also
     * contain at least one ANCHOR or GRINDER. (Locked design decision —
     * RETURNERs need a stabilizer to keep the group from drifting.)
     */
    public static boolean returnerHasStabilizer(List<MatchingProfile> group) {
        if (group == null || group.isEmpty()) {
            throw new IllegalArgumentException("group is null or empty");
        }
        boolean hasReturner = false;
        boolean hasStabilizer = false;
        for (MatchingProfile p : group) {
            if (p.getArchetype() == Archetype.RETURNER) hasReturner = true;
            if (STABILIZERS.contains(p.getArchetype()))  hasStabilizer = true;
        }
        // Rule only kicks in if a RETURNER is present.
        return !hasReturner || hasStabilizer;
    }

    /**
     * Convenience: check ALL group-level hard rules at once. Returns
     * true if every group-level rule passes; false if any fail.
     */
    public static boolean allGroupRulesPass(List<MatchingProfile> group) {
        return groupHasNonFragileMember(group) && returnerHasStabilizer(group);
    }
}
