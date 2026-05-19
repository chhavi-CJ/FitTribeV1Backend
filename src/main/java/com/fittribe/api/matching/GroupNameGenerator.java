package com.fittribe.api.matching;

import com.fittribe.api.entity.Archetype;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Picks a group name for a newly-matched group, organized by the
 * group's dominant archetype.
 *
 * <p><b>Strategy:</b>
 * <ol>
 *   <li>Look up the archetype-themed pool for the dominant archetype.
 *       Shuffle, then return the first name that isn't already taken.</li>
 *   <li>If every archetype-themed name is taken, fall back to the mixed
 *       pool (Iron Trio, The Lifters, etc.). Same shuffle-and-pick.</li>
 *   <li>If even the mixed pool is exhausted, append a numeric suffix
 *       to the first name in the dominant pool ("Comeback Crew 2",
 *       "Comeback Crew 3", ...) until uniqueness is achieved. This
 *       only kicks in at scale; for the first few hundred matched
 *       groups it will not trigger.</li>
 * </ol>
 *
 * <p>The "Three In The Gym" / "Four In The Gym" entry in the mixed pool
 * is auto-pluralized based on the group size passed in.
 *
 * <p>Pure logic — no Spring beans, no DB. The uniqueness check passes
 * through a {@link GroupNameUniquenessChecker} that Step 4b wires to
 * GroupRepository.
 */
public final class GroupNameGenerator {

    /** Pool of archetype-themed names. Editable by hand. */
    private static final Map<Archetype, List<String>> ARCHETYPE_POOLS = Map.of(
            Archetype.ANCHOR, List.of(
                    "The Anchors",
                    "Steady Crew",
                    "Morning Grind",
                    "The Regulars",
                    "Iron Disciplined"
            ),
            Archetype.STRIVER, List.of(
                    "Iron Trio",
                    "The Strivers",
                    "Heavy Hitters",
                    "The Sprint Club"
            ),
            Archetype.RETURNER, List.of(
                    "Comeback Crew",
                    "The Returners",
                    "Second Wind",
                    "Phoenix Club"
            ),
            Archetype.GRINDER, List.of(
                    "The Grinders",
                    "No Days Off",
                    "The Discipline Club"
            ),
            Archetype.SOCIAL_BUTTERFLY, List.of(
                    "The Squad",
                    "Friends Who Lift",
                    "Crew Cardio",
                    "The Group Chat"
            ),
            Archetype.SEEKER, List.of(
                    "The New Crew",
                    "Found It",
                    "Day One Club",
                    "The Try Club"
            )
    );

    /**
     * Mixed / universal fallback. Used when the archetype pool is
     * exhausted, or when no clear dominant archetype exists.
     * The "{N} In The Gym" entry is a template — the actual name is
     * built from {@link #groupSizeNumberWord(int)}.
     */
    private static final List<String> MIXED_POOL = List.of(
            "The Lifters",
            "Sweat Equity",
            "{N} In The Gym"
    );

    /** Templates that include {N} are resolved against group size. */
    private static final String SIZE_TEMPLATE_MARKER = "{N}";

    private final Random random;

    /** Default constructor — uses a freshly-seeded Random. */
    public GroupNameGenerator() {
        this(new Random());
    }

    /** Test constructor — accepts a seeded Random for deterministic tests. */
    public GroupNameGenerator(Random random) {
        if (random == null) throw new IllegalArgumentException("random is null");
        this.random = random;
    }

    /**
     * Pick a unique name for a newly-formed matched group.
     *
     * @param dominantArchetype the archetype best representing the group.
     *        Determined by the matching engine — typically the most common
     *        archetype among members, with ties broken in the order
     *        defined by {@link Archetype}.
     * @param groupSize the number of members (3 or 4).
     * @param uniquenessChecker checks whether a candidate is already taken.
     *        Must be non-null.
     * @return a name not currently in use, resolved for group size where
     *         the template requires it.
     * @throws IllegalArgumentException if any argument is null, or groupSize
     *         is outside [2, 8] (defensive — matching engine enforces 3 or 4,
     *         but we accept a wider band so the generator is reusable).
     */
    public String pickName(
            Archetype dominantArchetype,
            int groupSize,
            GroupNameUniquenessChecker uniquenessChecker
    ) {
        if (dominantArchetype == null) throw new IllegalArgumentException("dominantArchetype is null");
        if (uniquenessChecker == null)  throw new IllegalArgumentException("uniquenessChecker is null");
        if (groupSize < 2 || groupSize > 8) {
            throw new IllegalArgumentException("groupSize out of range: " + groupSize);
        }

        // 1. Try the archetype-themed pool.
        List<String> primary = ARCHETYPE_POOLS.getOrDefault(dominantArchetype, List.of());
        String pick = tryPool(primary, groupSize, uniquenessChecker);
        if (pick != null) return pick;

        // 2. Fall back to the mixed pool.
        pick = tryPool(MIXED_POOL, groupSize, uniquenessChecker);
        if (pick != null) return pick;

        // 3. Append a numeric suffix to the first name of the dominant pool.
        //    Use the dominant pool's first name (not mixed) so the result
        //    still reads as archetype-themed.
        String base = !primary.isEmpty() ? resolveTemplate(primary.get(0), groupSize)
                                         : resolveTemplate(MIXED_POOL.get(0), groupSize);
        int suffix = 2;
        while (uniquenessChecker.isTaken(base + " " + suffix)) {
            suffix++;
            if (suffix > 10_000) {
                // Defensive — should never happen in practice.
                throw new IllegalStateException("Could not find unique name within 10000 suffix attempts");
            }
        }
        return base + " " + suffix;
    }

    /** Shuffle a defensive copy of the pool and return the first untaken
     *  name (after template resolution), or null if all are taken. */
    private String tryPool(List<String> pool, int groupSize, GroupNameUniquenessChecker checker) {
        if (pool.isEmpty()) return null;
        // Defensive copy — never mutate the constant pool.
        java.util.List<String> shuffled = new java.util.ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, random);
        for (String candidate : shuffled) {
            String resolved = resolveTemplate(candidate, groupSize);
            if (!checker.isTaken(resolved)) return resolved;
        }
        return null;
    }

    /** Resolve {N} templates to the spelled-out number word for groupSize. */
    private static String resolveTemplate(String template, int groupSize) {
        if (!template.contains(SIZE_TEMPLATE_MARKER)) return template;
        return template.replace(SIZE_TEMPLATE_MARKER, groupSizeNumberWord(groupSize));
    }

    /** Convert a small group size to its English word, capitalized. */
    private static String groupSizeNumberWord(int groupSize) {
        return switch (groupSize) {
            case 2 -> "Two";
            case 3 -> "Three";
            case 4 -> "Four";
            case 5 -> "Five";
            case 6 -> "Six";
            case 7 -> "Seven";
            case 8 -> "Eight";
            default -> String.valueOf(groupSize);
        };
    }
}
