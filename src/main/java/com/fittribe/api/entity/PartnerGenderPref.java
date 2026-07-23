package com.fittribe.api.entity;

/**
 * Gender preference for matching. Per-user soft constraint applied at
 * group-builder stage. SAME = filter to same-gender candidates only.
 * ANY = widen the pool to all genders.
 */
public enum PartnerGenderPref {
    SAME,
    ANY
}
