package com.fittribe.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Weekly statistics endpoint for authenticated users.
 *
 * <p>Computes current-week totals (Monday–Sunday, IST) from raw session data:
 * total volume logged, total sets performed, sessions completed.
 *
 * <h3>Auth</h3>
 * All endpoints require authentication. User identity is extracted from the
 * JWT via {@code (UUID) auth.getPrincipal()}, matching the pattern used by
 * {@link WynnersWeeklyReportController}.
 *
 * <h3>Week window</h3>
 * Week is defined as Monday 00:00 UTC to Sunday 23:59 UTC in IST timezone
 * (Asia/Kolkata). The Monday is computed as the most recent Monday on or
 * before today in IST using {@code TemporalAdjusters.previousOrSame()}.
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private static final Logger log = LoggerFactory.getLogger(StatsController.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final WorkoutSessionRepository sessionRepo;
    private final ObjectMapper objectMapper;

    public StatsController(WorkoutSessionRepository sessionRepo,
                           ObjectMapper objectMapper) {
        this.sessionRepo = sessionRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Get current-week totals for the authenticated user.
     *
     * <p>Returns volume (kg), set count, and session count for all COMPLETED
     * sessions in the current ISO week (Monday–Sunday, IST).
     *
     * @return {@code 200} with totals. Returns zero totals (not 404) if the
     *         user has no completed sessions this week.
     */
    @GetMapping("/week")
    public ResponseEntity<ApiResponse<WeekStatsDto>> week(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();

        // Compute week boundaries in IST
        LocalDate mondayIst = LocalDate.now(IST)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sundayIst = mondayIst.plusDays(6);

        // Convert to UTC Instants for database query
        Instant weekStart = mondayIst.atStartOfDay(IST).toInstant();
        Instant weekEnd = sundayIst.atStartOfDay(IST).plusSeconds(86399).toInstant();

        // Query: all COMPLETED sessions in this week
        List<WorkoutSession> sessions = sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                userId, "COMPLETED", weekStart, weekEnd);

        // Aggregate
        BigDecimal totalVolume = BigDecimal.ZERO;
        int totalSets = 0;

        for (WorkoutSession session : sessions) {
            // Sum volume
            if (session.getTotalVolumeKg() != null) {
                totalVolume = totalVolume.add(session.getTotalVolumeKg());
            }

            // Count sets from exercises JSONB
            if (session.getExercises() != null && !session.getExercises().isBlank()) {
                try {
                    List<Map<String, Object>> exercises = objectMapper.readValue(
                            session.getExercises(),
                            objectMapper.getTypeFactory()
                                    .constructCollectionType(List.class, Map.class));
                    for (Map<String, Object> exercise : exercises) {
                        Object setsObj = exercise.get("sets");
                        if (setsObj instanceof List) {
                            totalSets += ((List<?>) setsObj).size();
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse exercises JSONB for session {} — skipping set count",
                            session.getId(), e);
                }
            }
        }

        WeekStatsDto stats = new WeekStatsDto(
                totalVolume,
                totalSets,
                sessions.size()
        );

        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * Weekly statistics DTO.
     */
    public record WeekStatsDto(BigDecimal totalVolumeKg, int totalSets, int sessionsCount) {}
}
