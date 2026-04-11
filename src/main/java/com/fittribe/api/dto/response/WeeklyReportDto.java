package com.fittribe.api.dto.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.WeeklyReport;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for {@code GET /api/v1/weekly-reports/latest} and
 * {@code GET /api/v1/weekly-reports/{id}} (Wynners A4.1 / A4.2).
 *
 * <p>The five JSONB columns ({@code personal_records}, {@code baselines},
 * {@code muscle_coverage}, {@code findings}, {@code recalibrations}) are
 * stored in the database as raw JSON strings. {@link #from} deserializes
 * each one via the shared {@link ObjectMapper} into a
 * {@code List<Map<String,Object>>} so the response body contains a proper
 * nested JSON array rather than an escaped string. If a column is null,
 * blank, or malformed (DB corruption), the field degrades to an empty list
 * rather than throwing — the rest of the report is still usable.
 *
 * <p>Scalar columns ({@code sessions_logged}, {@code sessions_goal}, etc.)
 * are copied directly from the entity with no transformation.
 */
public class WeeklyReportDto {

    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_REF =
            new TypeReference<>() {};

    private Long id;
    private Integer weekNumber;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private String userFirstName;
    private Boolean isWeekOne;
    private Integer sessionsLogged;
    private Integer sessionsGoal;
    private BigDecimal totalKgVolume;
    private Integer prCount;
    /** May be null when no OpenAI key was configured at compute time. */
    private String verdict;
    private List<Map<String, Object>> personalRecords;
    private List<Map<String, Object>> baselines;
    private List<Map<String, Object>> muscleCoverage;
    private List<Map<String, Object>> findings;
    private List<Map<String, Object>> recalibrations;
    private Instant computedAt;

    public WeeklyReportDto() {}

    /**
     * Build a DTO from a {@link WeeklyReport} entity, deserializing the
     * five JSONB columns using the provided {@link ObjectMapper}.
     *
     * @param row    the entity row read from {@code weekly_reports}
     * @param mapper the shared Spring {@code ObjectMapper} bean
     * @return a fully populated DTO
     */
    public static WeeklyReportDto from(WeeklyReport row, ObjectMapper mapper) {
        WeeklyReportDto dto = new WeeklyReportDto();
        dto.id             = row.getId();
        dto.weekNumber     = row.getWeekNumber();
        dto.weekStart      = row.getWeekStart();
        dto.weekEnd        = row.getWeekEnd();
        dto.userFirstName  = row.getUserFirstName();
        dto.isWeekOne      = row.getIsWeekOne();
        dto.sessionsLogged = row.getSessionsLogged();
        dto.sessionsGoal   = row.getSessionsGoal();
        dto.totalKgVolume  = row.getTotalKgVolume();
        dto.prCount        = row.getPrCount();
        dto.verdict        = row.getVerdict();
        dto.personalRecords = parseJsonbArray(mapper, row.getPersonalRecords());
        dto.baselines       = parseJsonbArray(mapper, row.getBaselines());
        dto.muscleCoverage  = parseJsonbArray(mapper, row.getMuscleCoverage());
        dto.findings        = parseJsonbArray(mapper, row.getFindings());
        dto.recalibrations  = parseJsonbArray(mapper, row.getRecalibrations());
        dto.computedAt     = row.getComputedAt();
        return dto;
    }

    /**
     * Deserialize a JSONB string to a list of maps. Returns an empty list
     * on null, blank, or unparseable input — the caller gets a degraded
     * but valid response rather than a 500.
     */
    private static List<Map<String, Object>> parseJsonbArray(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, LIST_MAP_REF);
        } catch (Exception e) {
            return List.of();
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Integer getWeekNumber() { return weekNumber; }
    public LocalDate getWeekStart() { return weekStart; }
    public LocalDate getWeekEnd() { return weekEnd; }
    public String getUserFirstName() { return userFirstName; }
    public Boolean getIsWeekOne() { return isWeekOne; }
    public Integer getSessionsLogged() { return sessionsLogged; }
    public Integer getSessionsGoal() { return sessionsGoal; }
    public BigDecimal getTotalKgVolume() { return totalKgVolume; }
    public Integer getPrCount() { return prCount; }
    public String getVerdict() { return verdict; }
    public List<Map<String, Object>> getPersonalRecords() { return personalRecords; }
    public List<Map<String, Object>> getBaselines() { return baselines; }
    public List<Map<String, Object>> getMuscleCoverage() { return muscleCoverage; }
    public List<Map<String, Object>> getFindings() { return findings; }
    public List<Map<String, Object>> getRecalibrations() { return recalibrations; }
    public Instant getComputedAt() { return computedAt; }
}
