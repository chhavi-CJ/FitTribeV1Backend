package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.exercise.LastLoggedSetDto;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.ExerciseRepository;
import com.fittribe.api.repository.SetLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ExerciseController {

    private final ExerciseRepository exerciseRepository;
    private final SetLogRepository setLogRepository;

    public ExerciseController(ExerciseRepository exerciseRepository,
                              SetLogRepository setLogRepository) {
        this.exerciseRepository = exerciseRepository;
        this.setLogRepository = setLogRepository;
    }

    /** GET /api/v1/exercises — no auth required */
    @GetMapping("/exercises")
    public ResponseEntity<ApiResponse<List<Exercise>>> getExercises() {
        List<Exercise> exercises = exerciseRepository.findAllByOrderByMuscleGroupAsc();
        return ResponseEntity.ok(ApiResponse.success(exercises));
    }

    /** GET /api/v1/exercises/{id} — full detail for one exercise */
    @GetMapping("/exercises/{id}")
    public ResponseEntity<ApiResponse<Exercise>> getExercise(@PathVariable String id) {
        Exercise exercise = exerciseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Exercise"));
        return ResponseEntity.ok(ApiResponse.success(exercise));
    }

    /**
     * GET /api/v1/exercises/last-logged — authenticated
     *
     * Returns a map of exercise_id -> last logged set (weight, reps,
     * timestamp) for this user across all COMPLETED sessions.
     *
     * Used by the frontend on the active workout screen to pre-fill
     * weight + reps with the user's most recent values, instead of
     * showing empty defaults. Massive friction reduction for users
     * who tend to lift the same weights week over week.
     *
     * Response shape:
     * {
     *   "success": true,
     *   "data": {
     *     "bench-press":  { "lastKg": 50.0, "lastReps": 10, "lastLoggedAt": "..." },
     *     "dumbbell-row": { "lastKg": 7.5,  "lastReps": 11, "lastLoggedAt": "..." }
     *   }
     * }
     *
     * For exercises the user has never logged, the key is simply absent
     * from the map (no null entries). Frontend falls back to AI suggested
     * weights or zeros in that case.
     */
    @GetMapping("/exercises/last-logged")
    public ResponseEntity<ApiResponse<Map<String, LastLoggedSetDto>>> getLastLogged(
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        List<Object[]> rows = setLogRepository.findLastLoggedPerExerciseForUser(userId);
        Map<String, LastLoggedSetDto> result = new HashMap<>();
        for (Object[] row : rows) {
            String exerciseId    = (String) row[0];
            BigDecimal weightKg  = (BigDecimal) row[1];
            Integer reps         = (Integer) row[2];
            // logged_at (timestamptz) comes back from JDBC as java.time.Instant
            // (current pgjdbc), not java.sql.Timestamp. The original
            // (java.sql.Timestamp) cast was wrong in every environment — it
            // only never threw locally because no test or curl exercised this
            // controller path before deploy; the first real HTTP call on
            // staging 500'd with ClassCastException. Handle Instant / Timestamp /
            // OffsetDateTime defensively so any driver or PG version works.
            Object tsRaw = row[3];
            Instant loggedAt;
            if (tsRaw instanceof Instant i) {
                loggedAt = i;
            } else if (tsRaw instanceof java.sql.Timestamp ts) {
                loggedAt = ts.toInstant();
            } else if (tsRaw instanceof java.time.OffsetDateTime odt) {
                loggedAt = odt.toInstant();
            } else if (tsRaw == null) {
                loggedAt = null;
            } else {
                throw new IllegalStateException(
                        "Unexpected type for logged_at: " + tsRaw.getClass().getName());
            }
            result.put(exerciseId, new LastLoggedSetDto(weightKg, reps, loggedAt));
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
