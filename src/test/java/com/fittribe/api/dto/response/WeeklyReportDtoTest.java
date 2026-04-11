package com.fittribe.api.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fittribe.api.entity.WeeklyReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WeeklyReportDto#from(WeeklyReport, ObjectMapper)}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Populated JSONB strings deserialize to correctly-sized lists with
 *       the expected field names present in each entry.</li>
 *   <li>{@code "[]"} strings deserialize to empty lists (not null).</li>
 *   <li>Null JSONB strings fall back to empty lists.</li>
 *   <li>Scalar fields copy across without transformation.</li>
 *   <li>A null {@code verdict} is allowed (no OpenAI key case).</li>
 * </ul>
 *
 * Uses a {@link ObjectMapper} with the Java-time module registered so that
 * {@code LocalDate} and {@code Instant} fields serialize correctly in the
 * round-trip assertions. (The production Spring Boot context auto-registers
 * this module; here we wire it manually.)
 */
class WeeklyReportDtoTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    // ── Happy path — all JSONB columns populated ──────────────────────────

    @Test
    void fromWithAllJsonbColumnsPopulated() {
        WeeklyReport row = buildRow();
        row.setPersonalRecords(
                """
                [{"exerciseId":"bench-press","newMaxKg":85,"previousMaxKg":82.5,"reps":6}]
                """);
        row.setBaselines(
                """
                [{"exerciseId":"bench-press","weightKg":85,"reps":6},
                 {"exerciseId":"pull-up","weightKg":0,"reps":8}]
                """);
        row.setMuscleCoverage(
                """
                [{"muscle":"CHEST","sessions":2,"status":"GREEN"},
                 {"muscle":"BACK_LATS","sessions":1,"status":"AMBER"},
                 {"muscle":"SHOULDERS","sessions":0,"status":"RED"},
                 {"muscle":"LEGS_QUADS","sessions":0,"status":"RED"},
                 {"muscle":"HAMSTRINGS","sessions":0,"status":"RED"},
                 {"muscle":"GLUTES","sessions":0,"status":"RED"},
                 {"muscle":"TRICEPS","sessions":2,"status":"GREEN"},
                 {"muscle":"BICEPS","sessions":1,"status":"AMBER"}]
                """);
        row.setFindings(
                """
                [{"ruleId":"low_adherence","severity":"RED","weight":90,"title":"Only 3 of 4 sessions done","detail":"Miss one more and the week is a write-off."},
                 {"ruleId":"missing_muscle","severity":"RED","weight":80,"title":"Legs skipped","detail":"Zero leg sessions this week."},
                 {"ruleId":"single_too_light","severity":"AMBER","weight":60,"title":"Bench below target","detail":"You hit 80 kg vs a 90 kg target."}]
                """);
        row.setRecalibrations(
                """
                [{"exerciseId":"bench-press","oldTargetKg":90,"newTargetKg":82.5,"reason":"Consistently below target"}]
                """);

        WeeklyReportDto dto = WeeklyReportDto.from(row, mapper);

        // Scalar fields
        assertEquals(42L, dto.getId());
        assertEquals(3, dto.getWeekNumber());
        assertEquals(LocalDate.of(2026, 4, 6), dto.getWeekStart());
        assertEquals(LocalDate.of(2026, 4, 13), dto.getWeekEnd());
        assertEquals("Harsh", dto.getUserFirstName());
        assertEquals(Boolean.FALSE, dto.getIsWeekOne());
        assertEquals(3, dto.getSessionsLogged());
        assertEquals(4, dto.getSessionsGoal());
        assertEquals(new BigDecimal("11240.00"), dto.getTotalKgVolume());
        assertEquals(2, dto.getPrCount());
        assertEquals("Solid push week despite a missed session.", dto.getVerdict());

        // JSONB list sizes
        assertEquals(1, dto.getPersonalRecords().size());
        assertEquals(2, dto.getBaselines().size());
        assertEquals(8, dto.getMuscleCoverage().size());
        assertEquals(3, dto.getFindings().size());
        assertEquals(1, dto.getRecalibrations().size());

        // Spot-check field names inside each list entry
        Map<String, Object> pr = dto.getPersonalRecords().get(0);
        assertEquals("bench-press", pr.get("exerciseId"));

        Map<String, Object> tile = dto.getMuscleCoverage().get(0);
        assertTrue(tile.containsKey("muscle"), "muscle_coverage entry must have 'muscle' key");
        assertTrue(tile.containsKey("status"), "muscle_coverage entry must have 'status' key");
        assertTrue(tile.containsKey("sessions"), "muscle_coverage entry must have 'sessions' key");

        Map<String, Object> finding = dto.getFindings().get(0);
        assertEquals("low_adherence", finding.get("ruleId"));
        assertEquals("RED", finding.get("severity"));

        Map<String, Object> recal = dto.getRecalibrations().get(0);
        assertEquals("bench-press", recal.get("exerciseId"));
    }

    // ── Empty arrays ──────────────────────────────────────────────────────

    @Test
    void emptyJsonbArraysDeserializeToEmptyLists() {
        WeeklyReport row = buildRow();
        row.setPersonalRecords("[]");
        row.setBaselines("[]");
        row.setMuscleCoverage("[]");
        row.setFindings("[]");
        row.setRecalibrations("[]");

        WeeklyReportDto dto = WeeklyReportDto.from(row, mapper);

        assertEquals(List.of(), dto.getPersonalRecords());
        assertEquals(List.of(), dto.getBaselines());
        assertEquals(List.of(), dto.getMuscleCoverage());
        assertEquals(List.of(), dto.getFindings());
        assertEquals(List.of(), dto.getRecalibrations());
    }

    // ── Null JSONB strings degrade gracefully ─────────────────────────────

    @Test
    void nullJsonbStringsDeserializeToEmptyLists() {
        WeeklyReport row = buildRow();
        row.setPersonalRecords(null);
        row.setBaselines(null);
        row.setMuscleCoverage(null);
        row.setFindings(null);
        row.setRecalibrations(null);

        WeeklyReportDto dto = WeeklyReportDto.from(row, mapper);

        assertEquals(List.of(), dto.getPersonalRecords(),  "null personal_records → empty list");
        assertEquals(List.of(), dto.getBaselines(),        "null baselines → empty list");
        assertEquals(List.of(), dto.getMuscleCoverage(),   "null muscle_coverage → empty list");
        assertEquals(List.of(), dto.getFindings(),         "null findings → empty list");
        assertEquals(List.of(), dto.getRecalibrations(),   "null recalibrations → empty list");
    }

    // ── Null verdict is allowed ───────────────────────────────────────────

    @Test
    void nullVerdictIsAllowed() {
        WeeklyReport row = buildRow();
        row.setVerdict(null);
        row.setFindings("[]");
        row.setPersonalRecords("[]");
        row.setBaselines("[]");
        row.setMuscleCoverage("[]");
        row.setRecalibrations("[]");

        WeeklyReportDto dto = WeeklyReportDto.from(row, mapper);

        assertNull(dto.getVerdict(), "null verdict must stay null in the DTO");
        assertNotNull(dto.getFindings(), "other fields must still populate");
    }

    // ── Malformed JSONB degrades gracefully ───────────────────────────────

    @Test
    void malformedJsonbDegradestoEmptyList() {
        WeeklyReport row = buildRow();
        row.setFindings("not-valid-json{{{");
        row.setPersonalRecords("[]");
        row.setBaselines("[]");
        row.setMuscleCoverage("[]");
        row.setRecalibrations("[]");
        row.setVerdict(null);

        WeeklyReportDto dto = WeeklyReportDto.from(row, mapper);

        // Should not throw — falls back to empty list
        assertEquals(List.of(), dto.getFindings(),
                "malformed JSONB must degrade to empty list, not throw");
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private static WeeklyReport buildRow() {
        WeeklyReport row = new WeeklyReport();
        row.setId(42L);
        row.setUserId(UUID.fromString("d60d34cf-cbe2-454c-b89e-6c7340e9b88b"));
        row.setWeekNumber(3);
        row.setWeekStart(LocalDate.of(2026, 4, 6));
        row.setWeekEnd(LocalDate.of(2026, 4, 13));
        row.setUserFirstName("Harsh");
        row.setIsWeekOne(false);
        row.setSessionsLogged(3);
        row.setSessionsGoal(4);
        row.setTotalKgVolume(new BigDecimal("11240.00"));
        row.setPrCount(2);
        row.setVerdict("Solid push week despite a missed session.");
        row.setSchemaVersion(1);
        return row;
    }
}
