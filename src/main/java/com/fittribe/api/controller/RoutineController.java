package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.request.SaveRoutineRequest;
import com.fittribe.api.dto.request.SaveRoutineRequest.RoutineExerciseInput;
import com.fittribe.api.dto.response.RoutineResponse;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.SavedRoutine;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.ExerciseRepository;
import com.fittribe.api.repository.SavedRoutineRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/routines")
public class RoutineController {

    private static final Logger log = LoggerFactory.getLogger(RoutineController.class);

    private final SavedRoutineRepository     routineRepo;
    private final WorkoutSessionRepository   sessionRepo;
    private final ExerciseRepository         exerciseRepo;

    public RoutineController(SavedRoutineRepository routineRepo,
                             WorkoutSessionRepository sessionRepo,
                             ExerciseRepository exerciseRepo) {
        this.routineRepo  = routineRepo;
        this.sessionRepo  = sessionRepo;
        this.exerciseRepo = exerciseRepo;
    }

    // ── POST /routines ───────────────────────────────────────────────
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<RoutineResponse>> createRoutine(
            @RequestBody SaveRoutineRequest request,
            Authentication auth) {

        UUID userId = userId(auth);
        validateRequest(request);

        List<Map<String, Object>> exercisesJson = buildExercisesJson(request.exercises());

        SavedRoutine routine = new SavedRoutine();
        routine.setUserId(userId);
        routine.setName(request.name().trim());
        routine.setExercises(exercisesJson);
        routineRepo.save(routine);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toResponse(routine)));
    }

    // ── GET /routines ────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<ApiResponse<List<RoutineResponse>>> listRoutines(Authentication auth) {
        UUID userId = userId(auth);

        List<RoutineResponse> routines = routineRepo
                .findAllByUserSortedByRecent(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(routines));
    }

    // ── GET /routines/{id} ───────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoutineResponse>> getRoutine(
            @PathVariable UUID id,
            Authentication auth) {

        SavedRoutine routine = requireOwned(id, userId(auth));
        return ResponseEntity.ok(ApiResponse.success(toResponse(routine)));
    }

    // ── PUT /routines/{id} ───────────────────────────────────────────
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<RoutineResponse>> updateRoutine(
            @PathVariable UUID id,
            @RequestBody SaveRoutineRequest request,
            Authentication auth) {

        SavedRoutine routine = requireOwned(id, userId(auth));
        validateRequest(request);

        List<Map<String, Object>> exercisesJson = buildExercisesJson(request.exercises());

        routine.setName(request.name().trim());
        routine.setExercises(exercisesJson);
        routineRepo.save(routine);

        return ResponseEntity.ok(ApiResponse.success(toResponse(routine)));
    }

    // ── DELETE /routines/{id} ────────────────────────────────────────
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> deleteRoutine(
            @PathVariable UUID id,
            Authentication auth) {

        SavedRoutine routine = requireOwned(id, userId(auth));
        routineRepo.delete(routine);
        return ResponseEntity.ok(ApiResponse.success(Map.of("deleted", true)));
    }

    // ── POST /routines/{id}/use ──────────────────────────────────────
    @PostMapping("/{id}/use")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> useRoutine(
            @PathVariable UUID id,
            Authentication auth) {

        SavedRoutine routine = requireOwned(id, userId(auth));
        routine.setTimesUsed(routine.getTimesUsed() != null ? routine.getTimesUsed() + 1 : 1);
        routine.setLastUsedAt(Instant.now());
        routineRepo.save(routine);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timesUsed",  routine.getTimesUsed());
        result.put("lastUsedAt", routine.getLastUsedAt());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private UUID userId(Authentication auth) {
        return (UUID) auth.getPrincipal();
    }

    private SavedRoutine requireOwned(UUID routineId, UUID userId) {
        return routineRepo.findByIdAndUserId(routineId, userId)
                .orElseThrow(() -> ApiException.notFound("Routine"));
    }

    private void validateRequest(SaveRoutineRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROUTINE_NAME_REQUIRED",
                    "Routine name is required.");
        }
        if (request.exercises() == null || request.exercises().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROUTINE_EMPTY",
                    "Routine must have at least one exercise.");
        }
    }

    private List<Map<String, Object>> buildExercisesJson(List<RoutineExerciseInput> inputs) {
        List<String> exerciseIds = inputs.stream()
                .map(RoutineExerciseInput::exerciseId)
                .collect(Collectors.toList());
        Map<String, Exercise> exMap = exerciseRepo.findAllById(exerciseIds).stream()
                .collect(Collectors.toMap(Exercise::getId, e -> e));

        List<Map<String, Object>> result = new ArrayList<>();
        for (RoutineExerciseInput input : inputs) {
            Exercise entity = exMap.get(input.exerciseId());
            if (entity == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "EXERCISE_NOT_FOUND",
                        "Exercise not found: " + input.exerciseId());
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("exerciseId",   entity.getId());
            m.put("exerciseName", entity.getName());
            m.put("sets",         input.sets() != null ? input.sets() : 3);
            m.put("muscleGroup",  entity.getMuscleGroup());
            m.put("equipment",    entity.getEquipment());
            m.put("isBodyweight", entity.isBodyweight());
            result.add(m);
        }
        return result;
    }

    private RoutineResponse toResponse(SavedRoutine routine) {
        return new RoutineResponse(
                routine.getId(),
                routine.getName(),
                routine.getExercises(),
                routine.getTimesUsed() != null ? routine.getTimesUsed() : 0,
                routine.getLastUsedAt(),
                routine.getCreatedAt(),
                routine.getUpdatedAt());
    }
}
