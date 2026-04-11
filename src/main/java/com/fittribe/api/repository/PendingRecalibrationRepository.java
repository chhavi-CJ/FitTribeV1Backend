package com.fittribe.api.repository;

import com.fittribe.api.entity.PendingRecalibration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data-access layer for the {@code pending_recalibrations} side table
 * (Flyway V34).
 *
 * <p>Producer is {@code WeeklyReportComputer} — one INSERT per
 * {@code Recalibration} emitted by {@link com.fittribe.api.weeklyreport.RecalibrationDetector}.
 * Consumer is the AI plan generator, which reads unapplied rows for a
 * user at plan-generation time and marks them applied once the new
 * plan has baked them in.
 *
 * <h3>Why not a plain {@code JpaRepository.save()}</h3>
 * The rows are append-only during normal operation (the generator
 * marks-applied but never deletes), and the side-table lifecycle is
 * too simple to need entity-level dirty tracking. Using native SQL
 * makes it obvious where writes happen and matches the style of the
 * sibling {@code WeeklyReportRepository} upsert.
 */
@Repository
public interface PendingRecalibrationRepository extends JpaRepository<PendingRecalibration, Long> {

    /**
     * All unapplied recalibrations for one user, oldest first. This is
     * the query the AI plan generator runs at plan-build time; the
     * partial index {@code idx_recalibrations_user_unapplied} on
     * {@code user_id WHERE applied_at IS NULL} makes it cheap as history
     * accumulates.
     */
    @Query(value = """
            SELECT * FROM pending_recalibrations
            WHERE user_id = :userId
              AND applied_at IS NULL
            ORDER BY created_at ASC
            """, nativeQuery = true)
    List<PendingRecalibration> findUnappliedByUser(@Param("userId") UUID userId);

    /**
     * Mark one row applied to the given plan. Idempotent — a second
     * call for an already-applied row is a no-op because the
     * {@code applied_at IS NULL} guard filters it out.
     */
    @Modifying
    @Query(value = """
            UPDATE pending_recalibrations
            SET applied_at = NOW(),
                applied_to_plan_id = :planId
            WHERE id = :id
              AND applied_at IS NULL
            """, nativeQuery = true)
    int markApplied(@Param("id") Long id, @Param("planId") UUID planId);
}
