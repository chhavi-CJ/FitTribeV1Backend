package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.ExerciseRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ExerciseController {

    private final ExerciseRepository exerciseRepository;

    public ExerciseController(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
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
}
