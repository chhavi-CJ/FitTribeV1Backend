package com.fittribe.api.matching;

import com.fittribe.api.entity.Archetype;
import com.fittribe.api.entity.MatchingProfile;
import com.fittribe.api.entity.PartnerGenderPref;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrator for Conscious Matching group formation (PRD §5–§7).
 *
 * Takes a pool of queued {@link MatchCandidate}s and produces:
 *   - zero or more validly-formed groups (3 or 4 members each)
 *   - a remainder pool of candidates not placed (returned to QUEUED
 *     status by the DB-wiring service for retry next batch)
 *
 * <p>Composes {@link CompatibilityScorer} (pairwise affinity + intensity)
 * and {@link HardRuleChecker} (gender pref + group-level rules). Adds
 * five orchestration layers on top:
 *
 * <ol>
 *   <li><b>Seed selection</b> — pick the highest-clarity profile
 *       (lowest spread between max and min per-question scores),
 *       tie-broken by user ID for determinism.</li>
 *   <li><b>Scarcity-aware ranking</b> — candidates whose archetype is
 *       rare in the input pool receive a small score bonus (≤10% of
 *       pool: +2, ≤20%: +1, ≥50%: −1). Computed once per batch run,
 *       not after each group forms.</li>
 *   <li><b>Greedy assembly</b> — pick top-3 scored candidates to form
 *       a tentative size-4 group with seed.</li>
 *   <li><b>5-swap backtrack</b> — if group fails hard rules, replace
 *       the lowest-scored member with the next-best candidate. Up to
 *       5 swaps before giving up on size 4.</li>
 *   <li><b>Downsize to 3</b> — if size 4 cannot be formed (even with
 *       swaps), try size 3 (seed + top 2), with another 5-swap
 *       allowance. If that also fails, defer the seed.</li>
 * </ol>
 *
 * <p>Pure logic. No DB. No Spring. No I/O. All decisions are
 * deterministic given the same input pool.
 */
public final class GroupBuilder {

    /** Quality floor — a tentative group must score at least this. */
    static final int MIN_GROUP_QUALITY = 0;

    /** Maximum swap attempts per group before giving up on that size. */
    static final int MAX_SWAPS = 5;

    private GroupBuilder() {}

    /** A group successfully formed by the engine. */
    public record FormedGroup(
            List<MatchCandidate> members,
            int qualityScore,
            Archetype dominantArchetype
    ) {}

    /** Engine output: groups formed + leftover candidates. */
    public record BuildResult(
            List<FormedGroup> formedGroups,
            List<MatchCandidate> remainder
    ) {}

    /**
     * Build as many groups as possible from the input pool.
     *
     * @param pool input candidates. Must not be null; may be empty
     *             (returns empty groups, empty remainder).
     * @return groups formed and unmatched remainder.
     */
    public static BuildResult buildGroups(List<MatchCandidate> pool) {
        if (pool == null) throw new IllegalArgumentException("pool is null");

        // Defensive copy — never mutate the caller's list.
        List<MatchCandidate> remainder = new ArrayList<>(pool);
        Map<Archetype, Integer> archetypeCounts = countArchetypes(pool);
        int poolSize = pool.size();

        List<FormedGroup> formed = new ArrayList<>();
        List<MatchCandidate> deferred = new ArrayList<>();

        while (remainder.size() >= 3) {
            MatchCandidate seed = pickSeed(remainder);
            List<ScoredCandidate> scored = scoreCandidates(seed, remainder, archetypeCounts, poolSize);

            FormedGroup group = tryFormGroup(seed, scored, /*targetSize*/ 4);
            if (group == null) {
                group = tryFormGroup(seed, scored, /*targetSize*/ 3);
            }

            if (group == null) {
                // Seed cannot anchor any valid group with the current remainder.
                deferred.add(seed);
                remainder.remove(seed);
                continue;
            }

            formed.add(group);
            removeAll(remainder, group.members());
        }

        // Deferred seeds return to the remainder.
        remainder.addAll(deferred);
        return new BuildResult(formed, remainder);
    }

    // ── Seed selection ──────────────────────────────────────────────

    /**
     * Pick the candidate with the lowest spread between max and min
     * per-question score (clarity proxy). Tie-broken by userId for
     * deterministic seed selection.
     */
    static MatchCandidate pickSeed(List<MatchCandidate> pool) {
        return pool.stream()
                .min(Comparator
                        .<MatchCandidate>comparingInt(c -> spread(c.profile()))
                        .thenComparing(c -> c.profile().getUserId() != null
                                ? c.profile().getUserId().toString()
                                : ""))
                .orElseThrow(() -> new IllegalStateException("empty pool in pickSeed"));
    }

    /** max(scoreQ1..Q4) − min(scoreQ1..Q4). Lower = clearer profile. */
    static int spread(MatchingProfile p) {
        int max = Math.max(Math.max(p.getScoreQ1(), p.getScoreQ2()),
                           Math.max(p.getScoreQ3(), p.getScoreQ4()));
        int min = Math.min(Math.min(p.getScoreQ1(), p.getScoreQ2()),
                           Math.min(p.getScoreQ3(), p.getScoreQ4()));
        return max - min;
    }

    // ── Scoring & scarcity ──────────────────────────────────────────

    /** Pairwise score + scarcity adjustment, or absent if hard-rule-incompatible. */
    record ScoredCandidate(MatchCandidate candidate, int score) {}

