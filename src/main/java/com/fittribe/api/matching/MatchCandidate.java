package com.fittribe.api.matching;

import com.fittribe.api.entity.MatchingProfile;

/**
 * Input pair to the matching engine: a {@link MatchingProfile} plus the
 * candidate's gender (read from User.gender at the DB-loading boundary).
 *
 * Why a wrapper instead of fetching User from the engine? The engine is
 * pure logic — it should never need a Spring bean, a UserRepository, or
 * any I/O. The DB-wiring service (Step 4b.ii) does the joins and hands
 * the engine fully-resolved candidates.
 *
 * Immutable. {@code gender} may be null (some users won't have set one).
 */
public record MatchCandidate(MatchingProfile profile, String gender) {
    public MatchCandidate {
        if (profile == null) throw new IllegalArgumentException("profile is null");
    }
}
