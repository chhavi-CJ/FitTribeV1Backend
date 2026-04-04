package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.request.HealthConditionsRequest;
import com.fittribe.api.dto.request.UpdateProfileRequest;
import com.fittribe.api.dto.request.UpdateUserProfileRequest;
import com.fittribe.api.dto.response.UserProfileResponse;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.UserPlan;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.PersonalRecordRepository;
import com.fittribe.api.repository.UserPlanRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.util.PromptSanitiser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Set<String> VALID_GOALS =
            Set.of("FAT_LOSS", "BUILD_MUSCLE", "ENDURANCE", "GENERAL_FITNESS", "GAIN_WEIGHT");
    private static final Set<String> VALID_LEVELS =
            Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");

    private final UserRepository            userRepository;
    private final WorkoutSessionRepository  sessionRepository;
    private final UserPlanRepository        planRepository;
    private final PersonalRecordRepository  prRepository;

    public UserController(UserRepository userRepository,
                          WorkoutSessionRepository sessionRepository,
                          UserPlanRepository planRepository,
                          PersonalRecordRepository prRepository) {
        this.userRepository    = userRepository;
        this.sessionRepository = sessionRepository;
        this.planRepository    = planRepository;
        this.prRepository      = prRepository;
    }

    // ── GET /api/v1/users/me ──────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> getMe(Authentication auth) {
        User user = resolveUser(auth);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    // ── PUT /api/v1/users/me ──────────────────────────────────────────
    @PutMapping("/me")
    @Transactional
    public ResponseEntity<ApiResponse<User>> updateProfile(
            @RequestBody @Valid UpdateProfileRequest request,
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
        if (request.weeklyGoal() != null) {
            // Invalidate current week's plan if goal changed — forces regeneration with correct split
            if (!request.weeklyGoal().equals(user.getWeeklyGoal())) {
                invalidateCurrentWeekPlan(user.getId());
            }
            user.setWeeklyGoal(request.weeklyGoal());
        }
        if (request.healthConditions() != null) {
            user.setHealthConditions(request.healthConditions().toArray(new String[0]));
        }
        if (request.aiContext() != null) {
            String sanitized = request.aiContext()
                    .replaceAll("(?i)(ignore previous|forget your|you are now|system prompt|jailbreak)", "")
                    .trim();
            user.setAiContext(sanitized);
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

    // ── GET /api/v1/users/profile ─────────────────────────────────────
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
        return ResponseEntity.ok(ApiResponse.success(buildProfileResponse(userId, user)));
    }

    // ── PUT /api/v1/users/profile ─────────────────────────────────────
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserProfile(
            @RequestBody @Valid UpdateUserProfileRequest request,
            Authentication auth) {

        UUID userId = (UUID) auth.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));

        if (request.displayName() != null) {
            String trimmed = request.displayName().trim();
            user.setDisplayName(trimmed.substring(0, Math.min(trimmed.length(), 50)));
        }
        if (request.currentWeightKg() != null) user.setWeightKg(request.currentWeightKg());
        if (request.primaryGoal() != null) {
            if (!VALID_GOALS.contains(request.primaryGoal())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Invalid primaryGoal. Must be one of: " + String.join(", ", VALID_GOALS));
            }
            user.setGoal(request.primaryGoal());
        }
        if (request.fitnessLevel() != null) {
            if (!VALID_LEVELS.contains(request.fitnessLevel())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Invalid fitnessLevel. Must be one of: " + String.join(", ", VALID_LEVELS));
            }
            user.setFitnessLevel(request.fitnessLevel());
        }
        // Weekly goal always goes to pending — promoted to live on Monday by scheduler
        if (request.weeklyGoal() != null) user.setPendingWeeklyGoal(request.weeklyGoal());

        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(buildProfileResponse(userId, user)));
    }

    // ── Helper ────────────────────────────────────────────────────────
    private User resolveUser(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
    }

    /**
     * Deletes the user_plans row for the current week so generatePlan()
     * will regenerate it with the correct split on next app open.
     */
    private void invalidateCurrentWeekPlan(UUID userId) {
        LocalDate monday = LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Optional<UserPlan> existing = planRepository.findByUserIdAndWeekStartDate(userId, monday);
        existing.ifPresent(planRepository::delete);
    }

    private UserProfileResponse buildProfileResponse(UUID userId, User user) {
        LocalDate monday         = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
        Instant weekFrom         = monday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant weekTo           = monday.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant();

        int completedThisWeek = sessionRepository.countByUserIdAndStatusAndFinishedAtBetween(
                userId, "COMPLETED", weekFrom, weekTo);
        int sessionsTotal = sessionRepository.countByUserIdAndStatus(userId, "COMPLETED");
        int prsTotal      = prRepository.countByUserId(userId);

        return new UserProfileResponse(
                userId,
                user.getDisplayName(),
                user.getFitnessLevel(),
                user.getGoal(),
                user.getWeightKg(),
                user.getWeeklyGoal() != null ? user.getWeeklyGoal() : 4,
                user.getPendingWeeklyGoal(),
                completedThisWeek,
                user.getStreak() != null ? user.getStreak() : 0,
                user.getMaxStreakEver() != null ? user.getMaxStreakEver() : 0,
                sessionsTotal,
                prsTotal,
                user.getCoins() != null ? user.getCoins() : 0,
                0,       // streakFreezeBalance — placeholder
                user.getRank() != null ? user.getRank() : "ROOKIE",
                true,    // notificationsEnabled — placeholder
                true,    // showInLeaderboard — placeholder
                user.getWeightUnit() != null ? user.getWeightUnit() : "KG");
    }
}
