package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.request.HealthConditionsRequest;
import com.fittribe.api.dto.request.UpdateProfileRequest;
import com.fittribe.api.entity.User;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.util.PromptSanitiser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Set<String> VALID_GOALS =
            Set.of("FAT_LOSS", "BUILD_MUSCLE", "ENDURANCE", "GENERAL_FITNESS", "GAIN_WEIGHT");
    private static final Set<String> VALID_LEVELS =
            Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ── GET /api/v1/users/me ──────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> getMe(Authentication auth) {
        User user = resolveUser(auth);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    // ── PUT /api/v1/users/me ──────────────────────────────────────────
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<User>> updateProfile(
            @RequestBody UpdateProfileRequest request,
            Authentication auth) {
        User user = resolveUser(auth);

        if (request.displayName() != null) {
            String trimmed = request.displayName().trim();
            user.setDisplayName(trimmed.substring(0, Math.min(trimmed.length(), 50)));
        }
        if (request.gender() != null)      user.setGender(request.gender());
        if (request.goal() != null) {
            if (!VALID_GOALS.contains(request.goal())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Invalid goal. Must be one of: " + String.join(", ", VALID_GOALS));
            }
            user.setGoal(request.goal());
        }
        if (request.fitnessLevel() != null) {
            if (!VALID_LEVELS.contains(request.fitnessLevel())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Invalid fitnessLevel. Must be one of: " + String.join(", ", VALID_LEVELS));
            }
            user.setFitnessLevel(request.fitnessLevel());
        }
        if (request.weightKg() != null)    user.setWeightKg(request.weightKg());
        if (request.heightCm() != null)    user.setHeightCm(request.heightCm());
        if (request.weeklyGoal() != null)  user.setWeeklyGoal(request.weeklyGoal());
        if (request.healthConditions() != null) {
            user.setHealthConditions(request.healthConditions().toArray(new String[0]));
        }

        return ResponseEntity.ok(ApiResponse.success(userRepository.save(user)));
    }

    // ── PUT /api/v1/users/me/health-conditions ────────────────────────
    @PutMapping("/me/health-conditions")
    public ResponseEntity<ApiResponse<User>> updateHealthConditions(
            @RequestBody HealthConditionsRequest request,
            Authentication auth) {
        User user = resolveUser(auth);

        String[] conditions = (request.conditions() != null)
                ? request.conditions().toArray(new String[0])
                : new String[0];
        user.setHealthConditions(conditions);

        return ResponseEntity.ok(ApiResponse.success(userRepository.save(user)));
    }

    // ── Helper ────────────────────────────────────────────────────────
    private User resolveUser(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
    }
}