    /**
     * Score every non-seed candidate against the seed. Excludes any
     * candidate failing the gender-pref hard rule. Returns scored
     * candidates sorted descending by score, then by userId for ties.
     */
    static List<ScoredCandidate> scoreCandidates(
            MatchCandidate seed,
            List<MatchCandidate> pool,
            Map<Archetype, Integer> archetypeCounts,
            int poolSize
    ) {
        List<ScoredCandidate> out = new ArrayList<>();
        UUID seedUserId = seed.profile().getUserId();
        for (MatchCandidate cand : pool) {
            if (cand.profile().getUserId().equals(seedUserId)) continue;

            // Hard rule: gender preference (pairwise).
            if (!HardRuleChecker.genderCompatible(
                    seed.gender(),
                    seed.profile().getPartnerGenderPref() != null
                            ? seed.profile().getPartnerGenderPref()
                            : PartnerGenderPref.ANY,
                    cand.gender(),
                    cand.profile().getPartnerGenderPref() != null
                            ? cand.profile().getPartnerGenderPref()
                            : PartnerGenderPref.ANY)) {
                continue;
            }

            int base = CompatibilityScorer.score(seed.profile(), cand.profile());
            int adjusted = base + scarcityAdjustment(cand.profile().getArchetype(),
                                                     archetypeCounts, poolSize);
            out.add(new ScoredCandidate(cand, adjusted));
        }
        out.sort(Comparator
                .<ScoredCandidate>comparingInt(s -> -s.score()) // desc
                .thenComparing(s -> s.candidate().profile().getUserId().toString()));
        return out;
    }

    /** Locked breakpoints: ≤10% +2, ≤20% +1, ≥50% −1, otherwise 0. */
    static int scarcityAdjustment(Archetype archetype,
                                  Map<Archetype, Integer> archetypeCounts,
                                  int poolSize) {
        if (poolSize == 0) return 0;
        int count = archetypeCounts.getOrDefault(archetype, 0);
        double share = (double) count / poolSize;
        if (share <= 0.10) return +2;
        if (share <= 0.20) return +1;
        if (share >= 0.50) return -1;
        return 0;
    }

    static Map<Archetype, Integer> countArchetypes(List<MatchCandidate> pool) {
        Map<Archetype, Integer> counts = new EnumMap<>(Archetype.class);
        for (MatchCandidate c : pool) {
            counts.merge(c.profile().getArchetype(), 1, Integer::sum);
        }
        return counts;
    }

    // ── Group assembly ──────────────────────────────────────────────

    /**
     * Try to form a group of exactly targetSize with seed + top candidates,
     * applying up to MAX_SWAPS lowest-member replacements if the group
     * fails hard rules. Returns null if no valid group of this size can
     * be formed.
     */
    static FormedGroup tryFormGroup(
            MatchCandidate seed,
            List<ScoredCandidate> scored,
            int targetSize
    ) {
        int neededOthers = targetSize - 1;
        if (scored.size() < neededOthers) return null;

        // Initial group: top N scored candidates.
        List<ScoredCandidate> chosen = new ArrayList<>(scored.subList(0, neededOthers));
        int swapCursor = neededOthers; // first unused candidate

        for (int attempt = 0; attempt <= MAX_SWAPS; attempt++) {
            FormedGroup tentative = buildGroup(seed, chosen);
            if (isValidGroup(tentative)) return tentative;

            // Swap the lowest-scored chosen member with the next unused.
            if (swapCursor >= scored.size()) return null; // out of candidates
            int lowestIdx = lowestScoreIndex(chosen);
            chosen.set(lowestIdx, scored.get(swapCursor));
            swapCursor++;
        }
        return null;
    }

    /** Build a FormedGroup record. Quality = sum of all pairwise scores. */
    static FormedGroup buildGroup(MatchCandidate seed, List<ScoredCandidate> others) {
        List<MatchCandidate> members = new ArrayList<>(others.size() + 1);
        members.add(seed);
        for (ScoredCandidate s : others) members.add(s.candidate());

        int quality = 0;
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                quality += CompatibilityScorer.score(
                        members.get(i).profile(), members.get(j).profile());
            }
        }
        Archetype dominant = pickDominantArchetype(members);
        return new FormedGroup(members, quality, dominant);
    }

    /** Most common archetype in group; ties broken by Archetype enum order. */
    static Archetype pickDominantArchetype(List<MatchCandidate> members) {
        Map<Archetype, Integer> counts = new EnumMap<>(Archetype.class);
        for (MatchCandidate m : members) counts.merge(m.profile().getArchetype(), 1, Integer::sum);
        Archetype best = null;
        int bestCount = -1;
        for (Archetype a : Archetype.values()) {
            int c = counts.getOrDefault(a, 0);
            if (c > bestCount) {
                bestCount = c;
                best = a;
            }
        }
        return best;
    }

    /** Validity = quality floor + all group-level hard rules. */
    static boolean isValidGroup(FormedGroup g) {
        if (g.qualityScore() < MIN_GROUP_QUALITY) return false;
        List<MatchingProfile> profiles = new ArrayList<>(g.members().size());
        for (MatchCandidate c : g.members()) profiles.add(c.profile());
        return HardRuleChecker.allGroupRulesPass(profiles);
    }

    static int lowestScoreIndex(List<ScoredCandidate> chosen) {
        int idx = 0;
        for (int i = 1; i < chosen.size(); i++) {
            if (chosen.get(i).score() < chosen.get(idx).score()) idx = i;
        }
        return idx;
    }

    static void removeAll(List<MatchCandidate> from, List<MatchCandidate> toRemove) {
        Set<UUID> ids = new HashSet<>();
        for (MatchCandidate c : toRemove) ids.add(c.profile().getUserId());
        from.removeIf(c -> ids.contains(c.profile().getUserId()));
    }
}
