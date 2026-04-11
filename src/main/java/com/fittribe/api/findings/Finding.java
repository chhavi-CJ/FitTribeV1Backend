package com.fittribe.api.findings;

/**
 * One finding produced by a {@link FindingsRule} evaluating a {@link WeekData}.
 * Immutable by construction (record).
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code ruleId} — the ID of the rule that produced it, matching a key
 *       under {@code fittribe.findings.templates} in the YAML. Used by the
 *       downstream {@code FindingsGenerator} (A2.2) for dedup / tracing.</li>
 *   <li>{@code severity} — "RED", "AMBER", or "GREEN", mirroring the YAML.
 *       Kept as a String rather than an enum because the set is small and
 *       adding a new severity level is a YAML-only change today.</li>
 *   <li>{@code weight} — priority score from the template. Higher = more
 *       important. The generator sorts by weight after grouping by severity.</li>
 *   <li>{@code title}, {@code detail} — user-facing copy with template
 *       placeholders already interpolated by the rule.</li>
 * </ul>
 */
public record Finding(
        String ruleId,
        String severity,
        int weight,
        String title,
        String detail
) {}
