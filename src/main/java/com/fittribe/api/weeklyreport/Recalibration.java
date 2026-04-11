package com.fittribe.api.weeklyreport;

import java.math.BigDecimal;

/**
 * One recalibration decision for a single exercise, produced by
 * {@link RecalibrationDetector} when the week's findings indicate the
 * planned weight should be adjusted.
 *
 * <p>The detector is a pure function — this record carries only the
 * decision. Persistence to {@code pending_recalibrations} happens in
 * {@code WeeklyReportComputer} (Stage 4). v1.1 §A3.2 is explicit that
 * recalibrations are NEVER written back directly to {@code user_plans};
 * they go to the side table and are consumed at plan-generation time.
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code exerciseId} — catalog id, matches
 *       {@code exercises.id}</li>
 *   <li>{@code oldTargetKg} — the target the user was training
 *       against, pulled from
 *       {@code WeekData.targetExercises[id].targetKg}. Never null —
 *       entries without a target are filtered out up-stream</li>
 *   <li>{@code newTargetKg} — the recommended new target. For
 *       too-light findings this is the logged max rounded to the
 *       nearest 2.5kg. For pr_regression it is the previous week's
 *       confirmed top-set weight (no rounding — we honour exactly
 *       what the user hit)</li>
 *   <li>{@code reason} — short human-readable explanation of why the
 *       recalibration was emitted, surfaced in the weekly report UI
 *       and in audit logs</li>
 * </ul>
 */
public record Recalibration(
        String exerciseId,
        BigDecimal oldTargetKg,
        BigDecimal newTargetKg,
        String reason
) {}
