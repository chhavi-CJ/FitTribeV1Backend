package com.fittribe.api.matching;

import com.fittribe.api.entity.Archetype;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GroupNameGenerator#pickName}.
 *
 * <p>Pure-logic generator — no Spring context, no DB. Uniqueness is
 * supplied through a {@link GroupNameUniquenessChecker} stub. Seeded
 * {@link Random} values used in deterministic tests were observed by
 * running the generator with that seed before the assertion was written,
 * then hardcoded — so a later reorder of the name pools fails these tests
 * loudly rather than silently changing behaviour.
 */
class GroupNameGeneratorTest {

    /** A checker where the given names are taken and everything else is free. */
    private static GroupNameUniquenessChecker takenSet(String... names) {
        Set<String> taken = Set.of(names);
        return taken::contains;
    }

    /** A checker where no name is ever taken. */
    private static final GroupNameUniquenessChecker FREE = name -> false;

    @Test
    void picks_from_archetype_themed_pool_when_all_free() {
        // Observed: new Random(7), RETURNER, size 3, all free -> "Comeback Crew".
        GroupNameGenerator gen = new GroupNameGenerator(new Random(7));
        String name = gen.pickName(Archetype.RETURNER, 3, FREE);
        assertEquals("Comeback Crew", name);
        assertTrue(Set.of("Comeback Crew", "The Returners", "Second Wind", "Phoenix Club")
                .contains(name));
    }

    @Test
    void falls_back_to_mixed_pool_when_all_archetype_names_taken() {
        GroupNameUniquenessChecker checker =
                takenSet("The Grinders", "No Days Off", "The Discipline Club");
        GroupNameGenerator gen = new GroupNameGenerator(new Random(7));
        String name = gen.pickName(Archetype.GRINDER, 4, checker);
        assertTrue(Set.of("The Lifters", "Sweat Equity", "Four In The Gym").contains(name),
                "expected a mixed-pool name but got: " + name);
    }

    @Test
    void resolves_three_in_the_gym_template_for_size_3() {
        // Every SEEKER name + both non-template mixed names taken.
        GroupNameUniquenessChecker checker = takenSet(
                "The New Crew", "Found It", "Day One Club", "The Try Club",
                "The Lifters", "Sweat Equity");
        GroupNameGenerator gen = new GroupNameGenerator(new Random(7));
        String name = gen.pickName(Archetype.SEEKER, 3, checker);
        assertEquals("Three In The Gym", name);
    }

    @Test
    void resolves_four_in_the_gym_template_for_size_4() {
        GroupNameUniquenessChecker checker = takenSet(
                "The New Crew", "Found It", "Day One Club", "The Try Club",
                "The Lifters", "Sweat Equity");
        GroupNameGenerator gen = new GroupNameGenerator(new Random(7));
        String name = gen.pickName(Archetype.SEEKER, 4, checker);
        assertEquals("Four In The Gym", name);
    }

    @Test
    void appends_numeric_suffix_when_both_pools_exhausted() {
        // All ANCHOR names + all mixed names (template resolved for size 3) taken.
        GroupNameUniquenessChecker checker = takenSet(
                "The Anchors", "Steady Crew", "Morning Grind", "The Regulars", "Iron Disciplined",
                "The Lifters", "Sweat Equity", "Three In The Gym");
        GroupNameGenerator gen = new GroupNameGenerator(new Random(42));
        // Step-3 fallback uses the dominant pool's first element ("The Anchors").
        assertEquals("The Anchors 2", gen.pickName(Archetype.ANCHOR, 3, checker));
    }

    @Test
    void continues_incrementing_suffix_until_unique() {
        GroupNameUniquenessChecker checker = takenSet(
                "The Anchors", "Steady Crew", "Morning Grind", "The Regulars", "Iron Disciplined",
                "The Lifters", "Sweat Equity", "Three In The Gym",
                "The Anchors 2");
        GroupNameGenerator gen = new GroupNameGenerator(new Random(42));
        assertEquals("The Anchors 3", gen.pickName(Archetype.ANCHOR, 3, checker));
    }

    @Test
    void rejects_null_archetype() {
        GroupNameGenerator gen = new GroupNameGenerator(new Random(1));
        assertThrows(IllegalArgumentException.class,
                () -> gen.pickName(null, 3, FREE));
    }

    @Test
    void rejects_null_checker() {
        GroupNameGenerator gen = new GroupNameGenerator(new Random(1));
        assertThrows(IllegalArgumentException.class,
                () -> gen.pickName(Archetype.ANCHOR, 3, null));
    }

    @Test
    void rejects_group_size_too_small() {
        GroupNameGenerator gen = new GroupNameGenerator(new Random(1));
        assertThrows(IllegalArgumentException.class,
                () -> gen.pickName(Archetype.ANCHOR, 1, FREE));
    }

    @Test
    void rejects_group_size_too_large() {
        GroupNameGenerator gen = new GroupNameGenerator(new Random(1));
        assertThrows(IllegalArgumentException.class,
                () -> gen.pickName(Archetype.ANCHOR, 9, FREE));
    }

    @Test
    void accepts_group_size_3_and_4() {
        GroupNameGenerator gen = new GroupNameGenerator(new Random(1));
        String three = gen.pickName(Archetype.ANCHOR, 3, FREE);
        String four = gen.pickName(Archetype.ANCHOR, 4, FREE);
        assertNotNull(three);
        assertNotNull(four);
        assertFalse(three.isBlank());
        assertFalse(four.isBlank());
    }

    @Test
    void does_not_mutate_shared_pool() {
        // Across many calls with a free checker, every returned name must
        // still be a valid ANCHOR pool entry. If the constant pool were
        // shuffled/mutated in place, or duplicated into, this would drift.
        Set<String> validAnchorNames = Set.of(
                "The Anchors", "Steady Crew", "Morning Grind", "The Regulars", "Iron Disciplined");
        Set<String> returned = new HashSet<>();
        GroupNameGenerator gen = new GroupNameGenerator(new Random(1));
        for (int i = 0; i < 50; i++) {
            String name = gen.pickName(Archetype.ANCHOR, 3, FREE);
            assertTrue(validAnchorNames.contains(name),
                    "returned name not a valid ANCHOR pool entry: " + name);
            returned.add(name);
        }
        // Sanity: never produced more distinct names than the pool holds.
        assertTrue(returned.size() <= validAnchorNames.size());
    }
}
