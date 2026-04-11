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
import com.fittribe.api.dto.response.FeedbackInfo;
import com.fittribe.api.dto.response.FinishSessionResponse;
import com.fittribe.api.dto.response.LogSetResponse;
import com.fittribe.api.dto.response.SessionHistoryItem;
import com.fittribe.api.dto.response.StartSessionResponse;
import com.fittribe.api.dto.response.TodaySessionResponse;
import com.fittribe.api.dto.request.SessionFeedbackRequest;
import com.fittribe.api.entity.CoinTransaction;
import com.fittribe.api.entity.FeedItem;
import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.entity.SavedRoutine;
import com.fittribe.api.entity.SessionFeedback;
import com.fittribe.api.entity.SetLog;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.CoinTransactionRepository;
import com.fittribe.api.repository.FeedItemRepository;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.PersonalRecordRepository;
import com.fittribe.api.repository.SavedRoutineRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Optional;
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
    private final SavedRoutineRepository    routineRepo;
    private final GroupMemberRepository     groupMemberRepo;
    private final FeedItemRepository        feedItemRepo;
    private final TransactionTemplate       transactionTemplate;

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
                             CoinService coinService,
                             SavedRoutineRepository routineRepo,
                             GroupMemberRepository groupMemberRepo,
                             FeedItemRepository feedItemRepo,
                             PlatformTransactionManager transactionManager) {
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
        this.routineRepo         = routineRepo;
        this.groupMemberRepo     = groupMemberRepo;
        this.feedItemRepo        = feedItemRepo;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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

        // Determine source — default to AI_PLAN for backward compat
        String source = (request.source() != null && !request.source().isBlank())
                ? request.source()
                : "AI_PLAN";
        session.setSource(source);

        // Validate source value
        if (!source.equals("AI_PLAN") && !source.equals("CUSTOM") && !source.equals("SAVED_ROUTINE")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE",
                    "Source must be AI_PLAN, CUSTOM, or SAVED_ROUTINE");
        }

        // For CUSTOM and SAVED_ROUTINE, plannedExercises is required
        if ((source.equals("CUSTOM") || source.equals("SAVED_ROUTINE"))
                && (request.plannedExercises() == null || request.plannedExercises().isEmpty())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLANNED_EXERCISES_REQUIRED",
                    "plannedExercises is required for CUSTOM and SAVED_ROUTINE sources");
        }

        // For SAVED_ROUTINE, sourceRoutineId is required and must be owned
        if (source.equals("SAVED_ROUTINE")) {
            if (request.sourceRoutineId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ROUTINE_ID_REQUIRED",
                        "sourceRoutineId is required for SAVED_ROUTINE source");
            }
            SavedRoutine routine = routineRepo.findByIdAndUserId(request.sourceRoutineId(), userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROUTINE_NOT_FOUND",
                            "Routine not found"));
            session.setSourceRoutineId(request.sourceRoutineId());

            // Increment usage tracking
            routine.setTimesUsed(routine.getTimesUsed() + 1);
            routine.setLastUsedAt(Instant.now());
            routineRepo.save(routine);
        }

        // Store planned exercises for CUSTOM and SAVED_ROUTINE
        if (request.plannedExercises() != null && !request.plannedExercises().isEmpty()) {
            try {
                session.setPlannedExercises(objectMapper.writeValueAsString(request.plannedExercises()));
            } catch (Exception e) {
                log.error("Failed to serialize planned exercises", e);
                throw new RuntimeException("Could not serialize planned exercises", e);
            }
        }

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
    // NOT @Transactional at the method level. The core save (session status
    // → COMPLETED + weeklyGoalHit) runs in its own explicit TransactionTemplate
    // block. All derived-data side effects (PR upsert, streak, weekly report,
    // rank, coin awards, feed items) run OUTSIDE any enclosing transaction,
    // each wrapped in its own try/catch. This guarantees:
    //   (a) derived-data failures cannot mark an outer tx rollback-only and
    //       turn a successful save into UnexpectedRollbackException at commit,
    //   (b) a failure in one derived block does not skip the others.
    @PostMapping("/{id}/finish")
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

        // ────────────────────────────────────────────────────────────────
        // CORE SAVE — runs in its own explicit transaction. MUST succeed.
        // Loads user FOR UPDATE, computes week/goal, writes session as
        // COMPLETED, counts weekly sessions, writes weeklyGoalHit. If this
        // block throws, the client gets a 5xx and no derived data runs.
        // ────────────────────────────────────────────────────────────────
        final String exercisesJsonFinal     = exercisesJson;
        final int totalSetsFinal            = totalSets;
        final BigDecimal totalVolumeKgFinal = totalVolumeKg;
        CoreFinishData core = transactionTemplate.execute(txStatus -> {
            User u = userRepo.findByIdForUpdate(userId)
                    .orElseThrow(() -> ApiException.notFound("User"));
            LocalDate monday = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
            int weekNum  = weeklyReportService.weekNumberFor(u, monday);
            int wkGoal   = u.getWeeklyGoal() != null ? u.getWeeklyGoal() : 4;
            Instant from = monday.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant to   = monday.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant();

            // Mutate the outer `session` reference. With open-in-view=false
            // it's detached here, so sessionRepo.save triggers em.merge():
            // load row, copy our field mutations, return managed. Hibernate
            // flushes the managed copy on commit. The count query below runs
            // inside the same tx and auto-flushes before executing, so it
            // sees the COMPLETED status from the first save.
            session.setStatus("COMPLETED");
            session.setFinishedAt(Instant.now());
            session.setTotalSets(totalSetsFinal);
            session.setTotalVolumeKg(totalVolumeKgFinal);
            session.setDurationMins(request.durationMins());
            session.setExercises(exercisesJsonFinal);
            session.setWeekNumber(weekNum);
            sessionRepo.save(session);

            int cnt = sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                    userId, "COMPLETED", from, to);
            boolean hit = cnt >= wkGoal;
            session.setWeeklyGoalHit(hit);
            sessionRepo.save(session);

            return new CoreFinishData(u, weekNum, wkGoal, hit, cnt, from, to);
        });
        // Core tx has committed. `session` and `core.user()` are detached.
        // Reading their scalar fields still works; writes must go via
        // atomic SQL (updateStreak) or accept merge semantics.

        final User user              = core.user();
        final int weekNumber         = core.weekNumber();
        final int weeklyGoal         = core.weeklyGoal();
        final boolean weeklyGoalHit  = core.weeklyGoalHit();
        final int count              = core.count();
        final Instant weekFrom       = core.weekFrom();
        final Instant weekTo         = core.weekTo();

        // ────────────────────────────────────────────────────────────────
        // DERIVED DATA — runs OUTSIDE any enclosing transaction. Each
        // inner @Transactional service/repo call opens its own short tx,
        // so a failure in one block cannot mark the others rollback-only.
        // Every failure is logged at error level; nothing is swallowed.
        // ────────────────────────────────────────────────────────────────

        // PR upsert — personal_records table.
        // Runs after session is COMPLETED so the history query in PR detection
        // excludes today correctly. One upsert per exercise; skips bodyweight sets.
        try {
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
        } catch (Exception e) {
            log.error("Failed to upsert personal records for session={}", id, e);
        }

        // Streak update — atomic SQL to avoid detached-entity merge races
        // with RankService.checkAndPromote (which also writes to users).
        // Coin balance is managed entirely by CoinService via atomic SQL updates.
        try {
            int newStreak = Math.max(0, user.getStreak() + 1);
            userRepo.updateStreak(userId, newStreak);
            userRepo.updateMaxStreakIfHigher(userId, newStreak);
            user.setStreak(newStreak); // keep in-memory value in sync for the response
        } catch (Exception e) {
            log.error("Failed to update streak for user={}", userId, e);
        }

        // Generate AI insight synchronously so it's included in the finish response.
        // generateInsightSync already returns null on failure per CLAUDE.md, but we
        // wrap defensively in case it ever throws an unchecked exception.
        String aiCoachInsight = null;
        try {
            aiCoachInsight = aiService.generateInsightSync(userId, session.getId());
        } catch (Exception e) {
            log.error("Failed to generate AI insight for session={}", id, e);
        }

        // Trigger async weekly report when goal is hit.
        if (weeklyGoalHit) {
            try {
                weeklyReportService.generateWeeklyReport(userId, weekNumber);
            } catch (Exception e) {
                log.error("Failed to trigger weekly report for user={} week={}", userId, weekNumber, e);
            }
        }

        // Rank promotion check.
        try {
            rankService.checkAndPromote(userId);
        } catch (Exception e) {
            log.error("Failed to check rank promotion for user={}", userId, e);
        }

        // ── Coin awards (idempotent via CoinService) ──────────────────
        try {
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
        } catch (Exception e) {
            log.error("Failed to award coins for session={}", id, e);
        }

        // ── Feed items — post to all user's groups ───────────────────────
        try {
            List<GroupMember> memberships = groupMemberRepo.findByUserId(userId);
            if (!memberships.isEmpty()) {
                String displayName = user.getDisplayName() != null ? user.getDisplayName() : "Someone";

                // WORKOUT_LOGGED feed item
                String workoutBody = displayName + " finished a workout · " + totalSets + " sets · "
                        + totalVolumeKg.toBigInteger() + " kg volume";
                for (GroupMember gm : memberships) {
                    try {
                        FeedItem fi = new FeedItem();
                        fi.setGroupId(gm.getGroupId());
                        fi.setUserId(userId);
                        fi.setType("WORKOUT_LOGGED");
                        fi.setBody(workoutBody);
                        feedItemRepo.save(fi);
                    } catch (Exception e) {
                        log.error("Failed to post WORKOUT_LOGGED feed for group={}", gm.getGroupId(), e);
                    }
                }

                // PR feed items — one per PR exercise, posted to every group
                for (ExerciseLogRequest prEx : newPrExercises) {
                    String exerciseName = prEx.exerciseName() != null ? prEx.exerciseName() : prEx.exerciseId();
                    BigDecimal prWeight = prEx.sets().stream()
                            .map(s -> s.weightKg() != null ? s.weightKg() : BigDecimal.ZERO)
                            .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                    int prReps = prEx.sets().stream()
                            .filter(s -> s.weightKg() != null && s.weightKg().compareTo(prWeight) == 0)
                            .mapToInt(SetLogRequest::reps)
                            .max().orElse(0);
                    String prBody = displayName + " hit a new PR · " + exerciseName + " · "
                            + prWeight.stripTrailingZeros().toPlainString() + "kg × " + prReps;
                    for (GroupMember gm : memberships) {
                        try {
                            FeedItem fi = new FeedItem();
                            fi.setGroupId(gm.getGroupId());
                            fi.setUserId(userId);
                            fi.setType("PR");
                            fi.setBody(prBody);
                            feedItemRepo.save(fi);
                        } catch (Exception e) {
                            log.error("Failed to post PR feed for group={}", gm.getGroupId(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to post feed items for session={}", id, e);
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

    /**
     * Tuple of values produced inside the core-save TransactionTemplate block
     * and consumed by the derived-data blocks + response builder that run
     * AFTER the core tx has committed. Keeps the derived-data section free
     * of references into the tx lambda scope.
     */
    private record CoreFinishData(
            User user,
            int weekNumber,
            int weeklyGoal,
            boolean weeklyGoalHit,
            int count,
            Instant weekFrom,
            Instant weekTo) {}

    // ── POST /sessions/{id}/feedback (upsert) ──────────────────────────
    @PostMapping("/{id}/feedback")
    public ResponseEntity<ApiResponse<FeedbackInfo>> submitFeedback(
            @PathVariable UUID id,
            @RequestBody @Valid SessionFeedbackRequest request,
            Authentication auth) {

        UUID userId = userId(auth);
        WorkoutSession session = requireOwned(id, userId);

        if (!"COMPLETED".equals(session.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "SESSION_NOT_COMPLETE", "Can only rate completed sessions.");
        }

        String notes = request.getNotes();
        if (notes != null) {
            notes = notes.replaceAll(
                    "(?i)(ignore previous|forget your|you are now|system prompt|jailbreak|ignore instructions)",
                    "").trim();
            if (notes.isEmpty()) notes = null;
        }

        SessionFeedback feedback = feedbackRepo.findBySessionId(id)
                .orElseGet(() -> {
                    SessionFeedback f = new SessionFeedback();
                    f.setUserId(userId);
                    f.setSessionId(id);
                    return f;
                });
        feedback.setRating(request.getRating());
        feedback.setNotes(notes);
        feedback.setUpdatedAt(Instant.now());
        feedbackRepo.save(feedback);

        return ResponseEntity.ok(ApiResponse.success(
                new FeedbackInfo(feedback.getRating(), feedback.getNotes(), feedback.getCreatedAt())));
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
                .orElse(null);

        if (session == null) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }

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

        List<Map<String, Object>> plannedExercises;
        try {
            String raw = session.getPlannedExercises();
            plannedExercises = (raw != null && !raw.isBlank())
                    ? objectMapper.readValue(raw, new TypeReference<>() {})
                    : null;
        } catch (Exception e) {
            plannedExercises = null;
        }

        FeedbackInfo feedback = feedbackRepo.findBySessionId(session.getId())
                .map(fb -> new FeedbackInfo(fb.getRating(), fb.getNotes(), fb.getCreatedAt()))
                .orElse(null);

        TodaySessionResponse response = new TodaySessionResponse(
                session.getId(),
                session.getName(),
                date,
                session.getTotalVolumeKg() != null ? session.getTotalVolumeKg() : BigDecimal.ZERO,
                session.getTotalSets()     != null ? session.getTotalSets()     : logs.size(),
                session.getDurationMins(),
                session.getFinishedAt(),
                session.getAiInsight(),
                session.getStatus(),
                user.getStreak(),
                completedThisWeek,
                swapLog,
                session.getSource(),
                plannedExercises,
                feedback);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── GET /sessions/history ─────────────────────────────────────────
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<SessionHistoryItem>>> history(Authentication auth) {
        UUID userId = userId(auth);

        List<WorkoutSession> sessions = sessionRepo
                .findTop20ByUserIdAndStatusOrderByStartedAtDesc(userId, "COMPLETED");

        // Batch-load feedback for all sessions in one query
        List<UUID> sessionIds = sessions.stream().map(WorkoutSession::getId).collect(Collectors.toList());
        Map<UUID, SessionFeedback> feedbackBySession = feedbackRepo.findBySessionIdIn(sessionIds)
                .stream()
                .collect(Collectors.toMap(SessionFeedback::getSessionId, fb -> fb));

        List<SessionHistoryItem> items = sessions.stream()
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

                    SessionFeedback fb = feedbackBySession.get(session.getId());
                    FeedbackInfo feedback = fb != null
                            ? new FeedbackInfo(fb.getRating(), fb.getNotes(), fb.getCreatedAt())
                            : null;

                    return new SessionHistoryItem(
                            session.getId(),
                            session.getName(),
                            date,
                            session.getTotalVolumeKg(),
                            session.getTotalSets() != null ? session.getTotalSets() : 0,
                            session.getDurationMins(),
                            exercises,
                            feedback);
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
