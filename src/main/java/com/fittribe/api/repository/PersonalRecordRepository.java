package com.fittribe.api.repository;

import com.fittribe.api.entity.PersonalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PersonalRecordRepository extends JpaRepository<PersonalRecord, Long> {

    /**
     * Upsert a personal record for one exercise.
     * Only overwrites if the new weight is strictly greater than the stored best.
     * Returns the number of rows affected (1 = new PR set, 0 = not a PR).
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO personal_records (user_id, exercise_id, weight_kg, reps, achieved_at)
        VALUES (:userId, :exerciseId, :weightKg, :reps, NOW())
        ON CONFLICT (user_id, exercise_id)
        DO UPDATE SET
            weight_kg   = EXCLUDED.weight_kg,
            reps        = EXCLUDED.reps,
            achieved_at = NOW()
        WHERE personal_records.weight_kg < EXCLUDED.weight_kg
        """, nativeQuery = true)
    int upsertPr(
            @Param("userId")     UUID       userId,
            @Param("exerciseId") String     exerciseId,
            @Param("weightKg")   BigDecimal weightKg,
            @Param("reps")       int        reps
    );

    /** Total distinct PRs held by this user — used in profile response. */
    @Query(value = "SELECT COUNT(*) FROM personal_records WHERE user_id = :userId",
           nativeQuery = true)
    int countByUserId(@Param("userId") UUID userId);

    /**
     * Personal records achieved in {@code [from, to)} for this user.
     *
     * Note: {@code personal_records.achieved_at} is declared as
     * {@code TIMESTAMP} (tz-naive) in V22, while the bind parameters here
     * are {@link Instant} (tz-aware). The cast
     * {@code (pr.achieved_at AT TIME ZONE 'UTC')} promotes the column to
     * {@code TIMESTAMPTZ} assuming the stored clock is UTC — which it is,
     * because the DB server is set to UTC and {@code NOW()} at upsert time
     * is UTC.
     *
     * Returns Object[] rows: [exercise_id (String), weight_kg (BigDecimal),
     * reps (Integer), achieved_at (Instant)].
     */
    @Query(value = """
        SELECT pr.exercise_id,
               pr.weight_kg,
               pr.reps,
               (pr.achieved_at AT TIME ZONE 'UTC') AS achieved_at_tz
        FROM personal_records pr
        WHERE pr.user_id = :userId
          AND (pr.achieved_at AT TIME ZONE 'UTC') >= :from
          AND (pr.achieved_at AT TIME ZONE 'UTC') <  :to
        """, nativeQuery = true)
    List<Object[]> findAchievedInWindow(
            @Param("userId") UUID    userId,
            @Param("from")   Instant from,
            @Param("to")     Instant to);
}
