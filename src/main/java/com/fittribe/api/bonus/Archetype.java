package com.fittribe.api.bonus;

/**
 * Type of bonus session to generate. Resolver picks one based on user
 * profile, recovery state, and bonuses already done this week.
 *
 * PUSH / PULL / LEGS          — compound-heavy sessions for recovered users
 * WEAK_POINT_FOCUS            — isolation-heavy session targeting an underworked group
 * ACCESSORY_CORE              — safe default for injured, pregnant, overtrained, or heavily-capped users
 */
public enum Archetype {
    PUSH,
    PULL,
    LEGS,
    WEAK_POINT_FOCUS,
    ACCESSORY_CORE
}
