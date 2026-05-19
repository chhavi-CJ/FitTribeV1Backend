package com.fittribe.api.matching;

import com.fittribe.api.entity.Archetype;
import com.fittribe.api.entity.MatchingProfile;
import com.fittribe.api.entity.PartnerGenderPref;
import com.fittribe.api.matching.GroupBuilder.BuildResult;
import com.fittribe.api.matching.GroupBuilder.FormedGroup;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GroupBuilder#buildGroups} and its helpers.
 *
 * <p>Pure-logic orchestrator — no Spring context, no DB. Deterministic
 * sequential UUIDs make seed tie-breaking reproducible; every expected
 * outcome below was hand-walked against {@link CompatibilityScorer} and
 * the scarcity table before being hardcoded.
 */
class GroupBuilderTest {

    private static int nextUuidSeed = 1;

    static MatchCandidate cand(Archetype archetype, String gender, PartnerGenderPref pref) {
        return cand(archetype, gender, pref, 0, 0, 0, 0);
    }

    static MatchCandidate cand(Archetype archetype, String gender, PartnerGenderPref pref,
                               int q1, int q2, int q3, int q4) {
        MatchingProfile p = new MatchingProfile();
        // Deterministic UUIDs so seed tie-breaking is reproducible across runs.
        p.setUserId(new UUID(0, nextUuidSeed++));
        p.setArchetype(archetype);
        p.setPartnerGenderPref(pref);
        p.setScoreQ1(q1);
        p.setScoreQ2(q2);
        p.setScoreQ3(q3);
        p.setScoreQ4(q4);
        return new MatchCandidate(p, gender);
    }

    static void resetUuids() {
        nextUuidSeed = 1;
    }

    private static List<Archetype> archetypesOf(FormedGroup g) {
        List<Archetype> out = new ArrayList<>();
        for (MatchCandidate c : g.members()) out.add(c.profile().getArchetype());
        return out;
    }

    // ── Pool too small ───────────────────────────────────────────────

    @Test
    void empty_pool_returns_empty_result() {
        resetUuids();
        BuildResult r = GroupBuilder.buildGroups(List.of());
        assertTrue(r.formedGroups().isEmpty());
        assertTrue(r.remainder().isEmpty());
    }

