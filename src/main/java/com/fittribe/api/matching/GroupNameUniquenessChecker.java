package com.fittribe.api.matching;

/**
 * Functional interface the name generator uses to check whether a
 * candidate name is already taken by an existing group. Step 4b wires
 * this to GroupRepository; the generator itself stays DB-free for
 * unit-testability.
 */
@FunctionalInterface
public interface GroupNameUniquenessChecker {
    /** @return true if a group with this name already exists. */
    boolean isTaken(String name);
}
