package com.fittribe.api.entity;

/**
 * Six psychological archetypes that classify a user's matching profile.
 * See PRD section 4 for descriptions and pairing rules.
 *
 * Three of these (STRIVER, RETURNER, SOCIAL_BUTTERFLY) are "fragile" —
 * two of the same fragile type alone in a group reinforce each other's
 * weakness and the group quietly collapses. Stabilisers (ANCHOR, GRINDER)
 * counteract this. This rule is enforced in the matching engine, not here.
 */
public enum Archetype {
    ANCHOR,
    STRIVER,
    RETURNER,
    SOCIAL_BUTTERFLY,
    GRINDER,
    SEEKER
}
