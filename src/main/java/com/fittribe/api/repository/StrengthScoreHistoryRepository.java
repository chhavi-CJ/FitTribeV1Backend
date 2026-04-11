package com.fittribe.api.repository;

import com.fittribe.api.entity.StrengthScoreHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Data-access layer for the {@code strength_score_history} table (Flyway V36).
 *
 * <h3>Upsert semantics</h3>
 * {@code ProgressSnapshotService} may recompute the same week's scores
 * multiple times (once per session finish). The unique constraint
 * {@code uq_strength_score_user_muscle_week} on V36 makes every recompute
 * idempotent — the {@link #upsert} method turns that constraint into an
 * overwrite path via {@code ON CONFLICT DO UPDATE}.
 */
@Repository
public interface StrengthScoreHistoryRepository extends JpaRepository<StrengthScoreHistory, Long> {

    /**
     * Insert-or-overwrite the strength score for one (user, muscle, week).
     *
     * <p>Returns the number of affected rows. Postgres reports 1 for
     * both the insert and the update path.
     */
    @Modifying
    @Query(value = """
            INSERT INTO strength_score_history
                (user_id, muscle, week_start, strength_score, formula_version)
            VALUES
                (:userId, :muscle, :weekStart, :strengthScore, :formulaVersion)
            ON CONFLICT (user_id, muscle, week_start) DO UPDATE SET
                strength_score  = EXCLUDED.strength_score,
                formula_version = EXCLUDED.formula_version,
                computed_at     = NOW()
            """, nativeQuery = true)
    int upsert(
            @Param("userId")         UUID      userId,
            @Param("muscle")         String    muscle,
            @Param("weekStart")      LocalDate weekStart,
            @Param("strengthScore")  int       strengthScore,
            @Param("formulaVersion") int       formulaVersion);

    /**
     * Fetch all score rows for a user from {@code fromWeekStart} onward,
     * newest week first. Used by the Trends tab time-series query to
     * build the per-muscle sparkline data.
     *
     * <p>Returns all muscles for the matching weeks — the caller groups
     * by muscle to build per-muscle series.
     */
    List<StrengthScoreHistory> findByUserIdAndWeekStartGreaterThanEqualOrderByWeekStartDesc(
            UUID userId, LocalDate fromWeekStart);
}
