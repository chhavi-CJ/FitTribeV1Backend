package com.fittribe.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.request.ExerciseLogRequest;
import com.fittribe.api.dto.request.FinishSessionRequest;
import com.fittribe.api.dto.request.LogSetRequest;
import com.fittribe.api.dto.request.SetLogRequest;
import com.fittribe.api.dto.request.StartSessionRequest;
import com.fittribe.api.dto.request.SwapExerciseRequest;
import com.fittribe.api.dto.response.FinishSessionResponse;
import com.fittribe.api.dto.response.LogSetResponse;
import com.fittribe.api.dto.response.SessionHistoryItem;
import com.fittribe.api.dto.response.StartSessionResponse;
import com.fittribe.api.dto.response.TodaySessionResponse;
import com.fittribe.api.dto.request.SessionFeedbackRequest;
import com.fittribe.api.entity.CoinTransaction;
import com.fittribe.api.entity.SessionFeedback;
import com.fittribe.api.entity.SetLog;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.CoinTransactionRepository;
import com.fittribe.api.repository.PersonalRecordRepository;
import com.fittribe.api.repository.SessionFeedbackRepository;
import com.fittribe.api.repository.SetLogRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.service.AiService;
import com.fittribe.api.service.CoinService;
import com.fittribe.api.service.RankService;
import com.fittribe.api.service.WeeklyReportService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private static final int COOLDOWN_HOURS    = 8;
    private static final int COINS_PER_SESSION = 10;

    private final WorkoutSessionRepository  sessionRepo;
    private final SetLogRepository          setLogRepo;
    private final UserRepository            userRepo;
    private final CoinTransactionRepository coinRepo;
    private final SessionFeedbackRepository feedbackRepo;
    private final AiService                 aiService;
    private final WeeklyReportService       weeklyReportService;
    private final ObjectMapper              objectMapper;
    private final PersonalRecordRepository  prRepo;
    private final RankService               rankService;
    private final CoinService               coinService;

    public SessionController(WorkoutSessionRepository sessionRepo,
                             SetLogRepository setLogRepo,
                             UserRepository userRepo,
                             CoinTransactionRepository coinRepo,
                             SessionFeedbackRepository feedbackRepo,
                             AiService aiService,
                             WeeklyReportService weeklyReportService,
                             ObjectMapper objectMapper,
                             PersonalRecordRepository prRepo,
                             RankService rankService,
                             CoinService coinService) {
        this.sessionRepo         = sessionRepo;
        this.setLogRepo          = setLogRepo;
        this.userRepo            = userRepo;
        this.coinRepo            = coinRepo;
        this.feedbackRepo        = feedbackRepo;
        this.aiService           = aiService;
        this.weeklyReportService = weeklyReportService;
        this.objectMapper        = objectMapper;
        this.prRepo              = prRepo;
        this.rankService         = rankService;
        this.coinService         = coinService;
    }

    // ── POST /sessions/start ──────────────────────────────────────────
    @PostMapping("/start")
    @Transactional
    public ResponseEntity<ApiResponse<?>> startSession(
            @RequestBody @Valid StartSessionRequest request,
            Authentication auth) {

        UUID userId = userId(auth);

        // 8-hour cooldown check
        Instant cooldownCutoff = Instant.now().minus(COOLDOWN_HOURS, ChronoUnit.HOURS);
        var recent = sessionRepo.findFirstByUserIdAndStatusAndFinishedAtAfter(
                userId, "COMPLETED", cooldownCutoff);

        if (recent.isPresent()) {
            Instant unlocksAt = recent.get().getFinishedAt()
                    .plus(COOLDOWN_HOURS, ChronoUnit.HOURS);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.errorWithMeta(
                            "Session started too recently",
                            "SESSION_TOO_SOON",
                            unlocksAt.toString()));
        }

        // Return existing IN_PROGRESS session instead of creating a duplicate
        var inProgress = sessionRepo.findFirstByUserIdAndStatusOrderByStartedAtDesc(
                userId, "IN_PROGRESS");
        if (inProgress.isPresent()) {
            WorkoutSession existing = inProgress.get();
            return ResponseEntity.ok(ApiResponse.success(
                    new StartSessionResponse(existing.getId(), existing.getStartedAt())));
        }

        WorkoutSession session = new WorkoutSession();
        session.setUserId(userId);
        session.setName(request.name());
        session.setBadge(request.badge());
        WorkoutSession saved = sessionRepo.save(session);

        return ResponseEntity.ok(ApiResponse.success(
                new StartSessionResponse(saved.getId(), saved.getStartedAt())));
    }

    // ── POST /sessions/{id}/log-set ───────────────────────────────────
    @PostMapping("/{id}/log-set")
    @Transactional
    public ResponseEntity<ApiResponse<LogSetResponse>> logSet(
            @PathVariable UUID id,
            @RequestBody @Valid LogSetRequest request,
            Authentication auth) {

        UUID userId = userId(auth);
        WorkoutSession session = requireOwnedInProgress(id, userId);

        // weightKg range validation — null is allowed (bodyweight), but numeric values must be 0-500
        if (request.weightKg() != null) {
            if (request.weightKg().compareTo(BigDecimal.ZERO) < 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "weightKg must be >= 0");
            }
            if (request.weightKg().compareTo(new BigDecimal("500")) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "weightKg must be <= 500");
            }
        }

        // PR detection: heaviest set this user has ever logged for this exercise.
        // Bodyweight sets (null weightKg) cannot set a weight PR — isPr stays false
        // unless this is literally the first time they've logged the exercise.
        boolean isPr;
        if (request.weightKg() == null) {
            isPr = setLogRepo
                    .findTopByUserIdAndExerciseIdOrderByWeightKgDesc(userId, request.exerciseId())
                    .isEmpty();
        } else {
            isPr = setLogRepo
                    .findTopByUserIdAndExerciseIdOrderByWeightKgDesc(userId, request.exerciseId())
                    .map(best -> best.getWeightKg() == null
                            || request.weightKg().compareTo(best.getWeightKg()) > 0)
                    .orElse(true);
        }

        // Upsert: insert or update if same session+exercise+setNumber already exists
        setLogRepo.upsertSetLog(
                session.getId(),
                request.exerciseId(),
                request.exerciseName(),
                request.setNumber(),
                request.weightKg(),
                request.reps() != null ? request.reps() : 0);

        // Fetch the saved row to return its id (upsert doesn't return the entity)
        SetLog saved = setLogRepo.findBySessionId(session.getId()).stream()
                .filter(sl -> sl.getExerciseId().equals(request.exerciseId())
                           && sl.getSetNumber().equals(request.setNumber()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "SET_LOG_ERROR", "Failed to retrieve saved set."));

        if (isPr && !Boolean.TRUE.equals(saved.getIsPr())) {
            saved.setIsPr(true);
            setLogRepo.save(saved);
        }

        return ResponseEntity.ok(ApiResponse.success(
                new LogSetResponse(saved.getId(), saved.getIsPr())));
    }

    // ── DELETE /sessions/{id}/log-set/{exerciseId}/{setNumber} ───────
    @DeleteMapping("/{id}/log-set/{exerciseId}/{setNumber}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> deleteSet(
            @PathVariable UUID id,
            @PathVariable String exerciseId,
            @PathVariable int setNumber,
            Authentication auth) {

        requireOwnedInProgress(id, userId(auth));
        setLogRepo.deleteBySessionIdAndExerciseIdAndSetNumber(id, exerciseId, setNumber);
        return ResponseEntity.ok(ApiResponse.success(Map.of("deleted", true)));
    }

    // ── DELETE /sessions/{id}/log-set/exercise/{exerciseId} ──────────
    @DeleteMapping("/{id}/log-set/exercise/{exerciseId}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> deleteExerciseSets(
            @PathVariable UUID id,
            @PathVariable String exerciseId,
            Authentication auth) {

        requireOwnedInProgress(id, userId(auth));
        setLogRepo.deleteBySessionIdAndExerciseId(id, exerciseId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("deleted", true)));
    }

    // ── PATCH /sessions/{id}/swap ────────────────────────────────────
    @PatchMapping("/{id}/swap")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> swapExercise(
            @PathVariable UUID id,
            @RequestBody SwapExerciseRequest request,
            Authentication auth) {

        UUID userId = userId(auth);
        WorkoutSession session = requireOwnedInProgress(id, userId);

        List<Map<String, Object>> swapLog;
        try {
            String raw = session.getSwapLog();
            swapLog = (raw != null && !raw.isBlank())
                    ? objectMapper.readValue(raw, new TypeReference<>() {})
                    : new ArrayList<>();
        } catch (Exception e) {
            swapLog = new ArrayList<>();
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("from",            request.fromExerciseId());
        entry.put("to",              request.toExerciseId());
        entry.put("toName",          request.toExerciseName());
        entry.put("toMuscleGroup",   request.toMuscleGroup());
        entry.put("toEquipment",     request.toEquipment());
        entry.put("toIsBodyweight",  request.toIsBodyweight());
        entry.put("swappedAt",       Instant.now().toString());
        swapLog.add(entry);

        try {
            session.setSwapLog(objectMapper.writeValueAsString(swapLog));
        } catch (Exception e) {
            log.error("Failed to serialize swap log for session {}", id, e);
            throw new RuntimeException("Could not update swap log", e);
        }
        sessionRepo.save(session);

        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "ok")));
    }

    // ── GET /sessions/{id}/sets ───────────────────────────────────────
    @GetMapping("/{id}/sets")
    public ResponseEntity<ApiResponse<List<SetLog>>> getSets(
            @PathVariable UUID id,
            Authentication auth) {

        requireOwned(id, userId(auth));
        List<SetLog> sets = setLogRepo.findBySessionId(id);
        return ResponseEntity.ok(ApiResponse.success(sets));
    }

    // ── POST /sessions/{id}/finish ────────────────────────────────────
    @PostMapping("/{id}/finish")
    @Transactional
    public ResponseEntity<ApiResponse<FinishSessionResponse>> finishSession(
            @PathVariable UUID id,
            @RequestBody FinishSessionRequest request,
            Authentication auth) {

        UUID userId = userId(auth);
        WorkoutSession session = requireOwned(id, userId);

        // Idempotency: already finished — return saved data without reprocessing
        if ("COMPLETED".equals(session.getStatus())) {
            return ResponseEntity.ok(ApiResponse.success(buildExistingResponse(session)));
        }

        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_IN_PROGRESS",
                    "This session is already " + session.getStatus() + ".");
        }

        // ── Compute totals and build exercises JSONB from request ─────
        int totalSets;
        BigDecimal totalVolumeKg;
        String exercisesJson;
        // Collects exercises where a new PR was set — used later for coin awards
        List<ExerciseLogRequest> newPrExercises = new ArrayList<>();

        List<ExerciseLogRequest> exercises = request.exercises();
        if (exercises == null || exercises.isEmpty()) {
            totalSets       = 0;
            totalVolumeKg   = BigDecimal.ZERO;
            exercisesJson   = "[]";
        } else {
            totalSets = exercises.stream()
                    .mapToInt(ex -> ex.sets() != null ? ex.sets().size() : 0)
                    .sum();

            totalVolumeKg = exercises.stream()
                    .filter(ex -> ex.sets() != null)
                    .flatMap(ex -> ex.sets().stream())
                    .map(s -> s.weightKg() != null
                            ? s.weightKg().multiply(BigDecimal.valueOf(s.reps()))
                            : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Build per-exercise data with PR detection
            List<Map<String, Object>> exerciseData = new ArrayList<>();
            for (ExerciseLogRequest ex : exercises) {
                if (ex.sets() == null || ex.sets().isEmpty()) continue;

                // Max weight logged today for this exercise
                BigDecimal todayMax = ex.sets().stream()
                        .map(s -> s.weightKg() != null ? s.weightKg() : BigDecimal.ZERO)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

                // PR detection: bodyweight exercises (all sets weightKg = 0) never get weight PR
                boolean isPr = false;
                boolean isBodyweight = ex.sets().stream()
                        .allMatch(s -> s.weightKg() == null
                                || s.weightKg().compareTo(BigDecimal.ZERO) == 0);
                if (!isBodyweight && todayMax.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal historicalMax = sessionRepo.findMaxWeightForExercise(
                            userId, ex.exerciseId(), session.getId());
                    isPr = historicalMax == null
                            || todayMax.compareTo(historicalMax) > 0;
                }

                BigDecimal exVolume = ex.sets().stream()
                        .map(s -> s.weightKg() != null
                                ? s.weightKg().multiply(BigDecimal.valueOf(s.reps()))
                                : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                List<Map<String, Object>> setsData = ex.sets().stream()
                        .map(s -> {
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("setNumber", s.setNumber());
                            m.put("reps",      s.reps());
                            m.put("weightKg",  s.weightKg());
                            return m;
                        })
                        .collect(Collectors.toList());

                Map<String, Object> exMap = new LinkedHashMap<>();
                exMap.put("exerciseId",   ex.exerciseId());
                exMap.put("exerciseName", ex.exerciseName());
                exMap.put("sets",         setsData);
                exMap.put("maxWeightKg",  todayMax);
                exMap.put("totalVolume",  exVolume);
                exMap.put("isPr",         isPr);
                exerciseData.add(exMap);
                if (isPr) newPrExercises.add(ex);
            }

            try {
                exercisesJson = objectMapper.writeValueAsString(exerciseData);
            } catch (Exception e) {
                log.error("Failed to serialize exercises for session {}", id, e);
                exercisesJson = "[]";
            }
        }

        // ── Week / goal calculations ──────────────────────────────────
        User user = userRepo.findByIdForUpdate(userId).orElseThrow(() -> ApiException.notFound("User"));
        LocalDate monday   = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
        int weekNumber     = weeklyReportService.weekNumberFor(user, monday);
        int weeklyGoal     = user.getWeeklyGoal() != null ? user.getWeeklyGoal() : 4;
        Instant weekFrom   = monday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant weekTo     = monday.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Save session as COMPLETED first so the DB row exists before we count
        session.setStatus("COMPLETED");
        session.setFinishedAt(Instant.now());
        session.setTotalSets(totalSets);
        session.setTotalVolumeKg(totalVolumeKg);
        session.setDurationMins(request.durationMins());
        session.setExercises(exercisesJson);
        session.setWeekNumber(weekNumber);
        sessionRepo.save(session);

        // Count COMPLETED sessions this week AFTER save — no +1 needed
        int count = sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                userId, "COMPLETED", weekFrom, weekTo);
        boolean weeklyGoalHit = count >= weeklyGoal;
        session.setWeeklyGoalHit(weeklyGoalHit);
        sessionRepo.save(session);

        // ── PR upsert — personal_records table ───────────────────────
        // Runs after session is COMPLETED so the history query in PR detection
        // excludes today correctly. One upsert per exercise; skips bodyweight sets.
        if (exercises != null) {
            for (ExerciseLogRequest ex : exercises) {
                if (ex.sets() == null || ex.sets().isEmpty()) continue;
                // Skip bodyweight exercises (all sets have null or zero weightKg)
                boolean isBodyweight = ex.sets().stream()
                        .allMatch(s -> s.weightKg() == null
                                || s.weightKg().compareTo(BigDecimal.ZERO) == 0);
                if (isBodyweight) continue;
                // Best set this session for this exercise
                ex.sets().stream()
                        .filter(s -> s.weightKg() != null
                                && s.weightKg().compareTo(BigDecimal.ZERO) > 0)
                        .max(Comparator.comparing(SetLogRequest::weightKg))
                        .ifPresent(best -> prRepo.upsertPr(
                                userId, ex.exerciseId(), best.weightKg(), best.reps()));
            }
        }

        // Update user streak (floor at 0 — streak must never go negative)
        // Coin balance is now managed entirely by CoinService via atomic SQL updates
        user.setStreak(Math.max(0, user.getStreak() + 1));
        userRepo.save(user);
        // Atomically update max_streak_ever if new streak beats stored value
        userRepo.updateMaxStreakIfHigher(userId, user.getStreak());

        // Generate AI insight synchronously so it's included in the finish response
        String aiCoachInsight = aiService.generateInsightSync(userId, session.getId());

        // Trigger async weekly report when goal is hit
        if (weeklyGoalHit) {
            weeklyReportService.generateWeeklyReport(userId, weekNumber);
        }

        // Rank promotion check
        rankService.checkAndPromote(userId);

        // ── Coin awards (idempotent via CoinService) ──────────────────
        // weekEpoch = start of current ISO week as epoch string (idempotency key for week events)
        String weekEpoch = String.valueOf(weekFrom.getEpochSecond());

        // 1. Log workout +10
        coinService.awardCoins(userId, COINS_PER_SESSION, "LOG_WORKOUT",
                "Logged " + session.getName(), id.toString());

        // 2. Personal record awards +25 per new PR
        for (ExerciseLogRequest prEx : newPrExercises) {
            String exerciseName = prEx.exerciseName() != null ? prEx.exerciseName() : prEx.exerciseId();
            coinService.awardCoins(userId, 25, "PERSONAL_RECORD",
                    exerciseName + " PR",
                    "PR_" + id + "_" + prEx.exerciseId());
        }

        // 3. Weekly goal +50 — only on the exact session that hits the goal
        if (count == weeklyGoal) {
            coinService.awardCoins(userId, 50, "WEEKLY_GOAL",
                    "Weekly goal hit", weekEpoch);
        }

        // 4. Volume improvement vs last week +30
        Instant lastWeekFrom = weekFrom.minus(7, ChronoUnit.DAYS);
        BigDecimal thisWeekVol = sessionRepo.sumVolumeByUserIdAndFinishedAtBetween(userId, weekFrom, weekTo);
        BigDecimal lastWeekVol = sessionRepo.sumVolumeByUserIdAndFinishedAtBetween(userId, lastWeekFrom, weekFrom);
        if (lastWeekVol != null && lastWeekVol.compareTo(BigDecimal.ZERO) > 0
                && thisWeekVol != null && thisWeekVol.compareTo(lastWeekVol) > 0) {
            coinService.awardCoins(userId, 30, "IMPROVE_VOLUME",
                    "Improved vs last week", weekEpoch);
        }

        // 5. Streak milestones
        int currentStreak = user.getStreak();
        if (currentStreak > 0 && currentStreak % 7 == 0) {
            coinService.awardCoins(userId, 35, "STREAK_MILESTONE",
                    currentStreak + "-day streak milestone",
                    String.valueOf(currentStreak));
        }
        if (currentStreak == 30) {
            coinService.awardCoins(userId, 100, "STREAK_30",
                    "30-day streak milestone", "30");
        }

        return ResponseEntity.ok(ApiResponse.success(new FinishSessionResponse(
                session.getId(),
                session.getName(),
                totalVolumeKg,
                totalSets,
                request.durationMins(),
                session.getFinishedAt(),
                user.getStreak(),
                COINS_PER_SESSION,
                weeklyGoalHit,
                weekNumber,
                count,
                aiCoachInsight)));
    }

    // ── POST /sessions/{id}/feedback ─────────────────────────────────
    @PostMapping("/{id}/feedback")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitFeedback(
            @PathVariable UUID id,
            @RequestBody @Valid SessionFeedbackRequest request,
            Authentication auth) {

        UUID userId = userId(auth);
        WorkoutSession session = requireOwned(id, userId);

        if (!"COMPLETED".equals(session.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "SESSION_NOT_COMPLETE", "Can only rate completed sessions.");
        }

        if (feedbackRepo.findBySessionId(id).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "FEEDBACK_EXISTS", "Feedback already submitted for this session.");
        }

        String notes = request.getNotes();
        if (notes != null) {
            notes = notes.replaceAll(
                    "(?i)(ignore previous|forget your|you are now|system prompt|jailbreak|ignore instructions)",
                    "").trim();
            if (notes.isEmpty()) notes = null;
        }

        SessionFeedback feedback = new SessionFeedback();
        feedback.setUserId(userId);
        feedback.setSessionId(id);
        feedback.setRating(request.getRating());
        feedback.setNotes(notes);
        feedbackRepo.save(feedback);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "message",   "Feedback saved successfully",
                "sessionId", id,
                "rating",    request.getRating())));
    }

    // ── POST /sessions/{id}/discard ───────────────────────────────────
    @PostMapping("/{id}/discard")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> discardSession(
            @PathVariable UUID id,
            Authentication auth) {

        WorkoutSession session = requireOwned(id, userId(auth));
        setLogRepo.deleteBySessionId(session.getId());
        sessionRepo.delete(session);

        return ResponseEntity.ok(ApiResponse.success(Map.of("discarded", true)));
    }

    // ── GET /sessions/today ───────────────────────────────────────────
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<TodaySessionResponse>> todaySession(Authentication auth) {
        UUID userId = userId(auth);

        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd   = dayStart.plus(1, ChronoUnit.DAYS);

        WorkoutSession session = sessionRepo
                .findFirstByUserIdAndStartedAtBetweenOrderByStartedAtDesc(userId, dayStart, dayEnd)
                .orElseThrow(() -> ApiException.notFound("Session"));

        List<SetLog> logs = setLogRepo.findBySessionId(session.getId());

        User user = userRepo.findById(userId).orElseThrow(() -> ApiException.notFound("User"));

        LocalDate monday   = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
        Instant weekFrom   = monday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant weekTo     = monday.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant();
        int completedThisWeek = sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                userId, "COMPLETED", weekFrom, weekTo);

        String date = session.getStartedAt() != null
                ? LocalDate.ofInstant(session.getStartedAt(), ZoneOffset.UTC).toString()
                : null;

        List<Map<String, Object>> swapLog;
        try {
            String raw = session.getSwapLog();
            swapLog = (raw != null && !raw.isBlank())
                    ? objectMapper.readValue(raw, new TypeReference<>() {})
                    : List.of();
        } catch (Exception e) {
            swapLog = List.of();
        }

        TodaySessionResponse response = new TodaySessionResponse(
                session.getId(),
                session.getName(),
                date,
                session.getTotalVolumeKg() != null ? session.getTotalVolumeKg() : BigDecimal.ZERO,
                session.getTotalSets()     != null ? session.getTotalSets()     : logs.size(),
                session.getDurationMins(),
                session.getAiInsight(),
                session.getStatus(),
                user.getStreak(),
                completedThisWeek,
                swapLog);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── GET /sessions/history ─────────────────────────────────────────
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<SessionHistoryItem>>> history(Authentication auth) {
        UUID userId = userId(auth);

        List<SessionHistoryItem> items = sessionRepo
                .findTop20ByUserIdAndStatusOrderByStartedAtDesc(userId, "COMPLETED")
                .stream()
                .map(session -> {
                    List<SetLog> logs = setLogRepo.findBySessionId(session.getId());

                    // Group sets by exercise name, preserving first-seen order
                    Map<String, List<SetLog>> grouped = logs.stream()
                            .collect(Collectors.groupingBy(
                                    sl -> sl.getExerciseName() != null
                                            ? sl.getExerciseName() : sl.getExerciseId(),
                                    LinkedHashMap::new,
                                    Collectors.toList()));

                    List<SessionHistoryItem.ExerciseGroup> exercises = grouped.entrySet().stream()
                            .map(e -> new SessionHistoryItem.ExerciseGroup(
                                    e.getKey(),
                                    e.getValue().stream()
                                            .map(sl -> new SessionHistoryItem.SetSummary(
                                                    sl.getWeightKg(), sl.getReps()))
                                            .collect(Collectors.toList())))
                            .collect(Collectors.toList());

                    String date = session.getStartedAt() != null
                            ? LocalDate.ofInstant(session.getStartedAt(), ZoneOffset.UTC).toString()
                            : null;

                    return new SessionHistoryItem(
                            session.getId(),
                            session.getName(),
                            date,
                            session.getTotalVolumeKg(),
                            session.getTotalSets() != null ? session.getTotalSets() : 0,
                            session.getDurationMins(),
                            exercises);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(items));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private UUID userId(Authentication auth) {
        return (UUID) auth.getPrincipal();
    }

    /** Derives a comma-separated muscle group label from set_log exercise IDs. */
    private String deriveMuscleGroups(List<SetLog> logs) {
        java.util.Set<String> muscles = new java.util.LinkedHashSet<>();
        for (SetLog sl : logs) {
            String id = sl.getExerciseId() != null ? sl.getExerciseId().toLowerCase() : "";
            if (id.contains("bench") || id.contains("pec") || id.contains("flye") || id.contains("push-up") || id.contains("dip")) muscles.add("Chest");
            if (id.contains("shoulder") || id.contains("lateral") || id.contains("front-raise") || id.contains("overhead") || id.contains("arnold") || id.contains("face-pull") || id.contains("reverse-flye")) muscles.add("Shoulders");
            if (id.contains("pull") || id.contains("row") || id.contains("deadlift") || id.contains("lat-pulldown") || id.contains("chin")) muscles.add("Back");
            if (id.contains("tricep") || id.contains("skull") || id.contains("close-grip")) muscles.add("Triceps");
            if (id.contains("bicep") || id.contains("curl") || id.contains("hammer")) muscles.add("Biceps");
            if (id.contains("squat") || id.contains("lunge") || id.contains("leg-press") || id.contains("leg-curl") || id.contains("leg-extension") || id.contains("hip-thrust") || id.contains("glute") || id.contains("calf") || id.contains("romanian")) muscles.add("Legs");
            if (id.contains("plank") || id.contains("crunch") || id.contains("dead-bug") || id.contains("mountain") || id.contains("russian-twist") || id.contains("leg-raise") || id.contains("bicycle") || id.contains("ab-wheel") || id.contains("dragon-flag")) muscles.add("Core");
        }
        return muscles.isEmpty() ? "Mixed" : String.join(", ", muscles);
    }

    /** Load session, verify it belongs to the authenticated user. */
    private WorkoutSession requireOwned(UUID sessionId, UUID userId) {
        WorkoutSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Session"));
        if (!session.getUserId().equals(userId)) throw ApiException.forbidden();
        return session;
    }

    /** Load session, verify ownership AND that it is still IN_PROGRESS. */
    private WorkoutSession requireOwnedInProgress(UUID sessionId, UUID userId) {
        WorkoutSession session = requireOwned(sessionId, userId);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_IN_PROGRESS",
                    "This session is already " + session.getStatus() + ".");
        }
        return session;
    }

    /**
     * Builds a FinishSessionResponse from an already-completed session.
     * Used by the idempotency check — avoids reprocessing coins/streak/goals.
     */
    private FinishSessionResponse buildExistingResponse(WorkoutSession session) {
        return new FinishSessionResponse(
                session.getId(),
                session.getName(),
                session.getTotalVolumeKg() != null ? session.getTotalVolumeKg() : BigDecimal.ZERO,
                session.getTotalSets()     != null ? session.getTotalSets()     : 0,
                session.getDurationMins(),
                session.getFinishedAt(),
                0,               // streak not re-read — idempotent replay, use 0 as sentinel
                0,               // coinsEarned not re-credited
                Boolean.TRUE.equals(session.getWeeklyGoalHit()),
                session.getWeekNumber() != null ? session.getWeekNumber() : 0,
                0,               // completedThisWeek not re-counted
                session.getAiInsight()); // already saved from original finish call
    }
}