    @Test
    void pool_of_two_returns_no_groups() {
        resetUuids();
        List<MatchCandidate> pool = List.of(
                cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY));
        BuildResult r = GroupBuilder.buildGroups(pool);
        assertTrue(r.formedGroups().isEmpty());
        assertEquals(2, r.remainder().size());
    }

    // ── Happy path ───────────────────────────────────────────────────

    @Test
    void forms_one_group_of_four_when_pool_has_exactly_four_compatible() {
        resetUuids();
        List<MatchCandidate> pool = List.of(
                cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.RETURNER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.GRINDER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.STRIVER, "MALE", PartnerGenderPref.ANY));
        BuildResult r = GroupBuilder.buildGroups(pool);
        assertEquals(1, r.formedGroups().size());
        assertEquals(4, r.formedGroups().get(0).members().size());
        assertTrue(r.remainder().isEmpty());
    }

    @Test
    void forms_one_group_of_three_when_pool_has_exactly_three() {
        resetUuids();
        List<MatchCandidate> pool = List.of(
                cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.RETURNER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.SEEKER, "MALE", PartnerGenderPref.ANY));
        BuildResult r = GroupBuilder.buildGroups(pool);
        assertEquals(1, r.formedGroups().size());
        assertEquals(3, r.formedGroups().get(0).members().size());
        assertTrue(r.remainder().isEmpty());
    }

    // ── Hard rule enforcement ────────────────────────────────────────

    @Test
    void excludes_gender_incompatible_candidate_from_pairing() {
        resetUuids();
        // Seed (uuid1, spread 0) = FEMALE/SAME. MALE/SAME is gender-excluded.
        MatchCandidate seed = cand(Archetype.ANCHOR, "FEMALE", PartnerGenderPref.SAME);
        MatchCandidate male = cand(Archetype.STRIVER, "MALE", PartnerGenderPref.SAME);
        MatchCandidate femaleA = cand(Archetype.RETURNER, "FEMALE", PartnerGenderPref.ANY);
        MatchCandidate femaleB = cand(Archetype.GRINDER, "FEMALE", PartnerGenderPref.ANY);
        BuildResult r = GroupBuilder.buildGroups(List.of(seed, male, femaleA, femaleB));

        assertEquals(1, r.formedGroups().size());
        assertEquals(3, r.formedGroups().get(0).members().size());
        assertEquals(1, r.remainder().size());
        assertEquals("MALE", r.remainder().get(0).gender());
        assertEquals(Archetype.STRIVER, r.remainder().get(0).profile().getArchetype());
    }

    @Test
    void defers_seed_when_no_valid_group_possible() {
        resetUuids();
        // All three fragile, no stabilizer -> all-fragile rule fails.
        List<MatchCandidate> pool = List.of(
                cand(Archetype.STRIVER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.RETURNER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.SOCIAL_BUTTERFLY, "MALE", PartnerGenderPref.ANY));
        BuildResult r = GroupBuilder.buildGroups(pool);
        assertTrue(r.formedGroups().isEmpty());
        assertEquals(3, r.remainder().size());
    }

    @Test
    void returner_only_pool_fails_stabilizer_rule() {
        resetUuids();
        List<MatchCandidate> pool = List.of(
                cand(Archetype.RETURNER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.RETURNER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.RETURNER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.RETURNER, "MALE", PartnerGenderPref.ANY));
        BuildResult r = GroupBuilder.buildGroups(pool);
        assertTrue(r.formedGroups().isEmpty());
        assertEquals(4, r.remainder().size());
    }

    // ── Scarcity ─────────────────────────────────────────────────────

    @Test
    void rare_archetype_gets_scarcity_bonus() {
        resetUuids();
        // 9 ANCHOR (share 0.9 -> -1) + 1 RETURNER (share 0.1 -> +2).
        // Seed = ANCHOR (uuid1). RETURNER adjusted = 5+1+2 = 8 vs
        // ANCHOR-ANCHOR adjusted = 3+1-1 = 3 -> RETURNER is top pick.
        List<MatchCandidate> pool = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            pool.add(cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY));
        }
        pool.add(cand(Archetype.RETURNER, "MALE", PartnerGenderPref.ANY));

        BuildResult r = GroupBuilder.buildGroups(pool);
        assertFalse(r.formedGroups().isEmpty());
        FormedGroup first = r.formedGroups().get(0);
        assertTrue(archetypesOf(first).contains(Archetype.RETURNER),
                "rare RETURNER should be pulled into the first formed group");
    }

    // ── Determinism ──────────────────────────────────────────────────

    @Test
    void same_input_produces_same_result() {
        resetUuids();
        List<MatchCandidate> pool1 = List.of(
                cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.RETURNER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.GRINDER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.STRIVER, "MALE", PartnerGenderPref.ANY));
        BuildResult r1 = GroupBuilder.buildGroups(pool1);

        resetUuids();
        List<MatchCandidate> pool2 = List.of(
                cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.RETURNER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.GRINDER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.STRIVER, "MALE", PartnerGenderPref.ANY));
        BuildResult r2 = GroupBuilder.buildGroups(pool2);

        assertEquals(r1.formedGroups().size(), r2.formedGroups().size());
        for (int i = 0; i < r1.formedGroups().size(); i++) {
            FormedGroup g1 = r1.formedGroups().get(i);
            FormedGroup g2 = r2.formedGroups().get(i);
            assertEquals(archetypesOf(g1), archetypesOf(g2));
            assertEquals(g1.qualityScore(), g2.qualityScore());
            assertEquals(g1.dominantArchetype(), g2.dominantArchetype());
        }
        assertEquals(r1.remainder().size(), r2.remainder().size());
    }

    // ── Downsizing & deferral ────────────────────────────────────────

    @Test
    void forms_size_4_when_possible_else_size_3() {
        resetUuids();
        List<MatchCandidate> pool = List.of(
                cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.RETURNER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.GRINDER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.STRIVER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.SEEKER, "MALE", PartnerGenderPref.ANY));
        BuildResult r = GroupBuilder.buildGroups(pool);
        assertEquals(1, r.formedGroups().size());
        assertEquals(4, r.formedGroups().get(0).members().size());
        assertEquals(1, r.remainder().size());
    }

    @Test
    void forms_multiple_groups_when_pool_is_large() {
        resetUuids();
        List<MatchCandidate> pool = List.of(
                cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.GRINDER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.GRINDER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.RETURNER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.STRIVER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.SEEKER, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.SOCIAL_BUTTERFLY, "MALE", PartnerGenderPref.ANY));
        BuildResult r = GroupBuilder.buildGroups(pool);
        assertEquals(2, r.formedGroups().size());
        assertEquals(4, r.formedGroups().get(0).members().size());
        assertEquals(4, r.formedGroups().get(1).members().size());
        assertTrue(r.remainder().isEmpty());
    }

    // ── Input validation ─────────────────────────────────────────────

    @Test
    void rejects_null_pool() {
        resetUuids();
        assertThrows(IllegalArgumentException.class, () -> GroupBuilder.buildGroups(null));
    }

    // ── Seed selection ───────────────────────────────────────────────

    @Test
    void lowest_spread_profile_is_seeded() {
        resetUuids();
        // A: spread 0 (all Qs = 2). B: spread 3 (Qs = 0,0,3,0).
        MatchCandidate a = cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY, 2, 2, 2, 2);
        MatchCandidate b = cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY, 0, 0, 3, 0);
        MatchCandidate ret = cand(Archetype.RETURNER, "MALE", PartnerGenderPref.ANY);
        MatchCandidate gri = cand(Archetype.GRINDER, "MALE", PartnerGenderPref.ANY);
        BuildResult r = GroupBuilder.buildGroups(List.of(a, b, ret, gri));

        assertEquals(1, r.formedGroups().size());
        // members[0] is the seed — must be A (lowest spread).
        assertSame(a, r.formedGroups().get(0).members().get(0));
    }

    // ── Quality floor (direct unit test of isValidGroup) ─────────────

    @Test
    void quality_floor_is_enforced_at_group_level() {
        resetUuids();
        // All-stabilizer members -> group-level hard rules pass; only the
        // quality floor decides.
        List<MatchCandidate> members = List.of(
                cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY),
                cand(Archetype.GRINDER, "MALE", PartnerGenderPref.ANY));

        FormedGroup belowFloor = new FormedGroup(members, -1, Archetype.ANCHOR);
        FormedGroup atFloor = new FormedGroup(members, 0, Archetype.ANCHOR);

        assertFalse(GroupBuilder.isValidGroup(belowFloor));
        assertTrue(GroupBuilder.isValidGroup(atFloor));
    }

    // ── Swap backtrack ───────────────────────────────────────────────

    @Test
    void swaps_lowest_member_when_group_initially_fails_rules() {
        resetUuids();
        // Seed = SB (uuid1). 3 more SB (uuid2-4), then 6 ANCHOR (uuid5-10).
        // Pool size 10: SB share 0.4 -> 0 scarcity; ANCHOR share 0.6 -> -1.
        // vs SB seed: SB+SB = 2+1 = 3 (no scarcity) ; ANCHOR+SB = 3+1-1 = 3.
        // All nine score 3; UUID tie-break puts the 3 SB first, so the
        // greedy top-3 is all-SB -> seed+3 all fragile -> invalid.
        // The 4th-ranked candidate is an ANCHOR; one swap yields a valid
        // group containing a non-fragile member.
        List<MatchCandidate> pool = new ArrayList<>();
        pool.add(cand(Archetype.SOCIAL_BUTTERFLY, "MALE", PartnerGenderPref.ANY)); // seed
        for (int i = 0; i < 3; i++) {
            pool.add(cand(Archetype.SOCIAL_BUTTERFLY, "MALE", PartnerGenderPref.ANY));
        }
        for (int i = 0; i < 6; i++) {
            pool.add(cand(Archetype.ANCHOR, "MALE", PartnerGenderPref.ANY));
        }

        BuildResult r = GroupBuilder.buildGroups(pool);
        assertFalse(r.formedGroups().isEmpty());
        FormedGroup first = r.formedGroups().get(0);
        assertEquals(4, first.members().size());
        // Proof a swap occurred: the naive top-3 was all-SB (all fragile),
        // yet the formed group contains a non-fragile ANCHOR.
        assertTrue(archetypesOf(first).contains(Archetype.ANCHOR),
                "swap should have replaced a fragile member with the ANCHOR");
        assertNotNull(first.dominantArchetype());
    }
}
