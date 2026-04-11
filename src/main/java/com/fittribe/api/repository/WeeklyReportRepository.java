package com.fittribe.api.repository;

import com.fittribe.api.entity.WeeklyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Data-access layer for the {@code weekly_reports} table (Flyway V32).
 *
 * <h3>Upsert semantics</h3>
 * The computer re-runs idempotently — if {@code WeeklyReportComputer}
 * is called twice for the same {@code (user_id, week_start)} the second
 * run must overwrite the first rather than inserting a duplicate. The
 * unique constraint {@code uq_weekly_reports_user_week} on V32 guards
 * against dupes; {@link #upsert} uses {@code ON CONFLICT DO UPDATE} to
 * turn that constraint into an overwrite path instead of a failure.
 *
 * <h3>Why native SQL instead of JPA {@code save()}</h3>
 * JPA's {@code save()} would either insert a new row (if id is null) or
 * update an existing row (if id is set). The upsert flow doesn't know
 * the id ahead of time — the computer produces a fresh {@code WeekData}
 * snapshot and needs to write it regardless of whether a previous row
 * exists. Native {@code ON CONFLICT} handles both cases in a single
 * round trip without a prior SELECT.
 *
 * <h3>JSONB binding</h3>
 * The JSONB columns are bound as {@code String} parameters and cast to
 * {@code jsonb} in the SQL (Postgres JDBC needs the explicit cast;
 * without it the driver sends them as {@code text} and the insert
 * fails). This matches the pattern used for {@code :findings::jsonb} in
 * WeekDataBuilder / JobEnqueuer.
 */
@Repository
public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {

    /**
     * Fetch the report for one user and week start, if one exists.
     * Used by {@code WeeklyReportController} when rendering the Weekly
     * Summary screen, and by tests to verify the upsert wrote the
     * expected row.
     */
    Optional<WeeklyReport> findByUserIdAndWeekStart(UUID userId, LocalDate weekStart);

    /**
     * Fetch the most recent report for a user, ordered by week start
     * date descending. Used by
     * {@code GET /api/v1/weekly-reports/latest} (Wynners A4.1).
     * Returns empty when no report has been computed yet for this user.
     */
    Optional<WeeklyReport> findTopByUserIdOrderByWeekStartDesc(UUID userId);

    /**
     * Insert-or-overwrite one report. All JSONB strings must be
     * serialized by the caller via the shared {@code ObjectMapper} bean
     * (normalizing {@code BigDecimal} via {@code stripTrailingZeros()}
     * before serialization is the computer's job — the repository
     * doesn't second-guess the payload).
     *
     * <p>Returns the number of affected rows. Postgres reports 1 for
     * both the insert and the update path (the whole statement is one
     * row either way), so the caller shouldn't rely on the return value
     * to distinguish them — use {@link #findByUserIdAndWeekStart} if the
     * id is needed.
     */
    @Modifying
    @Query(value = """
            INSERT INTO weekly_reports (
                user_id, week_start, week_end, week_number, user_first_name,
                sessions_logged, sessions_goal, total_kg_volume, pr_count,
                verdict,
                personal_records, baselines, muscle_coverage, findings, recalibrations,
                is_week_one, schema_version
            ) VALUES (
                :userId, :weekStart, :weekEnd, :weekNumber, :userFirstName,
                :sessionsLogged, :sessionsGoal, :totalKgVolume, :prCount,
                :verdict,
                CAST(:personalRecords AS jsonb),
                CAST(:baselines        AS jsonb),
                CAST(:muscleCoverage   AS jsonb),
                CAST(:findings         AS jsonb),
                CAST(:recalibrations   AS jsonb),
                :isWeekOne, :schemaVersion
            )
            ON CONFLICT (user_id, week_start) DO UPDATE SET
                week_end         = EXCLUDED.week_end,
                week_number      = EXCLUDED.week_number,
                user_first_name  = EXCLUDED.user_first_name,
                sessions_logged  = EXCLUDED.sessions_logged,
                sessions_goal    = EXCLUDED.sessions_goal,
                total_kg_volume  = EXCLUDED.total_kg_volume,
                pr_count         = EXCLUDED.pr_count,
                verdict          = EXCLUDED.verdict,
                personal_records = EXCLUDED.personal_records,
                baselines        = EXCLUDED.baselines,
                muscle_coverage  = EXCLUDED.muscle_coverage,
                findings         = EXCLUDED.findings,
                recalibrations   = EXCLUDED.recalibrations,
                is_week_one      = EXCLUDED.is_week_one,
                computed_at      = NOW(),
                schema_version   = EXCLUDED.schema_version
            """, nativeQuery = true)
    int upsert(
            @Param("userId")          UUID       userId,
            @Param("weekStart")       LocalDate  weekStart,
            @Param("weekEnd")         LocalDate  weekEnd,
            @Param("weekNumber")      int        weekNumber,
            @Param("userFirstName")   String     userFirstName,
            @Param("sessionsLogged")  int        sessionsLogged,
            @Param("sessionsGoal")    int        sessionsGoal,
            @Param("totalKgVolume")   BigDecimal totalKgVolume,
            @Param("prCount")         int        prCount,
            @Param("verdict")         String     verdict,
            @Param("personalRecords") String     personalRecords,
            @Param("baselines")       String     baselines,
            @Param("muscleCoverage")  String     muscleCoverage,
            @Param("findings")        String     findings,
            @Param("recalibrations")  String     recalibrations,
            @Param("isWeekOne")       boolean    isWeekOne,
            @Param("schemaVersion")   int        schemaVersion);
}
