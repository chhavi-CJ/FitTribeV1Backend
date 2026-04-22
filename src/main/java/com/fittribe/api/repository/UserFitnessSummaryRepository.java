package com.fittribe.api.repository;

import com.fittribe.api.entity.UserFitnessSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Data-access layer for {@code user_fitness_summary} (Flyway V49).
 *
 * <p>Uses a native upsert so the nightly job can INSERT or UPDATE in a single
 * round-trip. The {@code summary} parameter must be valid JSON — Postgres
 * requires an explicit {@code CAST(... AS jsonb)} when binding a String
 * parameter to a JSONB column via JDBC.
 */
@Repository
public interface UserFitnessSummaryRepository extends JpaRepository<UserFitnessSummary, UUID> {

    /**
     * Insert or update the fitness summary for a user.
     *
     * <p>ON CONFLICT (user_id) replaces all mutable columns.
     * {@code computed_at} is set to NOW() server-side for consistency.
     *
     * @param userId       the user to upsert for
     * @param summary      JSON string of the serialised {@code FitnessSummary}
     * @param sampleWindow human-readable window label, e.g. "2026-03-27 to 2026-04-22"
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO user_fitness_summary (user_id, summary, computed_at, sample_window)
        VALUES (:userId, CAST(:summary AS jsonb), NOW(), :sampleWindow)
        ON CONFLICT (user_id) DO UPDATE SET
            summary      = CAST(:summary AS jsonb),
            computed_at  = NOW(),
            sample_window = EXCLUDED.sample_window
        """, nativeQuery = true)
    void upsert(
            @Param("userId")       UUID   userId,
            @Param("summary")      String summary,
            @Param("sampleWindow") String sampleWindow
    );
}
