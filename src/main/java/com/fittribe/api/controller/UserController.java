package com.fittribe.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.request.HealthConditionsRequest;
import com.fittribe.api.dto.request.UpdateProfileRequest;
import com.fittribe.api.dto.request.UpdateSettingsRequest;
import com.fittribe.api.dto.request.UpdateUserProfileRequest;
import com.fittribe.api.dto.response.UserExerciseBestDto;
import com.fittribe.api.dto.response.UserProfileResponse;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.UserPlan;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.healthcondition.HealthConditionNormalizer;
import com.fittribe.api.repository.PrEventRepository;
import com.fittribe.api.repository.UserDayStatusRepository;
import com.fittribe.api.repository.UserExerciseBestsRepository;
import com.fittribe.api.repository.UserPlanRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.service.AnalyticsService;
import com.fittribe.api.service.UserDeletionService;
import com.fittribe.api.service.RankService;
import com.fittribe.api.util.PromptSanitiser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.fittribe.api.util.Zones;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Set<String> VALID_GOALS =
            Set.of("FAT_LOSS", "BUILD_MUSCLE", "ENDURANCE", "GENERAL_FITNESS", "GAIN_WEIGHT");
    private static final Set<String> VALID_LEVELS =
            Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserController.class);

    private final UserRepository              userRepository;
    private final WorkoutSessionRepository    sessionRepository;
    private final UserPlanRepository          planRepository;
    private final UserExerciseBestsRepository bestsRepository;
    private final PrEventRepository           prEventRepository;
    private final UserDayStatusRepository     userDayStatusRepository;
    private final ObjectMapper                objectMapper;
    private final UserDeletionService         userDeletionService;
    private final AnalyticsService            analyticsService;
    private final JdbcTemplate                jdbcTemplate;

    public UserController(UserRepository userRepository,
                          WorkoutSessionRepository sessionRepository,
                          UserPlanRepository planRepository,
                          UserExerciseBestsRepository bestsRepository,
                          PrEventRepository prEventRepository,
                          UserDayStatusRepository userDayStatusRepository,
                          ObjectMapper objectMapper,
                          UserDeletionService userDeletionService,
                          AnalyticsService analyticsService,
                          JdbcTemplate jdbcTemplate) {
        this.userRepository       = userRepository;
        this.sessionRepository    = sessionRepository;
        this.planRepository       = planRepository;
        this.bestsRepository      = bestsRepository;
        this.prEventRepository    = prEventRepository;
        this.userDayStatusRepository = userDayStatusRepository;
        this.objectMapper         = objectMapper;
        this.userDeletionService  = userDeletionService;
        this.analyticsService     = analyticsService;
        this.jdbcTemplate         = jdbcTemplate;
    }

    // ── GET /api/v1/users/me/exercise-bests ─────────────────────────────
    @GetMapping("/me/exercise-bests")
    public ResponseEntity<ApiResponse<List<UserExerciseBestDto>>> getExerciseBests(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        List<UserExerciseBestDto> bests = bestsRepository.findByUserId(userId).stream()
                .map(b -> new UserExerciseBestDto(
                        b.getExerciseId(),
                        b.getBestWtKg() != null ? b.getBestWtKg().doubleValue() : null,
                        b.getBestReps(),
                        b.getRepsAtBestWt()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(bests));
    }

    // ── GET /api/v1/users/me ──────────────────────────────────────────
    @GetMapping("/me")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMe(Authentication auth) {
        User user = resolveUser(auth);
        UUID userId = user.getId();

        int completedThisWeek          = sessionRepository.countCompletedThisWeekByStartedAt(userId);
        List<String> workoutDatesThisWeek = sessionRepository.findWorkoutDatesThisWeekByStartedAt(userId);

        LocalDate today       = Zones.fitnessDayNow();

        // Get day statuses (REST, SICK, TRAVELLING, BUSY) for this week
        LocalDate mondayOfThisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sundayOfThisWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<com.fittribe.api.entity.UserDayStatus> weekStatuses = userDayStatusRepository.findByIdUserIdAndIdDateBetween(userId, mondayOfThisWeek, sundayOfThisWeek);
        Map<String, String> dayStatusesThisWeek = weekStatuses.stream()
                .filter(s -> !"READY".equals(s.getStatus()))
                .collect(Collectors.toMap(
                        s -> s.getId().getDate().toString(),
                        com.fittribe.api.entity.UserDayStatus::getStatus
                ));

        Instant startOfDay    = Zones.fitnessDayStart(today);
        Instant endOfDay      = Zones.fitnessDayStart(today.plusDays(1));
        boolean workoutCompletedToday = sessionRepository.existsByUserIdAndStatusAndFinishedAtBetween(
                userId, "COMPLETED", startOfDay, endOfDay);

        int trainingDaysTotal = sessionRepository.countDistinctTrainingDays(userId);
        String currentRank    = RankService.rankFor(trainingDaysTotal);

        Map<String, Object> response = objectMapper.convertValue(user, Map.class);
        response.put("completedThisWeek",    completedThisWeek);
        response.put("workoutDatesThisWeek", workoutDatesThisWeek);
        response.put("dayStatusesThisWeek",  dayStatusesThisWeek);
        response.put("workoutCompletedToday", workoutCompletedToday);
        response.put("trainingDaysTotal",    trainingDaysTotal);
        response.put("currentRank",          currentRank);
        response.put("nextRankName",         RankService.nextRank(currentRank));
        response.put("daysToNextRank",       RankService.daysToNext(trainingDaysTotal));

        return ResponseEntity.ok(ApiResponse.success(response));
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
            int wg = request.weeklyGoal();
            if (wg < 1 || wg > 6) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "weekly_goal must be between 1 and 6");
            }
            // Invalidate current week's plan if goal changed — forces regeneration with correct split
            if (!request.weeklyGoal().equals(user.getWeeklyGoal())) {
                invalidateCurrentWeekPlan(user.getId());
            }
            user.setWeeklyGoal(request.weeklyGoal());
        }
        if (request.healthConditions() != null) {
            user.setHealthConditions(HealthConditionNormalizer.normalize(
                    request.healthConditions().toArray(new String[0])));
        }
        if (request.aiContext() != null) {
            String sanitized = request.aiContext()
                    .replaceAll("(?i)(ignore previous|forget your|you are now|system prompt|jailbreak)", "")
                    .trim();
            user.setAiContext(sanitized);
        }

        // Onboarding-completion gate. Flip onboarding_complete true ONLY on the
        // final onboarding submit — i.e. the user is still incomplete AND this
        // single payload carries all six required onboarding fields. This is
        // deliberately scoped so that later partial profile edits (e.g. just
        // changing weeklyGoal or aiContext) never toggle the flag back, and a
        // user who somehow reaches /users/me with a partial body stays gated.
        boolean completingOnboarding = false;
        if (!Boolean.TRUE.equals(user.getOnboardingComplete())
                && request.displayName()  != null
                && request.gender()       != null
                && request.goal()         != null
                && request.fitnessLevel() != null
                && request.heightCm()     != null
                && request.weightKg()     != null) {
            user.setOnboardingComplete(true);
            completingOnboarding = true;
        }

        User saved = userRepository.save(user);

        // Track onboarding completion
        if (completingOnboarding) {
            analyticsService.track(user.getId(), "onboarding_completed",
                    Map.of(
                            "fitness_level", request.fitnessLevel() != null ? request.fitnessLevel() : "",
                            "gym_days_selected", request.weeklyGoal() != null ? request.weeklyGoal().intValue() : 0
                    ));
        }

        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    // ── PUT /api/v1/users/settings ────────────────────────────────────
    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateSettings(
            @RequestBody UpdateSettingsRequest request,
            Authentication auth) {
        User user = resolveUser(auth);

        if (request.notificationsEnabled() != null)
            user.setNotificationsEnabled(request.notificationsEnabled());
        if (request.showInLeaderboard() != null)
            user.setShowInLeaderboard(request.showInLeaderboard());
        if (request.autoFreezeEnabled() != null)
            user.setAutoFreezeEnabled(request.autoFreezeEnabled());

        userRepository.save(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notificationsEnabled", user.getNotificationsEnabled());
        result.put("showInLeaderboard",    user.getShowInLeaderboard());
        result.put("autoFreezeEnabled",    user.getAutoFreezeEnabled());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── GET /api/v1/users/health-conditions ──────────────────────────
    @GetMapping("/health-conditions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealthConditions(Authentication auth) {
        User user = resolveUser(auth);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conditions",
                HealthConditionNormalizer.toShortIds(user.getHealthConditions()));
        result.put("gender", user.getGender());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── PUT /api/v1/users/health-conditions ──────────────────────────
    @PutMapping("/health-conditions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateHealthConditionsV2(
            @RequestBody HealthConditionsRequest request,
            Authentication auth) {
        User user = resolveUser(auth);

        String[] canonical = HealthConditionNormalizer.normalize(
                request.conditions() != null
                        ? request.conditions().toArray(new String[0])
                        : new String[0]);
        user.setHealthConditions(canonical);
        userRepository.save(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conditions", HealthConditionNormalizer.toShortIds(canonical));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── DELETE /api/v1/users/account ─────────────────────────────────
    @DeleteMapping("/account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteAccount(Authentication auth) {
        User user = resolveUser(auth);
        UUID   userId      = user.getId();
        String firebaseUid = user.getFirebaseUid();   // capture BEFORE delete

        // 1. Postgres hard-delete in its own transaction (commits on return).
        userDeletionService.deletePostgresData(user);

        // 2. Firebase delete only AFTER Postgres has committed. Soft-fail
        //    inside the service: a Firebase failure never 5xxs the user.
        userDeletionService.deleteFirebaseUser(userId, firebaseUid);

        return ResponseEntity.ok(ApiResponse.success(Map.of("deleted", true)));
    }

    // ── GET /api/v1/users/profile ─────────────────────────────────────
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
        return ResponseEntity.ok(ApiResponse.success(buildProfileResponse(userId, user)));
    }

    // ── GET /api/v1/users/profile/full ────────────────────────────────
    @GetMapping("/profile/full")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProfileFull(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));

        // Build composite response with profile, health conditions, and achievements
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("profile", buildProfileResponse(userId, user));

        // Health conditions
        Map<String, Object> healthConditions = new LinkedHashMap<>();
        healthConditions.put("conditions",
                HealthConditionNormalizer.toShortIds(user.getHealthConditions()));
        healthConditions.put("gender", user.getGender());
        response.put("healthConditions", healthConditions);

        // Achievements
        response.put("achievements", buildAchievements(userId));

        return ResponseEntity.ok(ApiResponse.success(response));
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
        if (request.weeklyGoal() != null) {
            int wg = request.weeklyGoal();
            if (wg < 1 || wg > 6) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "weekly_goal must be between 1 and 6");
            }
            user.setPendingWeeklyGoal(request.weeklyGoal());
        }

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
        LocalDate monday = LocalDate.now(Zones.APP_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Optional<UserPlan> existing = planRepository.findByUserIdAndWeekStartDate(userId, monday);
        existing.ifPresent(planRepository::delete);
    }

    private UserProfileResponse buildProfileResponse(UUID userId, User user) {
        LocalDate monday         = LocalDate.now(Zones.APP_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant weekFrom         = monday.atStartOfDay(Zones.APP_ZONE).toInstant();
        Instant weekTo           = monday.plusDays(7).atStartOfDay(Zones.APP_ZONE).toInstant();

        // Single round-trip for the 3 workout_sessions counts (was 3 separate queries)
        var counts = sessionRepository.getProfileCounts(userId, weekFrom, weekTo);
        int completedThisWeek = counts.getCompletedThisWeek();
        int sessionsTotal     = counts.getSessionsTotal();
        int trainingDaysTotal = counts.getTrainingDaysTotal();
        int prsTotal          = (int) prEventRepository.countDistinctPrExercisesByUserId(userId);
        String currentRank    = RankService.rankFor(trainingDaysTotal);
        String nextRankName   = RankService.nextRank(currentRank);
        int daysToNextRank    = RankService.daysToNext(trainingDaysTotal);

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
                user.getPurchasedFreezeBalance() != null ? user.getPurchasedFreezeBalance() : 0,
                Boolean.TRUE.equals(user.getAutoFreezeEnabled()),
                currentRank,
                Boolean.TRUE.equals(user.getNotificationsEnabled()),
                Boolean.TRUE.equals(user.getShowInLeaderboard()),
                user.getWeightUnit() != null ? user.getWeightUnit() : "KG",
                trainingDaysTotal,
                currentRank,
                nextRankName,
                daysToNextRank);
    }

    private List<Map<String, Object>> buildAchievements(UUID userId) {
        List<Map<String, Object>> result = new ArrayList<>();

        // ── Achievement 1: Streak ─────────────────────────────────────
        Map<String, Object> streakRow = jdbcTemplate.queryForMap(
                "SELECT streak, max_streak_ever FROM users WHERE id = ?", userId);
        int currentStreak = toInt(streakRow.get("streak"));
        int maxStreakEver  = toInt(streakRow.get("max_streak_ever"));

        if (currentStreak > 0) {
            String subtitle = currentStreak >= maxStreakEver
                    ? "Personal best — keep going"
                    : "Best was " + maxStreakEver + " days";
            result.add(achievement("STREAK",
                    currentStreak + "-day streak",
                    subtitle));
        }

        // ── Achievement 2: PRs this month ─────────────────────────────
        List<String> prNames = jdbcTemplate.queryForList(
                "SELECT DISTINCT e.name FROM pr_events pe " +
                "JOIN exercises e ON e.id = pe.exercise_id " +
                "WHERE pe.user_id = ? " +
                "  AND pe.superseded_at IS NULL " +
                "  AND pe.created_at >= DATE_TRUNC('month', NOW())",
                String.class, userId);

        int prCount = prNames.size();
        if (prCount > 0) {
            List<String> first3 = prNames.stream().limit(3).collect(Collectors.toList());
            String names = String.join(", ", first3);
            if (prCount > 3) names += " and more";
            result.add(achievement("PR",
                    prCount + " PR" + (prCount > 1 ? "s" : "") + " this month",
                    names));
        }

        // ── Achievement 3: Weekly goal consistency ────────────────────
        int weeklyGoal = toInt(jdbcTemplate.queryForObject(
                "SELECT COALESCE(weekly_goal, 4) FROM users WHERE id = ?",
                Integer.class, userId));

        int consecutive = 0;
        LocalDate todayMonday = LocalDate.now(Zones.APP_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int w = 1; w <= 12; w++) {
            LocalDate weekStart = todayMonday.minusWeeks(w);
            LocalDate weekEnd   = weekStart.plusDays(7);
            java.sql.Timestamp from = java.sql.Timestamp.from(weekStart.atStartOfDay(Zones.APP_ZONE).toInstant());
            java.sql.Timestamp to   = java.sql.Timestamp.from(weekEnd.atStartOfDay(Zones.APP_ZONE).toInstant());

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workout_sessions " +
                    "WHERE user_id = ? AND status = 'COMPLETED' " +
                    "  AND finished_at >= ? AND finished_at < ?",
                    Integer.class, userId, from, to);

            if (count != null && count >= weeklyGoal) {
                consecutive++;
            } else {
                break;
            }
        }

        if (consecutive > 0) {
            result.add(achievement("GOAL",
                    "Hit " + weeklyGoal + "-day goal",
                    consecutive + " week" + (consecutive > 1 ? "s" : "") + " in a row"));
        }

        return result;
    }

    private Map<String, Object> achievement(String iconType, String title, String subtitle) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type",     iconType);
        m.put("title",    title);
        m.put("subtitle", subtitle);
        m.put("iconType", iconType);
        return m;
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }
}
