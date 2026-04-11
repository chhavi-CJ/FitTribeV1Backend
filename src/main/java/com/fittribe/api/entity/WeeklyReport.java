package com.fittribe.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA mapping for the {@code weekly_reports} table (Flyway V32).
 *
 * <p>One computed report per {@code (user_id, week_start)} — rewritten
 * in place when the same week is recomputed (enforced by the unique
 * constraint and the repository's {@code ON CONFLICT DO UPDATE} upsert).
 * Written by {@code WeeklyReportComputer} from a frozen
 * {@code WeekData} snapshot and the findings/recalibrations/verdict
 * derived from it.
 *
 * <h3>JSONB columns</h3>
 * {@code personal_records}, {@code baselines}, {@code muscle_coverage},
 * {@code findings}, and {@code recalibrations} are stored as raw JSONB
 * and mapped here to {@link String}. This mirrors the pattern used by
 * {@link com.fittribe.api.jobs.PendingJob#getPayload()} and
 * {@link WorkoutSession#getExercises()} — the computer serializes the
 * in-memory shapes via the shared {@code ObjectMapper} before saving,
 * and the frontend deserializes on read. Keeping the entity field a
 * String avoids Hibernate having to reflect into dynamic Java types.
 *
 * <h3>Why scalar columns duplicate some JSONB contents</h3>
 * {@code sessions_logged}, {@code sessions_goal}, {@code total_kg_volume},
 * {@code pr_count}, and {@code user_first_name} are fast-path fields
 * used by list views (Progress → Stats "Week N report ready" card, the
 * weekly history list). Pulling them as scalars avoids parsing the JSONB
 * blobs just to render a one-line summary.
 */
@Entity
@Table(name = "weekly_reports")
public class WeeklyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "week_end", nullable = false)
    private LocalDate weekEnd;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "user_first_name", nullable = false, length = 100)
    private String userFirstName;

    @Column(name = "sessions_logged", nullable = false)
    private Integer sessionsLogged = 0;

    @Column(name = "sessions_goal", nullable = false)
    private Integer sessionsGoal = 0;

    @Column(name = "total_kg_volume", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalKgVolume = BigDecimal.ZERO;

    @Column(name = "pr_count", nullable = false)
    private Integer prCount = 0;

    /** One-sentence AI verdict — nullable by design (see VerdictGenerator). */
    @Column(name = "verdict")
    private String verdict;

    @Column(name = "personal_records", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String personalRecords = "[]";

    @Column(name = "baselines", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String baselines = "[]";

    @Column(name = "muscle_coverage", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String muscleCoverage = "[]";

    @Column(name = "findings", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String findings = "[]";

    @Column(name = "recalibrations", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String recalibrations = "[]";

    @Column(name = "is_week_one", nullable = false)
    private Boolean isWeekOne = false;

    @Column(name = "computed_at", insertable = false, updatable = false)
    private Instant computedAt;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion = 1;

    public WeeklyReport() {}

    // ── Accessors ─────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }

    public LocalDate getWeekEnd() { return weekEnd; }
    public void setWeekEnd(LocalDate weekEnd) { this.weekEnd = weekEnd; }

    public Integer getWeekNumber() { return weekNumber; }
    public void setWeekNumber(Integer weekNumber) { this.weekNumber = weekNumber; }

    public String getUserFirstName() { return userFirstName; }
    public void setUserFirstName(String userFirstName) { this.userFirstName = userFirstName; }

    public Integer getSessionsLogged() { return sessionsLogged; }
    public void setSessionsLogged(Integer sessionsLogged) { this.sessionsLogged = sessionsLogged; }

    public Integer getSessionsGoal() { return sessionsGoal; }
    public void setSessionsGoal(Integer sessionsGoal) { this.sessionsGoal = sessionsGoal; }

    public BigDecimal getTotalKgVolume() { return totalKgVolume; }
    public void setTotalKgVolume(BigDecimal totalKgVolume) { this.totalKgVolume = totalKgVolume; }

    public Integer getPrCount() { return prCount; }
    public void setPrCount(Integer prCount) { this.prCount = prCount; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public String getPersonalRecords() { return personalRecords; }
    public void setPersonalRecords(String personalRecords) { this.personalRecords = personalRecords; }

    public String getBaselines() { return baselines; }
    public void setBaselines(String baselines) { this.baselines = baselines; }

    public String getMuscleCoverage() { return muscleCoverage; }
    public void setMuscleCoverage(String muscleCoverage) { this.muscleCoverage = muscleCoverage; }

    public String getFindings() { return findings; }
    public void setFindings(String findings) { this.findings = findings; }

    public String getRecalibrations() { return recalibrations; }
    public void setRecalibrations(String recalibrations) { this.recalibrations = recalibrations; }

    public Boolean getIsWeekOne() { return isWeekOne; }
    public void setIsWeekOne(Boolean isWeekOne) { this.isWeekOne = isWeekOne; }

    public Instant getComputedAt() { return computedAt; }

    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
}
