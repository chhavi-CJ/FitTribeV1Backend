package com.fittribe.api.repository;

import com.fittribe.api.entity.PersonalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
}
