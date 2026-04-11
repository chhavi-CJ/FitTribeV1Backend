package com.fittribe.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.response.WeeklyReportDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Manual integration test for {@link WynnersWeeklyReportController}
 * against the live Railway Postgres database.
 *
 * <p>Calls {@code GET /api/v1/weekly-reports/latest} for Harsh
 * ({@code d60d34cf-cbe2-454c-b89e-6c7340e9b88b}) by invoking the controller
 * bean directly with a real {@link UsernamePasswordAuthenticationToken},
 * then pretty-prints the full response JSON. This exercises the complete
 * path: Spring bean wiring → repository query → JSONB deserialization →
 * DTO serialization, without an HTTP network hop.
 *
 * <p>Requires a weekly report to exist in the DB for Harsh (written by
 * {@link com.fittribe.api.weeklyreport.WeeklyReportComputer} — run
 * {@code WeeklyReportEndToEndManualIT} first if the table is empty).
 *
 * <h3>How to run</h3>
 * <pre>
 * export $(cat .env | xargs)
 * mvn test \
 *   -Dtest=WynnersWeeklyReportControllerManualIT \
 *   -Dfittribe.manualTest=true \
 *   -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "fittribe.manualTest", matches = "true")
class WynnersWeeklyReportControllerManualIT {

    /** Harsh's user ID — must have at least one row in weekly_reports. */
    private static final UUID HARSH_ID =
            UUID.fromString("d60d34cf-cbe2-454c-b89e-6c7340e9b88b");

    @Autowired
    private WynnersWeeklyReportController controller;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void latestForHarsh() throws Exception {
        // Mirrors what JwtAuthFilter puts into the SecurityContext
        Authentication auth =
                new UsernamePasswordAuthenticationToken(HARSH_ID, null, List.of());

        ResponseEntity<ApiResponse<WeeklyReportDto>> response = controller.latest(auth);

        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Expected 200 — does Harsh have a row in weekly_reports?");
        assertNotNull(response.getBody());
        WeeklyReportDto dto = response.getBody().getData();
        assertNotNull(dto, "response body data must not be null");

        // ── Pretty-print the full response ────────────────────────────────
        System.out.println("=============== GET /api/v1/weekly-reports/latest (Harsh) ===============");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(dto));
        System.out.println("==========================================================================");

        // ── Summary line ──────────────────────────────────────────────────
        System.out.println("weekNumber      = " + dto.getWeekNumber());
        System.out.println("weekStart       = " + dto.getWeekStart());
        System.out.println("sessionsLogged  = " + dto.getSessionsLogged()
                + " / " + dto.getSessionsGoal());
        System.out.println("prCount         = " + dto.getPrCount());
        System.out.println("verdict         = " + (dto.getVerdict() == null
                ? "<null — no OpenAI key>" : dto.getVerdict()));
        System.out.println("findings count  = " + dto.getFindings().size());
        System.out.println("muscleCoverage  = " + dto.getMuscleCoverage().size() + " tiles");
        System.out.println("recalibrations  = " + dto.getRecalibrations().size());
    }
}
