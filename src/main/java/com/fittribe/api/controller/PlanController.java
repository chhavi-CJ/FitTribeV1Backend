package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.entity.UserPlan;
import com.fittribe.api.service.PlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plan")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    /** POST /api/v1/plan/generate — generate (or return existing) weekly plan */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generate(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        UserPlan plan = planService.generatePlan(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "planId",        plan.getPlanId(),
                "weekNumber",    plan.getWeekNumber(),
                "weekStartDate", plan.getWeekStartDate().toString(),
                "generatedAt",   plan.getGeneratedAt() != null ? plan.getGeneratedAt().toString() : null
        )));
    }

    /** GET /api/v1/plan/today — today's workout or REST day */
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<Map<String, Object>>> today(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        Map<String, Object> result = planService.getTodaysPlan(userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** GET /api/v1/plan/week — full 7-day plan for the current week */
    @GetMapping("/week")
    public ResponseEntity<ApiResponse<Map<String, Object>>> week(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        Map<String, Object> result = planService.getWeekPlan(userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** POST /api/v1/plan/today/generate — generate today's AI workout */
    @PostMapping("/today/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateToday(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        Map<String, Object> result = planService.generateTodaysPlan(userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /** POST /api/v1/plan/today/status — set today's status */
    @PostMapping("/today/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setStatus(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        String status = body.get("status");
        if (status == null || !List.of("REST","TRAVELLING","BUSY","SICK").contains(status)) {
            @SuppressWarnings("unchecked")
            ApiResponse<Map<String, Object>> err = (ApiResponse<Map<String, Object>>)
                    (ApiResponse<?>) ApiResponse.error(
                            "status must be REST, TRAVELLING, BUSY or SICK",
                            "VALIDATION_ERROR");
            return ResponseEntity.badRequest().body(err);
        }
        Map<String, Object> result = planService.setTodayStatus(userId, status);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
