package com.fittribe.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.response.WeeklyReportDto;
import com.fittribe.api.entity.WeeklyReport;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.WeeklyReportRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * GET endpoints for the Wynners weekly report (A4.1 / A4.2).
 *
 * <p>This controller reads from the {@code weekly_reports} table written
 * by {@code WeeklyReportComputer} — the pre-computed, structured rows
 * produced by the Sunday-night job pipeline. It is intentionally separate
 * from {@link WeeklyReportController}, which computes reports on-demand
 * from raw session / set-log data and serves the existing frontend.
 *
 * <h3>Path prefix</h3>
 * {@code /api/v1/weekly-reports} (plural) avoids a Spring MVC ambiguous-
 * handler conflict: the legacy controller maps
 * {@code GET /api/v1/weekly-report/{weekNumber}} (singular, int path
 * variable) and a new {@code GET /api/v1/weekly-report/{id}} (Long path
 * variable) on the same template would cause a startup error.
 *
 * <h3>Auth</h3>
 * Both endpoints are covered by {@code anyRequest().authenticated()} in
 * {@code SecurityConfig} — no change to {@code SecurityConfig} needed.
 * User identity is extracted from the JWT via
 * {@code (UUID) auth.getPrincipal()}, matching the pattern used by all
 * other authenticated controllers in this package.
 *
 * <h3>403 vs 404 for wrong-user access</h3>
 * {@code GET /{id}} returns 403 rather than 404 when the report exists
 * but belongs to a different user. Returning 404 would obscure the fact
 * that the resource exists at all (which is fine for security theatre),
 * but 403 is more useful for debugging — an admin can tell at a glance
 * that the ID is valid and the caller just lacks access. The ID column
 * is a BIGSERIAL, so there is no meaningful enumeration risk.
 */
@RestController
@RequestMapping("/api/v1/weekly-reports")
public class WynnersWeeklyReportController {

    private final WeeklyReportRepository weeklyReportRepo;
    private final ObjectMapper objectMapper;

    public WynnersWeeklyReportController(WeeklyReportRepository weeklyReportRepo,
                                         ObjectMapper objectMapper) {
        this.weeklyReportRepo = weeklyReportRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Return the most recent weekly report for the authenticated user.
     *
     * @return {@code 200 WeeklyReportDto} when a report exists;
     *         {@code 404 NOT_FOUND} when no report has been computed yet.
     */
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<WeeklyReportDto>> latest(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        WeeklyReport row = weeklyReportRepo
                .findTopByUserIdOrderByWeekStartDesc(userId)
                .orElseThrow(() -> ApiException.notFound("WeeklyReport"));
        return ResponseEntity.ok(ApiResponse.success(WeeklyReportDto.from(row, objectMapper)));
    }

    /**
     * Return a specific historical report by its BIGSERIAL id.
     *
     * @param id the {@code weekly_reports.id} value
     * @return {@code 200 WeeklyReportDto} when the report exists and belongs
     *         to the authenticated user; {@code 403 FORBIDDEN} when the report
     *         exists but belongs to a different user; {@code 404 NOT_FOUND}
     *         when no row with that id exists.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WeeklyReportDto>> byId(
            @PathVariable Long id,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        WeeklyReport row = weeklyReportRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("WeeklyReport"));
        if (!userId.equals(row.getUserId())) {
            throw ApiException.forbidden();
        }
        return ResponseEntity.ok(ApiResponse.success(WeeklyReportDto.from(row, objectMapper)));
    }
}
