package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.service.WeeklyReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/weekly-report")
public class WeeklyReportController {

    private final WeeklyReportService weeklyReportService;

    public WeeklyReportController(WeeklyReportService weeklyReportService) {
        this.weeklyReportService = weeklyReportService;
    }

    /** GET /api/v1/weekly-report/current */
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<Map<String, Object>>> current(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        Map<String, Object> report = weeklyReportService.getCurrentReport(userId);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /** GET /api/v1/weekly-report/history */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> history(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        List<Map<String, Object>> reports = weeklyReportService.getHistory(userId);
        return ResponseEntity.ok(ApiResponse.success(reports));
    }

    /** GET /api/v1/weekly-report/{weekNumber} */
    @GetMapping("/{weekNumber}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> byWeekNumber(
            @PathVariable int weekNumber,
            Authentication auth) {
        if (weekNumber < 1 || weekNumber > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEEK",
                    "weekNumber must be between 1 and 200.");
        }
        UUID userId = (UUID) auth.getPrincipal();
        Map<String, Object> report = weeklyReportService.getByWeekNumber(userId, weekNumber);
        return ResponseEntity.ok(ApiResponse.success(report));
    }
}
