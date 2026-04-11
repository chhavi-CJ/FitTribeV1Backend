package com.fittribe.api.repository;

import com.fittribe.api.entity.UserProgressSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Data-access layer for the {@code user_progress_snapshot} table (Flyway V35).
 *
 * <h3>Why native upsert instead of JPA save()</h3>
 * The snapshot is always overwritten on recompute. JPA {@code save()} would
 * need a prior SELECT to decide INSERT vs UPDATE; the native
 * {@code ON CONFLICT (user_id) DO UPDATE} collapses both paths into one
 * round trip — same pattern as {@link WeeklyReportRepository#upsert}.
 *
 * <h3>JSONB binding</h3>
 * The {@code data} column is bound as a {@code String} and cast to
 * {@code jsonb} in the SQL — Postgres JDBC requires the explicit cast;
 * without it the driver sends the value as {@code text} and the INSERT
 * fails.
 */
@Repository
public interface UserProgressSnapshotRepository extends JpaRepository<UserProgressSnapshot, UUID> {

    /**
     * Insert-or-overwrite the progress snapshot for one user.
     *
     * <p>{@code data} must be a valid JSON string serialised by the
     * caller via the shared {@code ObjectMapper} bean.
     */
    @Modifying
    @Query(value = """
            INSERT INTO user_progress_snapshot (user_id, data, schema_version)
            VALUES (:userId, CAST(:data AS jsonb), :schemaVersion)
            ON CONFLICT (user_id) DO UPDATE SET
                data           = EXCLUDED.data,
                schema_version = EXCLUDED.schema_version,
                computed_at    = NOW()
            """, nativeQuery = true)
    int upsert(
            @Param("userId")        UUID   userId,
            @Param("data")          String data,
            @Param("schemaVersion") int    schemaVersion);
}
