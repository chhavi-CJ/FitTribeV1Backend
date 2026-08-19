package com.fittribe.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.request.EditSetRequest;
import com.fittribe.api.dto.request.ExerciseLogRequest;
import com.fittribe.api.dto.request.FinishSessionRequest;
import com.fittribe.api.dto.request.LogSetRequest;
import com.fittribe.api.dto.request.SetLogRequest;
import com.fittribe.api.dto.request.StartSessionRequest;
import com.fittribe.api.dto.request.SwapExerciseRequest;
import com.fittribe.api.dto.response.DeleteSetResponse;
import com.fittribe.api.dto.response.EditSetResponse;
import com.fittribe.api.dto.response.FeedbackInfo;
import com.fittribe.api.dto.response.FinishSessionResponse;
import com.fittribe.api.dto.response.LogSetResponse;
import com.fittribe.api.dto.response.PrDetails;
import com.fittribe.api.dto.response.SessionHistoryItem;
import com.fittribe.api.dto.response.StartSessionResponse;
import com.fittribe.api.dto.response.TodaySessionResponse;
import com.fittribe.api.dto.request.SessionFeedbackRequest;
import com.fittribe.api.dto.request.UpdateSessionRequest;
import com.fittribe.api.entity.CoinTransaction;
import com.fittribe.api.entity.FeedItem;
import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.service.FeedEventWriter;
import com.fittribe.api.service.SessionFinishContext;
import com.fittribe.api.service.SessionFinishPostProcessor;
import com.fittribe.api.entity.PrEvent;
import com.fittribe.api.entity.SavedRoutine;
import com.fittribe.api.entity.SessionFeedback;
import com.fittribe.api.entity.SetLog;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.service.AnalyticsService;
import com.fittribe.api.prv2.detector.ExerciseType;
import com.fittribe.api.prv2.detector.LoggedSet;
import com.fittribe.api.prv2.detector.PrCategory;
import com.fittribe.api.prv2.detector.PRDetector;
import com.fittribe.api.prv2.detector.PRResult;
import com.fittribe.api.prv2.service.PrEditCascadeAsyncRunner;
import com.fittribe.api.prv2.service.PrEditCascadeService;
import com.fittribe.api.prv2.service.PrWritePathService;
import com.fittribe.api.entity.Exercise;
import com.fittribe.api.repository.CoinTransactionRepository;
import com.fittribe.api.repository.ExerciseRepository;
import com.fittribe.api.repository.FeedItemRepository;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.PrEventRepository;
import com.fittribe.api.repository.SavedRoutineRepository;
import com.fittribe.api.repository.SessionFeedbackRepository;
import com.fittribe.api.repository.SetLogRepository;
import com.fittribe.api.repository.UserDayStatusRepository;
import com.fittribe.api.repository.UserExerciseBestsRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.jobs.JobEnqueuer;
import com.fittribe.api.jobs.JobType;
import com.fittribe.api.jobs.JobWorker;
import com.fittribe.api.service.AiService;
import com.fittribe.api.service.BonusFreezeGrantService;
import com.fittribe.api.service.CoinService;
import com.fittribe.api.service.GroupProgressService;
import com.fittribe.api.service.PlanService;
import com.fittribe.api.service.RankService;
import com.fittribe.api.service.ShareImageService;
import com.fittribe.api.strengthscore.ProgressSnapshotService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import com.fittribe.api.util.Zones;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.HashMap;
import com.fittribe.api.repository.DailyPlanGeneratedRepository;
import com.fittribe.api.entity.DailyPlanGenerated;
import com.fittribe.api.entity.DailyPlanGeneratedId;

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
    private final JobEnqueuer               jobEnqueuer;
    private final PlanService               planService;
    private final ObjectMapper              objectMapper;
    private final RankService               rankService;
    private final CoinService               coinService;
    private final SavedRoutineRepository    routineRepo;
    private final GroupMemberRepository     groupMemberRepo;
    private final FeedItemRepository        feedItemRepo;
    private final TransactionTemplate       transactionTemplate;
    private final ProgressSnapshotService   progressSnapshotService;
    private final PrWritePathService        prWritePathService;
    private final PrEditCascadeService      prEditCascadeService;
    private final PrEditCascadeAsyncRunner  prEditCascadeAsyncRunner;
    private final PrEventRepository         prEventRepo;
    private final UserExerciseBestsRepository userExerciseBestsRepo;
    private final PRDetector                 prDetector;
    private final ExerciseRepository         exerciseRepo;
    private final GroupProgressService       groupProgressService;
    private final FeedEventWriter            feedEventWriter;
    private final UserDayStatusRepository   dayStatusRepo;
    private final SessionFinishPostProcessor postProcessor;
    private final AnalyticsService          analyticsService;
    private final BonusFreezeGrantService    bonusFreezeGrantService;
    private final ShareImageService         shareImageService;
    private final DailyPlanGeneratedRepository dailyPlanRepo;

    public SessionController(WorkoutSessionRepository sessionRepo,
                             SetLogRepository setLogRepo,
                             UserRepository userRepo,
                             CoinTransactionRepository coinRepo,
                             SessionFeedbackRepository feedbackRepo,
                             AiService aiService,
                             JobEnqueuer jobEnqueuer,
                             PlanService planService,
                             ObjectMapper objectMapper,
                             RankService rankService,
                             CoinService coinService,
                             SavedRoutineRepository routineRepo,
                             GroupMemberRepository groupMemberRepo,
                             FeedItemRepository feedItemRepo,
                             PlatformTransactionManager transactionManager,
                             ProgressSnapshotService progressSnapshotService,
                             PrWritePathService prWritePathService,
                             PrEditCascadeService prEditCascadeService,
                             PrEditCascadeAsyncRunner prEditCascadeAsyncRunner,
                             PrEventRepository prEventRepo,
                             UserExerciseBestsRepository userExerciseBestsRepo,
                             PRDetector prDetector,
                             ExerciseRepository exerciseRepo,
                             GroupProgressService groupProgressService,
                             FeedEventWriter feedEventWriter,
                             BonusFreezeGrantService bonusFreezeGrantService,
                             UserDayStatusRepository dayStatusRepo,
                             SessionFinishPostProcessor postProcessor,
                             AnalyticsService analyticsService,
                             ShareImageService shareImageService,
                             DailyPlanGeneratedRepository dailyPlanRepo) {
        this.sessionRepo         = sessionRepo;
        this.setLogRepo          = setLogRepo;
        this.userRepo            = userRepo;
        this.coinRepo            = coinRepo;
        this.feedbackRepo        = feedbackRepo;
        this.aiService           = aiService;
        this.jobEnqueuer         = jobEnqueuer;
        this.planService         = planService;
        this.objectMapper        = objectMapper;
        this.rankService         = rankService;
        this.coinService         = coinService;
        this.routineRepo         = routineRepo;
        this.groupMemberRepo     = groupMemberRepo;
        this.feedItemRepo        = feedItemRepo;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.progressSnapshotService = progressSnapshotService;
        this.prWritePathService  = prWritePathService;
        this.prEditCascadeService = prEditCascadeService;
        this.prEditCascadeAsyncRunner = prEditCascadeAsyncRunner;
        this.prEventRepo = prEventRepo;
        this.userExerciseBestsRepo = userExerciseBestsRepo;
        this.prDetector = prDetector;
        this.exerciseRepo = exerciseRepo;
        this.groupProgressService = groupProgressService;
        this.feedEventWriter = feedEventWriter;
        this.bonusFreezeGrantService = bonusFreezeGrantService;
        this.dayStatusRepo = dayStatusRepo;
        this.postProcessor = postProcessor;
        this.analyticsService = analyticsService;
        this.shareImageService = shareImageService;
        this.dailyPlanRepo = dailyPlanRepo;
    }

    // All-zeros UUID, rejected as a clientId — too easy to send by mistake
    // (uninitialised buffers, default values) and signals nothing useful.
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    // ── POST /sessions/start ──────────────────────────────────────────
    //
    // Accepts an optional client-supplied UUID via {@code clientId}. When
    // present the value becomes the canonical {@code workout_sessions.id};
    // when absent the server generates one (legacy contract). Every other
    // existing branch — cooldown, in-progress dedupe, source validation,
    // SAVED_ROUTINE update — is unchanged.
    //
    // {@code noRollbackFor = DataIntegrityViolationException.class} keeps
    // the surrounding transaction usable inside the catch block: when two
    // concurrent requests race for the same {@code clientId}, the loser's
    // INSERT trips the PK constraint and we re-query so both callers see
    // identical responses (idempotent retry).
    @PostMapping("/start")
    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> startSession(
            @RequestBody @Valid StartSessionRequest request,
            Authentication auth) {

        UUID userId = userId(auth);
        UUID clientId = request.clientId();

        // Reject the all-zeros UUID up front — a valid v4 from any
        // randomUUID source will never equal this value.
        if (clientId != null && ZERO_UUID.equals(clientId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CLIENT_ID",
                    "clientId must not be the all-zeros UUID.");
        }

        // 8-hour cooldown check — runs first so it always wins over
        // idempotency: a client retrying a clientId during cooldown still
        // gets SESSION_TOO_SOON.
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

        // Idempotency lookup: existing row with this clientId for this user
        // → either return it (still IN_PROGRESS) or 409 (already finalised).
        // A row owned by a different user looks like 404 to avoid leaking
        // the existence of foreign sessions.
        if (clientId != null) {
            Optional<WorkoutSession> existing = sessionRepo.findById(clientId);
            if (existing.isPresent()) {
                WorkoutSession s = existing.get();
                if (!userId.equals(s.getUserId())) {
                    throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                            "Session not found.");
                }
                if ("IN_PROGRESS".equals(s.getStatus())) {
                    return ResponseEntity.ok(ApiResponse.success(
                            new StartSessionResponse(s.getId(), s.getStartedAt())));
                }
                throw new ApiException(HttpStatus.CONFLICT, "SESSION_ALREADY_FINALIZED",
                        "Session has already been finalized and cannot be reused.");
            }
        }

        // Return existing IN_PROGRESS session instead of creating a duplicate.
        // Runs after the clientId lookup so a user with both an existing
        // IN_PROGRESS session AND a fresh clientId still resumes the
        // existing session (cross-device safety).
        var inProgress = sessionRepo.findFirstByUserIdAndStatusOrderByStartedAtDesc(
                userId, "IN_PROGRESS");
        if (inProgress.isPresent()) {
            WorkoutSession existing = inProgress.get();
            return ResponseEntity.ok(ApiResponse.success(
                    new StartSessionResponse(existing.getId(), existing.getStartedAt())));
        }

        WorkoutSession session = new WorkoutSession();
        // Caller supplies the id when present; else fall back to a
        // server-generated v4. The DB-level PK constraint is the
        // authoritative idempotency guard for the clientId path.
        session.setId(clientId != null ? clientId : UUID.randomUUID());
        session.setUserId(userId);
        session.setName(request.name());
        session.setBadge(request.badge());

        // Determine source — default to AI_PLAN for backward compat
        String source = (request.source() != null && !request.source().isBlank())
                ? request.source()
                : "AI_PLAN";
        session.setSource(source);

        // Validate source value
        if (!source.equals("AI_PLAN") && !source.equals("CUSTOM") && !source.equals("SAVED_ROUTINE")
                && !source.equals("BONUS")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE",
                    "Source must be AI_PLAN, CUSTOM, SAVED_ROUTINE, or BONUS");
        }

        // For CUSTOM, SAVED_ROUTINE, and BONUS, plannedExercises is required
        if ((source.equals("CUSTOM") || source.equals("SAVED_ROUTINE") || source.equals("BONUS"))
                && (request.plannedExercises() == null || request.plannedExercises().isEmpty())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLANNED_EXERCISES_REQUIRED",
                    "plannedExercises is required for CUSTOM, SAVED_ROUTINE, and BONUS sources");
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

        // Store planned exercises for ALL sources (AI_PLAN, CUSTOM, SAVED_ROUTINE, BONUS)
        // This enables session reconstruction on resume — unlogged planned exercises
        // are merged with set_logs to restore the full exercise list on app restart.
        List<?> plannedExercises = request.plannedExercises();

        // For AI_PLAN sessions, if plannedExercises not in payload, load from today's DailyPlanGenerated
        if (("AI_PLAN".equals(source) || "BONUS".equals(source)) &&
            (plannedExercises == null || plannedExercises.isEmpty())) {
            try {
                LocalDate today = Zones.fitnessDayNow();
                Optional<DailyPlanGenerated> dailyPlan = dailyPlanRepo.findByIdUserIdAndIdDate(userId, today);
                if (dailyPlan.isPresent()) {
                    String exercisesJson = dailyPlan.get().getExercises();
                    if (exercisesJson != null && !exercisesJson.isBlank()) {
                        plannedExercises = objectMapper.readValue(exercisesJson, List.class);
                        log.debug("AI_PLAN session: loaded plannedExercises from DailyPlanGenerated for userId={}", userId);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load plannedExercises from DailyPlanGenerated for AI_PLAN session, userId={}", userId, e);
                // Fall through — continue without plannedExercises
            }
        }

        if (plannedExercises != null && !plannedExercises.isEmpty()) {
            try {
                session.setPlannedExercises(objectMapper.writeValueAsString(plannedExercises));
            } catch (Exception e) {
                log.error("Failed to serialize planned exercises", e);
                throw new RuntimeException("Could not serialize planned exercises", e);
            }
        }

        try {
            // saveAndFlush, not save, because we need the PK-violation to
            // surface synchronously here — without an explicit flush the
            // INSERT runs at commit time, well past our catch block.
            // WorkoutSession implements Persistable<UUID> so Spring Data
            // calls EntityManager.persist (single INSERT) rather than
            // merge (SELECT + INSERT/UPDATE) for these fresh entities.
            WorkoutSession saved = sessionRepo.saveAndFlush(session);

            // Track workout started event
            long sessionsCompleted = sessionRepo.countByUserIdAndStatus(userId, "COMPLETED");
            analyticsService.track(userId, "workout_started",
                    Map.of(
                            "session_id", saved.getId().toString(),
                            "is_first_workout", sessionsCompleted == 0
                    ));

            return ResponseEntity.ok(ApiResponse.success(
                    new StartSessionResponse(saved.getId(), saved.getStartedAt())));
        } catch (DataIntegrityViolationException e) {
            // Without a clientId, server-generated UUID collisions are
            // effectively impossible — if this fires we have something
            // worse than a race; surface it.
            if (clientId == null) throw e;

            log.debug("Lost INSERT race for clientId {}, returning existing session", clientId);
            Optional<WorkoutSession> winner = sessionRepo.findById(clientId);
            if (winner.isPresent() && userId.equals(winner.get().getUserId())) {
                WorkoutSession s = winner.get();
                return ResponseEntity.ok(ApiResponse.success(
                        new StartSessionResponse(s.getId(), s.getStartedAt())));
            }
            // Either the row vanished (impossible after PK violation) or
            // belongs to another user (UUID collision across users) — both
            // are pathological. Bubble up.
            throw e;
        }
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

        // Sparkle: provisional PR signal for mid-workout celebration.
        // Compares new set against max(historical best from user_exercise_bests,
        // max already logged in this session). Bodyweight sets (null weightKg)
        // can only sparkle on first-ever logging of the exercise.
        //
        // Also detects REP_PR: when weight matches the session's current max
        // for this exercise AND reps exceed the session's max reps at that weight.
        //
        // This is read-only — does NOT write to user_exercise_bests or pr_events.
        // Those are updated only at session finish by PrWritePathService, which
        // is the single source of truth for PR detection.
        boolean isPr;
        if (request.weightKg() == null) {
            // Bodyweight: sparkle only on first-ever logging of this exercise AND
            // no prior bodyweight set already logged in this session.
            boolean historicalExists = userExerciseBestsRepo
                    .findByUserIdAndExerciseId(userId, request.exerciseId()).isPresent();
            long priorSetsInSession = setLogRepo.findBySessionId(session.getId()).stream()
                    .filter(sl -> sl.getExerciseId().equals(request.exerciseId()))
                    .count();
            isPr = !historicalExists && priorSetsInSession == 0;
        } else {
            BigDecimal historicalBest = userExerciseBestsRepo
                    .findByUserIdAndExerciseId(userId, request.exerciseId())
                    .map(b -> b.getBestWtKg() != null ? b.getBestWtKg() : BigDecimal.ZERO)
                    .orElse(BigDecimal.ZERO);
            BigDecimal currentSessionMax = setLogRepo
                    .findMaxWeightInSessionForExercise(session.getId(), request.exerciseId());
            if (currentSessionMax == null) currentSessionMax = BigDecimal.ZERO;
            BigDecimal barToBeat = historicalBest.max(currentSessionMax);
            isPr = request.weightKg().compareTo(barToBeat) > 0;

            // REP_PR check: weight matches session/historical best but reps are higher
            if (!isPr && request.reps() != null && request.reps() > 0) {
                BigDecimal effectiveBest = currentSessionMax.compareTo(BigDecimal.ZERO) > 0
                        ? currentSessionMax.max(historicalBest)
                        : historicalBest;
                if (request.weightKg().compareTo(effectiveBest) == 0) {
                    // Same weight as best — check if reps beat session max at this weight
                    Integer sessionMaxReps = setLogRepo
                            .findMaxRepsInSessionForExerciseAtWeight(
                                    session.getId(), request.exerciseId(), request.weightKg());
                    // Also check historical reps at this weight
                    Integer historicalReps = userExerciseBestsRepo
                            .findByUserIdAndExerciseId(userId, request.exerciseId())
                            .map(b -> b.getRepsAtBestWt())
                            .orElse(null);
                    int bestReps = Math.max(
                            sessionMaxReps != null ? sessionMaxReps : 0,
                            historicalReps != null ? historicalReps : 0);
                    isPr = request.reps() > bestReps;
                }
            }
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

        // Build PrDetails for celebration popup when isPr=true.
        // Runs PRDetector to get category, payload, and coin amount.
        // This is read-only — pr_events are NOT written until session finish.
        //
        // FIRST_EVER is suppressed from the mid-workout sparkle: it's a silent
        // benchmark, not a user-facing celebration. FIRST_EVER sets still earn
        // 3 coins at session finish via PrWritePathService — only the sparkle
        // is hidden here.
        PrDetails prDetails = null;
        boolean userFacingIsPr = false;
        if (isPr) {
            try {
                var currentBests = userExerciseBestsRepo
                        .findByUserIdAndExerciseId(userId, request.exerciseId())
                        .orElse(null);
                ExerciseType exerciseType = (currentBests != null && currentBests.getExerciseType() != null)
                        ? ExerciseType.valueOf(currentBests.getExerciseType())
                        : ExerciseType.WEIGHTED;
                LoggedSet detectorInput = new LoggedSet(
                        saved.getId(), request.exerciseId(), request.weightKg(),
                        request.reps(), null);
                PRResult result = prDetector.detect(detectorInput, currentBests, exerciseType);
                if (result.isPR() && result.category() != PrCategory.FIRST_EVER) {
                    prDetails = buildPrDetails(result);
                    userFacingIsPr = true;
                }
            } catch (Exception e) {
                log.debug("Could not build PrDetails for sparkle: exercise={}", request.exerciseId(), e);
            }
        }

        return ResponseEntity.ok(ApiResponse.success(
                new LogSetResponse(saved.getId(), userFacingIsPr, prDetails)));
    }

    // ── PATCH /sessions/{id}/log-set/{exerciseId}/{setNumber} ───────
    // NOT @Transactional at the method level. The sync writes (JSONB +
    // prDetectionCompletedAt=null) MUST commit before the @Async cascade
    // runner submits — otherwise the cascade thread races with the sync
    // tx's not-yet-committed null write, and the cascade's "mark complete"
    // can land before the sync's "mark in-flight," leaving the flag stuck
    // on a stale NOW(). Each repo call below manages its own short tx.
    @PatchMapping("/{id}/log-set/{exerciseId}/{setNumber}")
    public ResponseEntity<ApiResponse<EditSetResponse>> editSet(
            @PathVariable UUID id,
            @PathVariable String exerciseId,
            @PathVariable int setNumber,
            @RequestBody @Valid EditSetRequest request,
            Authentication auth) {

        UUID userId = userId(auth);
        WorkoutSession session = requireOwnedAndEditable(id, userId);

        // Parse exercises JSONB
        List<Map<String, Object>> exercises;
        try {
            String raw = session.getExercises();
            exercises = (raw != null && !raw.isBlank())
                    ? objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {})
                    : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Failed to parse exercises JSONB for session={}", id, e);
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXERCISES_JSONB",
                    "Could not parse exercises from session");
        }

        // Find exercise and set
        Map<String, Object> targetEx = null;
        Map<String, Object> targetSet = null;
        for (Map<String, Object> ex : exercises) {
            if (exerciseId.equals(ex.get("exerciseId"))) {
                targetEx = ex;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> sets = (List<Map<String, Object>>) ex.get("sets");
                if (sets != null) {
                    for (Map<String, Object> s : sets) {
                        if (setNumber == ((Number) s.get("setNumber")).intValue()) {
                            targetSet = s;
                            break;
                        }
                    }
                }
                break;
            }
        }

        if (targetEx == null || targetSet == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SET_NOT_FOUND",
                    "Set not found in exercises");
        }

        // Defense-in-depth: a weighted exercise must always have a weight value.
        // The reverse case (bodyweight exercise with non-null weight) is allowed —
        // it represents weighted-bodyweight progression (e.g. Weighted Pull-Ups
        // with belt + 5kg). Only block the data-corruption direction.
        boolean isBodyweight = exerciseRepo.findById(exerciseId)
                .map(Exercise::isBodyweight)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "EXERCISE_NOT_FOUND", "Exercise not found."));
        if (!isBodyweight && request.weightKg() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "WEIGHT_REQUIRED",
                    "This exercise requires a weight value.");
        }

        // Parse setId from JSONB (canonical source after finish)
        UUID setId = targetSet.get("setId") != null
                ? UUID.fromString(targetSet.get("setId").toString())
                : null;

        // Capture old values for cascade (before JSONB update)
        BigDecimal oldWeightKg = targetSet.get("weightKg") != null
                ? new BigDecimal(targetSet.get("weightKg").toString())
                : null;
        int oldReps = ((Number) targetSet.get("reps")).intValue();

        // Pre-edit isPr — read BEFORE the put below so the optimistic
        // value reflects the set's status at request time, not after
        // any field flip we are about to write.
        Boolean preEditIsPr = targetSet.get("isPr") instanceof Boolean b ? b : Boolean.FALSE;

        // ── Optimistic isPr (read-only, no writes) ─────────────────────
        // Preserves pre-edit state — never invents a new PR on edit.
        // The async cascade is authoritative; if the edit legitimately
        // upgrades a non-PR set into a PR, /today polling picks it up
        // when prDetectionCompletedAt is set.
        boolean optimisticIsPr = computeOptimisticIsPr(
                userId, id, setId, preEditIsPr);

        // Update JSONB — including isPr so buildTodayResponse below returns
        // the optimistic flag while prDetectionCompletedAt is null. (Per
        // Commit D's logic: when prDetectionComplete=false, the per-set
        // isPr in the response comes from JSONB.isPr.)
        targetSet.put("weightKg", request.weightKg());
        targetSet.put("reps", request.reps());
        if (request.holdSeconds() != null) {
            targetSet.put("holdSeconds", request.holdSeconds());
        }
        targetSet.put("isPr", optimisticIsPr);

        // Re-serialize exercises JSONB
        try {
            session.setExercises(objectMapper.writeValueAsString(exercises));
        } catch (Exception e) {
            log.error("Failed to serialize exercises JSONB for session={}", id, e);
            throw new RuntimeException("Could not update exercises", e);
        }

        // Mark "cascade in flight" — single UPDATE flushes both the JSONB
        // change and the flag clear in one round-trip via Hibernate dirty
        // checking on the merged entity.
        session.setPrDetectionCompletedAt(null);
        sessionRepo.save(session);

        // ── Submit async cascade (fire-and-forget on prDetectionExecutor) ──
        LoggedSet oldValue = new LoggedSet(setId, exerciseId, oldWeightKg, oldReps, null);
        LoggedSet newValue = new LoggedSet(setId, exerciseId,
                request.weightKg(), request.reps(), request.holdSeconds());
        prEditCascadeAsyncRunner.runEditCascadeAsync(userId, id, setId, oldValue, newValue);

        // Reuse the in-scope session reference; cascade does not write to
        // workout_sessions on the sync path. Pass an override map so the
        // edited set's response carries the optimistic isPr — pr_events for
        // this setId is pre-edit until the async cascade commits, so it
        // alone can't be trusted for THIS set during the cascade window.
        // Guard against null setId (legacy sessions without embedded UUIDs).
        Map<UUID, Boolean> isPrOverrides = setId != null
                ? Map.of(setId, optimisticIsPr)
                : Map.of();
        TodaySessionResponse todayDto = buildTodayResponse(session, userId, isPrOverrides);

        return ResponseEntity.ok(ApiResponse.success(
                new EditSetResponse(setId, optimisticIsPr, todayDto)));
    }

    /**
     * Read-only optimistic isPr prediction for the edited set.
     *
     * <p>Preserves pre-edit state — never invents a new PR on edit.
     * Two stable cases keep returning {@code true}:
     * <ol>
     *   <li>FIRST_EVER on this {@code setId} — that category survives
     *       any value edit and the cascade keeps it active.</li>
     *   <li>The set was already {@code isPr=true} pre-edit (read from
     *       JSONB before the field is overwritten) — the cascade may
     *       downgrade it, but optimistically we keep the trophy
     *       visible until pr_detection_completed_at is repopulated.</li>
     * </ol>
     *
     * <p>Otherwise we return {@code false}. This deliberately drops the
     * old "predict-via-detector against currentBests" path: that path
     * compared the new value to bests that still include the edited
     * set's own contribution, producing "PR against yourself" false
     * positives whenever the edited set was the current bests-holder.
     *
     * <p>The async cascade remains authoritative. If an edit
     * legitimately turns a non-PR set into a PR, the cascade sets
     * {@code isPr=true} on commit and the next {@code /today} fetch
     * picks it up via the existing {@code prDetectionCompletedAt}
     * polling. The user sees the trophy appear within seconds rather
     * than instantly — a deliberate trade for not lying.
     */
    private boolean computeOptimisticIsPr(UUID userId, UUID sessionId, UUID setId,
                                           Boolean preEditIsPr) {
        // 1. FIRST_EVER stays active regardless of edit value.
        List<com.fittribe.api.entity.PrEvent> activeEvents = prEventRepo
                .findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId);
        if (activeEvents.stream().anyMatch(e -> "FIRST_EVER".equals(e.getPrCategory()))) {
            return true;
        }

        // 2. Preserve pre-edit isPr — the async cascade is authoritative.
        return Boolean.TRUE.equals(preEditIsPr);
    }

    // ── DELETE /sessions/{id}/log-set/{exerciseId}/{setNumber} ───────
    // NOT @Transactional at the method level — same rationale as editSet:
    // sync writes must commit before the async cascade submits.
    @DeleteMapping("/{id}/log-set/{exerciseId}/{setNumber}")
    public ResponseEntity<ApiResponse<DeleteSetResponse>> deleteSet(
            @PathVariable UUID id,
            @PathVariable String exerciseId,
            @PathVariable int setNumber,
            Authentication auth) {

        UUID userId = userId(auth);
        WorkoutSession session = requireOwnedAndEditable(id, userId);

        // Parse exercises JSONB
        List<Map<String, Object>> exercises;
        try {
            String raw = session.getExercises();
            exercises = (raw != null && !raw.isBlank())
                    ? objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {})
                    : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Failed to parse exercises JSONB for session={}", id, e);
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXERCISES_JSONB",
                    "Could not parse exercises from session");
        }

        // Find exercise and set, capture setId and oldValue from JSONB for cascade
        Map<String, Object> targetEx = null;
        Map<String, Object> targetSet = null;
        UUID deletedSetId = null;
        LoggedSet oldValue = null;
        for (Map<String, Object> ex : exercises) {
            if (exerciseId.equals(ex.get("exerciseId"))) {
                targetEx = ex;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> sets = (List<Map<String, Object>>) ex.get("sets");
                if (sets != null) {
                    for (int i = 0; i < sets.size(); i++) {
                        Map<String, Object> s = sets.get(i);
                        if (setNumber == ((Number) s.get("setNumber")).intValue()) {
                            targetSet = s;
                            // Parse setId from JSONB
                            deletedSetId = s.get("setId") != null
                                    ? UUID.fromString(s.get("setId").toString())
                                    : null;
                            // Capture for cascade
                            BigDecimal wt = s.get("weightKg") != null
                                    ? new BigDecimal(s.get("weightKg").toString())
                                    : null;
                            int reps = ((Number) s.get("reps")).intValue();
                            oldValue = new LoggedSet(deletedSetId, exerciseId, wt, reps, null);
                            sets.remove(i);
                            break;
                        }
                    }
                }
                break;
            }
        }

        if (targetEx == null || targetSet == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SET_NOT_FOUND",
                    "Set not found in exercises");
        }

        // Enforce: at least one set must remain per exercise
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> remainingSets = (List<Map<String, Object>>) targetEx.get("sets");
        if (remainingSets == null || remainingSets.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DELETE_LAST_SET",
                    "Cannot delete the last set of an exercise");
        }

        // Re-serialize exercises JSONB
        try {
            session.setExercises(objectMapper.writeValueAsString(exercises));
        } catch (Exception e) {
            log.error("Failed to serialize exercises JSONB for session={}", id, e);
            throw new RuntimeException("Could not update exercises", e);
        }

        // Mark "cascade in flight" + commit JSONB write in one UPDATE
        session.setPrDetectionCompletedAt(null);
        sessionRepo.save(session);

        // Submit async cascade. The deleted set is gone from JSONB so there's
        // no per-set isPr to optimistically write — remaining sets keep their
        // existing JSONB.isPr (cascade only supersedes events for the deleted
        // setId, by induction; other sets unaffected).
        if (deletedSetId != null) {
            prEditCascadeAsyncRunner.runDeleteCascadeAsync(
                    userId, session.getId(), deletedSetId, oldValue);
        }

        // Reuse in-scope session; buildTodayResponse skips the pr_events
        // query when prDetectionCompletedAt is null and falls back to JSONB.isPr.
        TodaySessionResponse todayDto = buildTodayResponse(session, userId);

        return ResponseEntity.ok(ApiResponse.success(
                new DeleteSetResponse(true, todayDto)));
    }

    // ── DELETE /sessions/{id}/log-set/exercise/{exerciseId} ──────────
    // NOT @Transactional — same rationale as editSet/deleteSet.
    @DeleteMapping("/{id}/log-set/exercise/{exerciseId}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> deleteExerciseSets(
            @PathVariable UUID id,
            @PathVariable String exerciseId,
            Authentication auth) {

        UUID userId = userId(auth);
        WorkoutSession session = requireOwnedAndEditable(id, userId);

        // Parse exercises JSONB
        List<Map<String, Object>> exercises;
        try {
            String raw = session.getExercises();
            exercises = (raw != null && !raw.isBlank())
                    ? objectMapper.readValue(raw, new TypeReference<List<Map<String, Object>>>() {})
                    : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Failed to parse exercises JSONB for session={}", id, e);
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXERCISES_JSONB",
                    "Could not parse exercises from session");
        }

        // Find exercise and capture all sets as LoggedSet list for cascade (setId from JSONB)
        List<LoggedSet> oldValues = new ArrayList<>();
        Map<String, Object> targetEx = null;
        for (int i = 0; i < exercises.size(); i++) {
            Map<String, Object> ex = exercises.get(i);
            if (exerciseId.equals(ex.get("exerciseId"))) {
                targetEx = ex;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> sets = (List<Map<String, Object>>) ex.get("sets");
                if (sets != null) {
                    for (Map<String, Object> s : sets) {
                        UUID setId = s.get("setId") != null
                                ? UUID.fromString(s.get("setId").toString())
                                : null;
                        BigDecimal wt = s.get("weightKg") != null
                                ? new BigDecimal(s.get("weightKg").toString())
                                : null;
                        int reps = ((Number) s.get("reps")).intValue();
                        oldValues.add(new LoggedSet(setId, exerciseId, wt, reps, null));
                    }
                }
                exercises.remove(i);
                break;
            }
        }

        if (targetEx == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EXERCISE_NOT_FOUND",
                    "Exercise not found in session");
        }

        // Re-serialize exercises JSONB
        try {
            session.setExercises(objectMapper.writeValueAsString(exercises));
        } catch (Exception e) {
            log.error("Failed to serialize exercises JSONB for session={}", id, e);
            throw new RuntimeException("Could not update exercises", e);
        }

        // Mark "cascade in flight" + commit JSONB write in one UPDATE
        session.setPrDetectionCompletedAt(null);
        sessionRepo.save(session);

        // Submit async cascade
        prEditCascadeAsyncRunner.runExerciseDeleteCascadeAsync(
                userId, session.getId(), exerciseId, oldValues);

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

        long startNanos = System.nanoTime();
        UUID userId = userId(auth);
        WorkoutSession session = requireOwned(id, userId);

        log.info("finish START sessionId={} userId={}", id, userId);

        // Idempotency: already finished — return saved data without reprocessing
        if ("COMPLETED".equals(session.getStatus())) {
            return ResponseEntity.ok(ApiResponse.success(buildExistingResponse(session)));
        }

        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_IN_PROGRESS",
                    "This session is already " + session.getStatus() + ".");
        }
        log.info("finish T+{}ms idempotency_check_done sessionId={}",
                (System.nanoTime() - startNanos) / 1_000_000, id);

        // ── Optional name update ─────────────────────────────────────────
        // The active workout screen lets users edit the workout name (e.g.
        // they forgot to set a meaningful name on the Custom Workout screen
        // before tapping Start). The updated name is only persisted at
        // finish — discarded sessions don't get the name update, which
        // matches the principle: no point saving the workout if the user
        // walked away.
        if (request.name() != null) {
            String trimmedName = request.name().trim();
            if (!trimmedName.isEmpty()) {
                if (trimmedName.length() > 60) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "NAME_TOO_LONG", "Workout name must be 60 characters or less.");
                }
                session.setName(trimmedName);
            }
        }

        // ── Compute totals and build exercises JSONB from request ─────
        int totalSets;
        BigDecimal totalVolumeKg;
        String exercisesJson;

        List<ExerciseLogRequest> exercises = request.exercises();

        // Pre-fetch set_log UUIDs keyed by "exerciseId:setNumber". Embedded into
        // each per-set JSONB entry so PATCH/DELETE during the edit window can read
        // setId from the JSONB without touching set_logs (deleted at end of finish).
        Map<String, UUID> setIdByExerciseAndNumber = (exercises != null && !exercises.isEmpty())
                ? setLogRepo.findBySessionId(id).stream()
                        .collect(Collectors.toMap(
                                sl -> sl.getExerciseId() + ":" + sl.getSetNumber(),
                                SetLog::getId,
                                (a, b) -> a))
                : Map.of();

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

                // isPr is intentionally false at finish time. PrWritePathService runs
                // AFTER this block and is the sole authority on PR detection — it writes
                // pr_events and updates user_exercise_bests. The frontend should rely
                // on pr_events or the /finish response to learn which exercises PR'd.
                boolean isPr = false;

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
                            m.put("setId",     setIdByExerciseAndNumber.get(
                                    ex.exerciseId() + ":" + s.setNumber()));
                            // Frontend-supplied display-only optimistic flag.
                            // Persisted as-is; never read by coin or PR-write logic.
                            // /today enrichment trusts this until pr_detection_completed_at
                            // is set, then prefers pr_events as authoritative.
                            m.put("isPr",      s.isPr() != null && s.isPr());
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
            LocalDate monday = LocalDate.now(Zones.APP_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            int weekNum  = weekNumberFor(u, monday);
            int wkGoal   = u.getWeeklyGoal() != null ? u.getWeeklyGoal() : 4;
            Instant from = monday.atStartOfDay(Zones.APP_ZONE).toInstant();
            Instant to   = monday.plusDays(7).atStartOfDay(Zones.APP_ZONE).toInstant();

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
        log.info("finish T+{}ms core_save_done sessionId={}",
                (System.nanoTime() - startNanos) / 1_000_000, id);
        // Core tx has committed. `session` and `core.user()` are detached.
        // Reading their scalar fields still works; writes must go via
        // atomic SQL (updateStreak) or accept merge semantics.

        final User user              = core.user();
        final int prevStreak         = user.getStreak();
        final int weekNumber         = core.weekNumber();
        final int weeklyGoal         = core.weeklyGoal();
        final boolean weeklyGoalHit  = core.weeklyGoalHit();
        final int count              = core.count();
        final Instant weekFrom       = core.weekFrom();
        final Instant weekTo         = core.weekTo();

        // ────────────────────────────────────────────────────────────────
        // SYNC FLOOR — only what the response strictly depends on:
        //   1. Streak update + max-streak update (response carries `streak`)
        // Everything else moves to SessionFinishPostProcessor.
        // ────────────────────────────────────────────────────────────────

        // Streak update — atomic SQL to avoid detached-entity merge races
        // with RankService.checkAndPromote (which runs in the pipeline).
        int newStreak = 0;
        try {
            LocalDate finishDateIst = session.getFinishedAt()
                    .atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
            long otherSessionsToday = sessionRepo.countOtherCompletedOnSameDay(
                    userId, session.getId(), finishDateIst);
            if (otherSessionsToday == 0) {
                newStreak = Math.max(0, user.getStreak() + 1);
                userRepo.updateStreak(userId, newStreak);
                userRepo.updateMaxStreakIfHigher(userId, newStreak);
                user.setStreak(newStreak); // keep in-memory value in sync for the response
            } else {
                // Second session same IST day — streak already counted, preserve current value
                newStreak = user.getStreak();
            }
        } catch (Exception e) {
            log.error("Failed to update streak for user={}", userId, e);
        }
        log.info("finish T+{}ms streak_done sessionId={}",
                (System.nanoTime() - startNanos) / 1_000_000, id);

        // AI insight runs on its own pool (aiInsightExecutor) — fire-and-forget.
        // The dispatch itself is ~1ms; the slow OpenAI call runs on the pool.
        // Frontend polls /ai/insight/{sessionId} for the result.
        try {
            aiService.generateInsightAsync(userId, session.getId());
        } catch (Exception e) {
            log.error("Failed to enqueue async AI insight for session={}", id, e);
        }
        String aiCoachInsight = null; // populated async; client polls /ai/insight/{sessionId}

        // ── Build context + dispatch async post-finish pipeline ─────────
        // Everything else (clearTodayStatus, streakSnapshot, strengthSnapshot,
        // rankPromotion, baseCoinAwards, streakMilestoneFeed, prDetection,
        // workoutFinishedFeed, groupProgress, nextWeekPlan, setLogCleanup)
        // runs on prDetectionExecutor.
        List<LoggedSet> loggedSetsForCtx = new ArrayList<>();
        if (exercises != null) {
            for (ExerciseLogRequest ex : exercises) {
                if (ex.sets() == null) continue;
                for (SetLogRequest setReq : ex.sets()) {
                    UUID setId = setIdByExerciseAndNumber.get(
                            ex.exerciseId() + ":" + setReq.setNumber());
                    loggedSetsForCtx.add(new LoggedSet(
                            setId, ex.exerciseId(),
                            setReq.weightKg(), setReq.reps(), null));
                }
            }
        }

        SessionFinishContext postCtx = new SessionFinishContext(
                userId,
                id,
                weekNumber,
                weeklyGoalHit,
                count,
                newStreak,
                prevStreak,
                totalVolumeKg,
                totalSets,
                loggedSetsForCtx,
                session.getFinishedAt(),
                weeklyGoal,
                weekFrom,
                weekTo);

        // Fire-and-forget — runs on prDetectionExecutor.
        // Called via the postProcessor bean (not this) so Spring's @Async
        // proxy fires.
        postProcessor.runPostFinishPipeline(postCtx);

        // Track workout completed event
        analyticsService.trackWithSession(userId, "workout_completed", session.getId().toString(),
                Map.of(
                        "streak_count", newStreak,
                        "exercises_count", exercises != null ? exercises.size() : 0,
                        "duration_minutes", request.durationMins() != null ? request.durationMins() : 0
                ));

        log.info("finish END T+{}ms response_built sessionId={}",
                (System.nanoTime() - startNanos) / 1_000_000, id);
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

    // ── PATCH /sessions/{id} ─────────────────────────────────────────
    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<TodaySessionResponse>> updateSession(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateSessionRequest request,
            Authentication auth) {

        UUID userId = userId(auth);
        WorkoutSession session = requireOwned(id, userId);

        if (!"COMPLETED".equals(session.getStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "SESSION_NOT_COMPLETE", "Can only edit completed sessions.");
        }

        // Edit-window guard: allow edits until 05:00 IST the day after the session finished
        Instant cutoff = session.getFinishedAt()
                .atZone(Zones.APP_ZONE)
                .toLocalDate()
                .plusDays(1)
                .atTime(5, 0)
                .atZone(Zones.APP_ZONE)
                .toInstant();
        if (Instant.now().isAfter(cutoff)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "EDIT_WINDOW_EXPIRED",
                    "Sessions can only be edited until 5 AM the day after they finished.");
        }

        // Timestamp validation
        if (!request.finishedAt().isAfter(request.startedAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "INVALID_TIME_RANGE", "finishedAt must be after startedAt.");
        }
        Instant nowPlusSkew = Instant.now().plusSeconds(5);
        if (request.startedAt().isAfter(nowPlusSkew)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "START_IN_FUTURE", "startedAt cannot be in the future.");
        }
        if (request.finishedAt().isAfter(nowPlusSkew)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "FINISH_IN_FUTURE", "finishedAt cannot be in the future.");
        }
        long durationMins = Math.round(
                (request.finishedAt().toEpochMilli() - request.startedAt().toEpochMilli()) / 60000.0);
        if (durationMins < 1 || durationMins > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "DURATION_OUT_OF_RANGE", "Session duration must be between 1 and 120 minutes.");
        }

        session.setStartedAt(request.startedAt());
        session.setFinishedAt(request.finishedAt());
        session.setDurationMins((int) durationMins);
        sessionRepo.save(session);

        return ResponseEntity.ok(ApiResponse.success(buildTodayResponse(session, userId)));
    }

    // ── POST /sessions/{id}/discard ───────────────────────────────────
    @PostMapping("/{id}/discard")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> discardSession(
            @PathVariable UUID id,
            Authentication auth) {

        WorkoutSession session = requireOwnedInProgress(id, userId(auth));
        setLogRepo.deleteBySessionId(session.getId());
        sessionRepo.delete(session);

        return ResponseEntity.ok(ApiResponse.success(Map.of("discarded", true)));
    }

    // ── GET /sessions/today ───────────────────────────────────────────
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<TodaySessionResponse>> todaySession(Authentication auth) {
        UUID userId = userId(auth);

        ZoneId IST = ZoneId.of("Asia/Kolkata");

        // 1. Look for any IN_PROGRESS session for this user,
        //    regardless of calendar day. A user may have started
        //    a session yesterday and not yet finished it — they
        //    should still be able to resume.
        WorkoutSession inProgress = sessionRepo
                .findFirstByUserIdAndStatusOrderByStartedAtDesc(userId, "IN_PROGRESS")
                .orElse(null);

        if (inProgress != null) {
            // Stale-week backstop: if the IN_PROGRESS session started
            // in a previous ISO week (IST), the Sunday cron should
            // have abandoned it but didn't. Self-heal.
            LocalDate sessionDate       = inProgress.getStartedAt().atZone(IST).toLocalDate();
            LocalDate sessionWeekMonday = sessionDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate currentWeekMonday = LocalDate.now(IST).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

            if (sessionWeekMonday.isBefore(currentWeekMonday)) {
                inProgress.setStatus("ABANDONED");
                sessionRepo.save(inProgress);
                log.info("Self-healed stale IN_PROGRESS session {} from week {} to ABANDONED",
                        inProgress.getId(), sessionWeekMonday);
                // Fall through to look for a COMPLETED session today instead.
            } else {
                // Current-week IN_PROGRESS — return it regardless of
                // which day it started.
                return ResponseEntity.ok(ApiResponse.success(buildTodayResponse(inProgress, userId)));
            }
        }

        // 2. No active session — look for a COMPLETED session today
        //    (IST). This drives the post-workout home card UX, which
        //    is inherently a today-scoped behavior.
        LocalDate today  = Zones.fitnessDayNow();
        Instant dayStart = Zones.fitnessDayStart(today);
        Instant dayEnd   = Zones.fitnessDayStart(today.plusDays(1));

        WorkoutSession session = sessionRepo
                .findFirstByUserIdAndStartedAtBetweenOrderByStartedAtDesc(userId, dayStart, dayEnd)
                .orElse(null);

        if (session == null) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }

        // 3. Defense-in-depth: never surface ABANDONED via /today.
        //    (Should not happen in practice — ABANDONED is filtered
        //    out elsewhere — but cheap to guard.)
        if ("ABANDONED".equals(session.getStatus())) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }

        return ResponseEntity.ok(ApiResponse.success(buildTodayResponse(session, userId)));
    }

    // ── GET /sessions/history ─────────────────────────────────────────
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<SessionHistoryItem>>> history(Authentication auth) {
        UUID userId = userId(auth);

        List<WorkoutSession> sessions = sessionRepo
                .findTop20ByUserIdAndStatusOrderByStartedAtDesc(userId, "COMPLETED");

        if (sessions.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }

        List<UUID> sessionIds = sessions.stream()
                .map(WorkoutSession::getId).collect(Collectors.toList());

        // Query 2: feedback — one batch for all sessions
        Map<UUID, SessionFeedback> feedbackBySession = feedbackRepo.findBySessionIdIn(sessionIds)
                .stream()
                .collect(Collectors.toMap(SessionFeedback::getSessionId, fb -> fb));

        // Query 3: exercise catalog — needed for muscleGroup lookup per exercise.
        // Uses findAll() because the catalog is small (19 exercises) and stable.
        // TODO: if the catalog grows significantly, switch to findAllById(distinctExerciseIds)
        //   where distinctExerciseIds is collected from session.getExercises() JSONB in a
        //   first pass — avoids fetching unused rows at the cost of parsing JSONB twice.
        List<Exercise> allExercises = exerciseRepo.findAll();
        Map<String, String> muscleGroupById = allExercises.stream()
                .collect(Collectors.toMap(Exercise::getId,
                        e -> e.getMuscleGroup() != null ? e.getMuscleGroup() : ""));
        Map<String, Boolean> bodyweightById = allExercises.stream()
                .collect(Collectors.toMap(Exercise::getId, Exercise::isBodyweight));

        // Query 4: pr_events — one batch for all sessions.
        // week_start IN clause is required to hit the correct RANGE partitions.
        // Uses UTC + Monday, matching PrWritePathService.weekStartFor() exactly:
        //   LocalDate.ofInstant(instant, ZoneOffset.UTC).with(previousOrSame(DayOfWeek.MONDAY))
        Set<LocalDate> weekStarts = sessions.stream()
                .filter(s -> s.getStartedAt() != null)
                .map(s -> LocalDate.ofInstant(s.getStartedAt(), ZoneOffset.UTC)
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                .collect(Collectors.toSet());
        Map<UUID, List<PrEvent>> prBySession = prEventRepo
                .findActiveByUserIdAndSessionIdInAndWeekStartIn(userId, sessionIds, weekStarts)
                .stream()
                .collect(Collectors.groupingBy(PrEvent::getSessionId));

        // Build response items in memory — no further DB calls
        List<SessionHistoryItem> items = new ArrayList<>();
        for (WorkoutSession session : sessions) {
            List<PrEvent> prs = prBySession.getOrDefault(session.getId(), List.of());

            // Set-level PR lookup: non-FIRST_EVER events, keyed by set_id
            Set<UUID> prSetIds = prs.stream()
                    .filter(pe -> !"FIRST_EVER".equals(pe.getPrCategory()))
                    .map(PrEvent::getSetId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            // Exercise-level first-ever lookup: FIRST_EVER events, keyed by exercise_id
            Set<String> firstEverExerciseIds = prs.stream()
                    .filter(pe -> "FIRST_EVER".equals(pe.getPrCategory()))
                    .map(PrEvent::getExerciseId)
                    .collect(Collectors.toSet());

            int firstEverCount = (int) prs.stream()
                    .filter(pe -> "FIRST_EVER".equals(pe.getPrCategory())).count();
            int prCount = (int) prs.stream()
                    .filter(pe -> !"FIRST_EVER".equals(pe.getPrCategory())).count();

            // Parse exercises from the JSONB snapshot written at finish time.
            // This is the same durable source used by buildTodayResponse().
            List<SessionHistoryItem.ExerciseGroup> exercises =
                    parseSnapshotIntoExerciseGroups(
                            session.getExercises(), prSetIds, firstEverExerciseIds,
                            muscleGroupById, bodyweightById);

            LinkedHashSet<String> muscleGroupsSeen = new LinkedHashSet<>();
            for (SessionHistoryItem.ExerciseGroup eg : exercises) {
                if (eg.muscleGroup() != null && !eg.muscleGroup().isBlank()) {
                    muscleGroupsSeen.add(eg.muscleGroup());
                }
            }

            String date = session.getStartedAt() != null
                    ? LocalDate.ofInstant(session.getStartedAt(), ZoneOffset.UTC).toString()
                    : null;

            SessionFeedback fb = feedbackBySession.get(session.getId());
            FeedbackInfo feedback = fb != null
                    ? new FeedbackInfo(fb.getRating(), fb.getNotes(), fb.getCreatedAt())
                    : null;

            items.add(new SessionHistoryItem(
                    session.getId(),
                    session.getName(),
                    date,
                    session.getTotalVolumeKg(),
                    session.getTotalSets() != null ? session.getTotalSets() : 0,
                    session.getDurationMins(),
                    session.getStreak(),
                    new ArrayList<>(muscleGroupsSeen),
                    firstEverCount,
                    prCount,
                    exercises,
                    feedback));
        }

        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @PostMapping(value = "/{sessionId}/share-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> generateShareImage(
            @PathVariable UUID sessionId,
            @RequestParam(required = false) MultipartFile photo,
            Authentication auth) {
        try {
            UUID userId = userId(auth);

            log.info("share-image called: sessionId={} hasPhoto={}", sessionId, (photo != null && !photo.isEmpty()));

            WorkoutSession session = sessionRepo.findById(sessionId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "SESSION_NOT_FOUND", "Session not found"));

            if (!session.getUserId().equals(userId)) {
                throw new ApiException(HttpStatus.FORBIDDEN,
                        "FORBIDDEN", "Cannot generate share image for another user's session");
            }

            User user = userRepo.findById(userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "USER_NOT_FOUND", "User not found"));

            byte[] imageBytes = shareImageService.generateShareImage(session, user, photo);

            return ResponseEntity.ok()
                    .header("Content-Type", "image/png")
                    .header("Content-Disposition", "inline; filename=\"share-card.png\"")
                    .body(imageBytes);

        } catch (IOException e) {
            log.error("Failed to generate share image", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "IMAGE_GENERATION_FAILED", "Failed to generate share image");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Parses the {@code workout_sessions.exercises} JSONB snapshot into typed
     * {@link SessionHistoryItem.ExerciseGroup} objects, enriching each set with
     * an {@code isPr} flag derived from the caller's pre-fetched PR set-ID set.
     *
     * <p>JSONB set shape: {@code {"setId":"<uuid>","weightKg":<n>,"reps":<n>,"setNumber":<n>}}
     * JSONB exercise shape: {@code {"exerciseId":"<id>","exerciseName":"<name>","sets":[...]}}
     */
    @SuppressWarnings("unchecked")
    private List<SessionHistoryItem.ExerciseGroup> parseSnapshotIntoExerciseGroups(
            String rawJson,
            Set<UUID> prSetIds,
            Set<String> firstEverExerciseIds,
            Map<String, String> muscleGroupById,
            Map<String, Boolean> bodyweightById) {

        if (rawJson == null || rawJson.isBlank()) return List.of();

        try {
            List<Map<String, Object>> parsed = objectMapper.readValue(
                    rawJson, new TypeReference<List<Map<String, Object>>>() {});

            List<SessionHistoryItem.ExerciseGroup> result = new ArrayList<>();
            for (Map<String, Object> ex : parsed) {
                String exerciseName = (String) ex.get("exerciseName");
                String exerciseId   = (String) ex.get("exerciseId");
                String muscleGroup  = muscleGroupById.getOrDefault(
                        exerciseId != null ? exerciseId : "", "");
                boolean firstEver   = exerciseId != null && firstEverExerciseIds.contains(exerciseId);
                boolean isBodyweight = exerciseId != null
                        && Boolean.TRUE.equals(bodyweightById.get(exerciseId));

                List<SessionHistoryItem.SetSummary> sets = new ArrayList<>();
                Object setsRaw = ex.get("sets");
                if (setsRaw instanceof List<?> setsList) {
                    for (Object setObj : setsList) {
                        if (!(setObj instanceof Map<?, ?> rawSet)) continue;
                        Map<String, Object> setData = (Map<String, Object>) rawSet;

                        UUID setId = null;
                        Object setIdRaw = setData.get("setId");
                        if (setIdRaw != null) {
                            try { setId = UUID.fromString(setIdRaw.toString()); }
                            catch (IllegalArgumentException ignored) {}
                        }

                        BigDecimal kg = null;
                        Object kgRaw = setData.get("weightKg");
                        if (kgRaw instanceof Number n) {
                            kg = BigDecimal.valueOf(n.doubleValue());
                        }

                        int reps = 0;
                        Object repsRaw = setData.get("reps");
                        if (repsRaw instanceof Number n) reps = n.intValue();

                        boolean isPr = setId != null && prSetIds.contains(setId);
                        sets.add(new SessionHistoryItem.SetSummary(setId, kg, reps, isPr));
                    }
                }

                result.add(new SessionHistoryItem.ExerciseGroup(
                        exerciseName != null ? exerciseName : exerciseId,
                        muscleGroup, firstEver, isBodyweight, sets));
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse exercises JSONB snapshot: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Builds a {@link TodaySessionResponse} from a loaded session.
     * Shared by GET /sessions/today, PATCH /log-set, and DELETE /log-set
     * so all three return the same shape and the frontend can replace
     * its state atomically.
     */
    private TodaySessionResponse buildTodayResponse(WorkoutSession session, UUID userId) {
        return buildTodayResponse(session, userId, Map.of());
    }

    /**
     * Build the TodaySessionResponse, honouring per-set isPr overrides.
     *
     * <p>The override map exists for the edit-set endpoint: it supplies the
     * optimistic isPr for the just-edited set BEFORE the async cascade has
     * run. For other sets, pr_events is the authoritative source — the
     * cascade only mutates events for the edited setId, so non-edited sets'
     * pr_events rows are always current relative to this request.
     *
     * <p>Empty map = "no overrides; use pr_events for every set." Used by
     * todaySession, updateSession, deleteSet, deleteExerciseSets.
     */
    private TodaySessionResponse buildTodayResponse(WorkoutSession session, UUID userId,
                                                     Map<UUID, Boolean> isPrOverrides) {
        // TEMP DEBUG: Log session state at entry to diagnose reconstruction issue
        log.info("DEBUG buildTodayResponse ENTRY sessionId={} status={}",
                session.getId(), session.getStatus());
        log.info("DEBUG buildTodayResponse exercises={} plannedExercises={}",
                (session.getExercises() != null && !session.getExercises().isBlank())
                    ? "length:" + session.getExercises().length()
                    : "NULL/EMPTY",
                (session.getPlannedExercises() != null && !session.getPlannedExercises().isBlank())
                    ? "length:" + session.getPlannedExercises().length()
                    : "NULL/EMPTY");

        // TEMP debug: confirms 3-arg overload is reached and shows what state
        // the per-set isPr resolver will see. Remove after isPr trophies bug fix.
        log.info("DEBUG buildTodayResponse sessionId={} prDetectionCompletedAt={} overrides.size={}",
                session.getId(), session.getPrDetectionCompletedAt(), isPrOverrides.size());

        // For COMPLETED sessions, set_logs is wiped by the post-finish pipeline
        // (Option Y cleanup) and totalSets is always non-null in the entity, so
        // the logs.size() fallback below is unreachable — skip the round-trip.
        // For IN_PROGRESS sessions, totalSets may still be null mid-workout, so
        // we keep the query.
        List<SetLog> logs = "COMPLETED".equals(session.getStatus())
                ? List.of()
                : setLogRepo.findBySessionId(session.getId());

        User user = userRepo.findById(userId).orElseThrow(() -> ApiException.notFound("User"));

        ZoneId IST = ZoneId.of("Asia/Kolkata");
        LocalDate monday   = LocalDate.now(IST).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant weekFrom   = monday.atStartOfDay(IST).toInstant();
        Instant weekTo     = monday.plusDays(7).atStartOfDay(IST).toInstant();
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
            if (raw != null && !raw.isBlank()) {
                // TEMP DEBUG: Log the shape of raw plannedExercises to debug parsing errors
                String preview = raw.length() > 200 ? raw.substring(0, 200) : raw;
                log.info("DEBUG plannedExercises raw (first 200 chars): {}", preview);
                plannedExercises = objectMapper.readValue(raw, new TypeReference<>() {});
            } else {
                plannedExercises = null;
            }
        } catch (Exception e) {
            log.error("PLANNEDEXERCISES PARSE FAILED sessionId={} - Exception: {}",
                    session.getId(), e.getMessage(), e);
            plannedExercises = null;
        }

        FeedbackInfo feedback = feedbackRepo.findBySessionId(session.getId())
                .map(fb -> new FeedbackInfo(fb.getRating(), fb.getNotes(), fb.getCreatedAt()))
                .orElse(null);

        // Parse exercises JSONB and enrich with set-level PR flags from pr_events.
        // For IN_PROGRESS sessions with null exercises, reconstruct from set_logs.
        List<Map<String, Object>> exercises;
        try {
            String rawEx = session.getExercises();
            if (rawEx != null && !rawEx.isBlank()) {
                List<Map<String, Object>> parsed = objectMapper.readValue(
                        rawEx, new TypeReference<List<Map<String, Object>>>() {});

                // Batch-fetch isBodyweight for every distinct exerciseId in
                // this session — single round-trip, computed at read time
                // from the canonical Exercise catalog (JSONB stays as-is
                // for backward compat with existing sessions).
                List<String> exerciseIds = parsed.stream()
                        .map(e -> (String) e.get("exerciseId"))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
                Map<String, Boolean> bodyweightById = exerciseIds.isEmpty()
                        ? Map.of()
                        : exerciseRepo.findAllById(exerciseIds).stream()
                                .collect(Collectors.toMap(Exercise::getId, Exercise::isBodyweight));

                // pr_events is the authoritative per-set isPr source. Query it
                // unconditionally — for non-edited sets in any state, this is
                // always correct. For the just-edited set during the async
                // cascade window, the caller's isPrOverrides map supplies the
                // optimistic value that wins below.
                //
                // FIRST_EVER events are filtered out: those mark the first time
                // a user logs an exercise (analytics signal) but should not
                // surface as trophies on the Summary screen.
                java.util.Set<UUID> prSetIds = prEventRepo
                        .findBySessionIdAndSupersededAtIsNull(session.getId())
                        .stream()
                        .filter(pe -> !"FIRST_EVER".equals(pe.getPrCategory()))
                        .map(pe -> pe.getSetId())
                        .collect(java.util.stream.Collectors.toSet());

                // TEMP debug: shows how many active non-FIRST_EVER PRs the
                // per-set loop will use as authoritative set-id matches.
                // Remove after isPr trophies bug fix.
                log.info("DEBUG buildTodayResponse sessionId={} prSetIds.size={} prSetIds={}",
                        session.getId(), prSetIds.size(), prSetIds);

                // Whether the async PR detection cascade has finished writing
                // pr_events for this session. Read once outside the loop —
                // every set in the session shares the same gate.
                boolean cascadeDone = session.getPrDetectionCompletedAt() != null;

                exercises = new ArrayList<>();
                for (Map<String, Object> ex : parsed) {
                    Map<String, Object> enrichedEx = new LinkedHashMap<>(ex);
                    enrichedEx.remove("isPr");

                    boolean anySetIsPr = false;
                    Object setsRaw = enrichedEx.get("sets");
                    if (setsRaw instanceof List<?> setsList) {
                        List<Map<String, Object>> enrichedSets = new ArrayList<>();
                        for (Object setObj : setsList) {
                            if (!(setObj instanceof Map<?, ?> setMap)) continue;
                            @SuppressWarnings("unchecked")
                            Map<String, Object> enrichedSet = new LinkedHashMap<>((Map<String, Object>) setMap);
                            Object setIdRaw = enrichedSet.get("setId");
                            UUID setId = null;
                            if (setIdRaw != null) {
                                try {
                                    setId = UUID.fromString(setIdRaw.toString());
                                } catch (IllegalArgumentException ignored) {}
                            }
                            // Capture the JSONB-supplied optimistic flag BEFORE
                            // the put() below overwrites it. The write side at
                            // finishSession (lines 911-915) persists the
                            // frontend's computePr result here as a display-only
                            // hint while the async cascade runs.
                            Object jsonbIsPrRaw = enrichedSet.get("isPr");
                            boolean jsonbIsPr = Boolean.TRUE.equals(jsonbIsPrRaw);

                            // Per-set isPr resolver — 4 branches, evaluated in
                            // order:
                            //
                            //   1. Edit-set override window: caller supplied
                            //      the optimistic isPr because pr_events for
                            //      that setId is pre-edit until the edit-set
                            //      cascade commits. Wins for edited sets only;
                            //      empty map for every other caller.
                            //
                            //   2. pr_events row exists (non-FIRST_EVER, not
                            //      superseded): cascade has confirmed this set
                            //      as a PR. Authoritative.
                            //
                            //   3. Cascade still in flight (prDetectionCompletedAt
                            //      is null): fall back to the JSONB flag the
                            //      frontend supplied at finish. Lets trophies
                            //      render immediately for the obvious cases
                            //      (within-session WEIGHT_PR / REP_PR) while the
                            //      backend cascade is still running. Matches the
                            //      write-side contract documented at
                            //      finishSession lines 911-914.
                            //
                            //   4. Cascade done, no pr_events row: not a PR.
                            //      pr_events is authoritative once
                            //      prDetectionCompletedAt is set; ignore JSONB
                            //      (which is stale because it's never rewritten
                            //      after finish).
                            boolean isPr;
                            if (setId != null && isPrOverrides.containsKey(setId)) {
                                isPr = Boolean.TRUE.equals(isPrOverrides.get(setId));
                            } else if (setId != null && prSetIds.contains(setId)) {
                                isPr = true;
                            } else if (!cascadeDone) {
                                isPr = jsonbIsPr;
                            } else {
                                isPr = false;
                            }
                            enrichedSet.put("isPr", isPr);
                            if (isPr) anySetIsPr = true;
                            enrichedSets.add(enrichedSet);
                        }
                        enrichedEx.put("sets", enrichedSets);
                    }

                    enrichedEx.put("prAchieved", anySetIsPr);
                    enrichedEx.put("isBodyweight",
                            bodyweightById.getOrDefault((String) ex.get("exerciseId"), false));
                    exercises.add(enrichedEx);
                }
            } else if ("IN_PROGRESS".equals(session.getStatus())) {
                // For IN_PROGRESS sessions with no persisted exercises JSONB,
                // reconstruct from set_logs + plannedExercises.
                log.info("DEBUG RECONSTRUCTION TRIGGERED sessionId={} exercises is null/empty",
                        session.getId());
                List<SetLog> sessionLogs = setLogRepo.findBySessionId(session.getId());
                log.info("DEBUG RECONSTRUCTION sessionLogs.size={} plannedExercises={}",
                        sessionLogs.size(),
                        (session.getPlannedExercises() != null && !session.getPlannedExercises().isBlank())
                            ? "present"
                            : "NULL/EMPTY");
                exercises = reconstructExercisesFromSetLogs(
                        session.getId(),
                        session.getPlannedExercises(),
                        sessionLogs,
                        userId,
                        isPrOverrides);
                log.info("DEBUG RECONSTRUCTION COMPLETE sessionId={} reconstructed {} exercises",
                        session.getId(), exercises.size());
            } else {
                log.info("DEBUG RECONSTRUCTION SKIPPED sessionId={} status={} (not IN_PROGRESS)",
                        session.getId(), session.getStatus());
                exercises = List.of();
            }
        } catch (Exception e) {
            log.error("RECONSTRUCTION FAILED sessionId={} - Exception: {}",
                    session.getId(), e.getMessage(), e);
            exercises = List.of();
        }

        boolean prDetectionComplete = session.getPrDetectionCompletedAt() != null;

        // TEMP DEBUG: Final state before building response
        log.info("DEBUG buildTodayResponse EXIT sessionId={} final exercises.size={}",
                session.getId(), exercises.size());

        return new TodaySessionResponse(
                session.getId(),
                session.getName(),
                date,
                session.getTotalVolumeKg() != null ? session.getTotalVolumeKg() : BigDecimal.ZERO,
                session.getTotalSets()     != null ? session.getTotalSets()     : logs.size(),
                session.getDurationMins(),
                session.getStartedAt(),
                session.getFinishedAt(),
                session.getAiInsight(),
                session.getStatus(),
                user.getStreak(),
                completedThisWeek,
                swapLog,
                session.getSource(),
                plannedExercises,
                exercises,
                feedback,
                prDetectionComplete);
    }

    /**
     * Reconstruct exercises array from set_logs for IN_PROGRESS sessions.
     * Returns UNION of: all plannedExercises (with logged sets + suggested unlogged)
     * plus any exerciseIds in set_logs but not in plan (mid-session additions).
     *
     * Preserves plannedExercises order; appends mid-session additions in order of
     * their first set_log created_at.
     */
    private List<Map<String, Object>> reconstructExercisesFromSetLogs(
            UUID sessionId,
            String plannedExercisesJson,
            List<SetLog> setLogs,
            UUID userId,
            Map<UUID, Boolean> isPrOverrides) {

        // Parse plannedExercises
        List<Map<String, Object>> planned = new ArrayList<>();
        Set<String> plannedExIds = new HashSet<>();
        try {
            if (plannedExercisesJson != null && !plannedExercisesJson.isBlank()) {
                planned = objectMapper.readValue(plannedExercisesJson,
                        new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> ex : planned) {
                    String exId = (String) ex.get("exerciseId");
                    if (exId != null) plannedExIds.add(exId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse plannedExercises for reconstruction, sessionId={}", sessionId, e);
        }

        // Group set_logs by exerciseId, preserving order of first set_log per exercise
        Map<String, List<SetLog>> logsByExercise = new LinkedHashMap<>();
        Map<String, Instant> exerciseFirstLogTime = new HashMap<>();
        for (SetLog log : setLogs) {
            logsByExercise.computeIfAbsent(log.getExerciseId(), k -> new ArrayList<>())
                    .add(log);
            exerciseFirstLogTime.putIfAbsent(log.getExerciseId(), log.getLoggedAt());
        }

        // Batch-fetch isBodyweight for all exercises (planned + logged)
        Set<String> allExerciseIds = new HashSet<>(plannedExIds);
        allExerciseIds.addAll(logsByExercise.keySet());
        Map<String, Boolean> bodyweightById = allExerciseIds.isEmpty()
                ? Map.of()
                : exerciseRepo.findAllById(allExerciseIds).stream()
                        .collect(Collectors.toMap(Exercise::getId, Exercise::isBodyweight));

        // Fetch PR events for this session
        java.util.Set<UUID> prSetIds = prEventRepo
                .findBySessionIdAndSupersededAtIsNull(sessionId)
                .stream()
                .filter(pe -> !"FIRST_EVER".equals(pe.getPrCategory()))
                .map(pe -> pe.getSetId())
                .collect(java.util.stream.Collectors.toSet());

        List<Map<String, Object>> result = new ArrayList<>();

        // 1. Add all plannedExercises in order, with logged sets enriched
        for (Map<String, Object> plannedEx : planned) {
            Map<String, Object> enrichedEx = new LinkedHashMap<>(plannedEx);
            String exId = (String) enrichedEx.get("exerciseId");
            List<SetLog> logsForEx = logsByExercise.getOrDefault(exId, List.of());

            // Build sets array: suggested sets from plan + logged sets from set_logs
            List<Map<String, Object>> setsArray = new ArrayList<>();
            Object setsRaw = enrichedEx.get("sets");
            int suggestedSetCount = 0;
            int suggestedReps = 0;
            BigDecimal suggestedKg = BigDecimal.ZERO;
            boolean isBodyweight = Boolean.TRUE.equals(enrichedEx.get("isBodyweight"));

            // Parse plan structure: "sets": 4 (integer count), "reps": 12, "suggestedKg": 60.0
            if (setsRaw instanceof Number setsNum) {
                suggestedSetCount = setsNum.intValue();
                Object repsRaw = enrichedEx.get("reps");
                Object kgRaw = enrichedEx.get("suggestedKg");
                if (repsRaw instanceof Number repsNum) {
                    suggestedReps = repsNum.intValue();
                }
                if (kgRaw instanceof Number kgNum) {
                    suggestedKg = new BigDecimal(kgNum.toString());
                }
            }

            // Build suggested sets from plan count
            Map<Integer, Map<String, Object>> suggestedBySetNum = new LinkedHashMap<>();
            for (int i = 1; i <= suggestedSetCount; i++) {
                Map<String, Object> suggestedSet = new LinkedHashMap<>();
                suggestedSet.put("setNumber", i);
                suggestedSet.put("reps", suggestedReps);
                if (!isBodyweight) {
                    suggestedSet.put("weightKg", suggestedKg);
                }
                suggestedSet.put("done", false);
                suggestedSet.put("isPr", false);
                suggestedBySetNum.put(i, suggestedSet);
            }

            // Merge logged sets on top: replace suggested or append if beyond plan
            for (SetLog log : logsForEx) {
                int setNum = log.getSetNumber();
                Map<String, Object> loggedSet = new LinkedHashMap<>();
                loggedSet.put("setId", log.getId().toString());
                loggedSet.put("setNumber", setNum);
                loggedSet.put("weightKg", log.getWeightKg());
                loggedSet.put("reps", log.getReps());
                loggedSet.put("done", true);

                // Resolve isPr
                boolean isPr = false;
                if (log.getId() != null && isPrOverrides.containsKey(log.getId())) {
                    isPr = Boolean.TRUE.equals(isPrOverrides.get(log.getId()));
                } else if (log.getId() != null && prSetIds.contains(log.getId())) {
                    isPr = true;
                }
                loggedSet.put("isPr", isPr);

                // Replace suggested set if it exists, otherwise we'll append
                suggestedBySetNum.put(setNum, loggedSet);
            }

            // Build final sets array in order
            setsArray.addAll(suggestedBySetNum.values());

            enrichedEx.put("sets", setsArray);
            enrichedEx.put("isBodyweight",
                    bodyweightById.getOrDefault(exId, false));

            boolean anySetIsPr = setsArray.stream()
                    .anyMatch(s -> Boolean.TRUE.equals(s.get("isPr")));
            enrichedEx.put("prAchieved", anySetIsPr);

            result.add(enrichedEx);
        }

        // 2. Append mid-session additions (logged but not in plan) in order of first set_log
        List<String> midSessionExIds = logsByExercise.keySet().stream()
                .filter(exId -> !plannedExIds.contains(exId))
                .sorted((exId1, exId2) -> {
                    Instant t1 = exerciseFirstLogTime.get(exId1);
                    Instant t2 = exerciseFirstLogTime.get(exId2);
                    if (t1 == null || t2 == null) return 0; // Safety: avoid NPE on null times
                    return t1.compareTo(t2);
                })
                .toList();

        for (String exId : midSessionExIds) {
            List<SetLog> logsForEx = logsByExercise.get(exId);
            Map<String, Object> addedEx = new LinkedHashMap<>();

            // Use first log's exercise name
            String exName = logsForEx.get(0).getExerciseName();
            addedEx.put("exerciseId", exId);
            addedEx.put("exerciseName", exName);

            // Build sets from logs only (no suggested sets for mid-session additions)
            List<Map<String, Object>> setsArray = new ArrayList<>();
            for (SetLog log : logsForEx) {
                Map<String, Object> loggedSet = new LinkedHashMap<>();
                loggedSet.put("setId", log.getId().toString());
                loggedSet.put("setNumber", log.getSetNumber());
                loggedSet.put("weightKg", log.getWeightKg());
                loggedSet.put("reps", log.getReps());
                loggedSet.put("done", true);

                boolean isPr = false;
                if (log.getId() != null && isPrOverrides.containsKey(log.getId())) {
                    isPr = Boolean.TRUE.equals(isPrOverrides.get(log.getId()));
                } else if (log.getId() != null && prSetIds.contains(log.getId())) {
                    isPr = true;
                }
                loggedSet.put("isPr", isPr);
                setsArray.add(loggedSet);
            }

            addedEx.put("sets", setsArray);
            addedEx.put("isBodyweight", bodyweightById.getOrDefault(exId, false));
            boolean anySetIsPr = setsArray.stream()
                    .anyMatch(s -> Boolean.TRUE.equals(s.get("isPr")));
            addedEx.put("prAchieved", anySetIsPr);

            result.add(addedEx);
        }

        return result;
    }

    /**
     * Builds a {@link PrDetails} from a {@link PRResult} for the celebration popup.
     * Extracts current/previous values and unit from the result's valuePayload.
     */
    @SuppressWarnings("unchecked")
    private PrDetails buildPrDetails(PRResult result) {
        String type = result.category().toString();
        int coins = result.suggestedCoins();
        Map<String, Object> payload = result.valuePayload();

        double currentValue = 0;
        Double previousValue = null;
        String unit = "kg";

        switch (result.category()) {
            case FIRST_EVER -> {
                Map<String, Object> newBest = (Map<String, Object>) payload.get("new_best");
                if (newBest != null && newBest.get("weight_kg") != null) {
                    currentValue = ((Number) newBest.get("weight_kg")).doubleValue();
                } else if (newBest != null && newBest.get("reps") != null) {
                    currentValue = ((Number) newBest.get("reps")).doubleValue();
                    unit = "reps";
                }
                // previousValue stays null for FIRST_EVER
            }
            case WEIGHT_PR -> {
                Map<String, Object> newBest = (Map<String, Object>) payload.get("new_best");
                Map<String, Object> prevBest = (Map<String, Object>) payload.get("previous_best");
                if (newBest != null && newBest.get("weight_kg") != null) {
                    currentValue = ((Number) newBest.get("weight_kg")).doubleValue();
                }
                if (prevBest != null && prevBest.get("weight_kg") != null) {
                    previousValue = ((Number) prevBest.get("weight_kg")).doubleValue();
                }
            }
            case MAX_ATTEMPT -> {
                if (payload.get("weight_kg") != null) {
                    currentValue = ((Number) payload.get("weight_kg")).doubleValue();
                }
                if (payload.get("previous_best_weight_kg") != null) {
                    previousValue = ((Number) payload.get("previous_best_weight_kg")).doubleValue();
                }
            }
            case REP_PR -> {
                unit = "reps";
                if (payload.get("new_reps") != null) {
                    currentValue = ((Number) payload.get("new_reps")).doubleValue();
                }
                if (payload.get("previous_reps") != null) {
                    previousValue = ((Number) payload.get("previous_reps")).doubleValue();
                }
            }
            case VOLUME_PR -> {
                if (payload.get("new_volume") != null) {
                    currentValue = ((Number) payload.get("new_volume")).doubleValue();
                }
                if (payload.get("previous_best_volume") != null) {
                    previousValue = ((Number) payload.get("previous_best_volume")).doubleValue();
                }
            }
        }

        return new PrDetails(type, currentValue, previousValue, unit, coins);
    }

    private UUID userId(Authentication auth) {
        return (UUID) auth.getPrincipal();
    }

    /**
     * Compute the 1-based week number for a user, counting from their
     * account creation week. Matches the formula previously in
     * {@code WeeklyReportService#weekNumberFor} — uses UTC for both the
     * creation date and the target Monday, consistent with the legacy
     * service and with how {@code finishSession} derives {@code monday}.
     *
     * @param user         the authenticated user (must have {@code createdAt})
     * @param targetMonday the Monday of the week to number (UTC)
     * @return 1-based week number (week of account creation = week 1)
     */
    private static int weekNumberFor(User user, LocalDate targetMonday) {
        LocalDate createdMonday = user.getCreatedAt()
                .atZone(ZoneOffset.UTC).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return (int) ChronoUnit.WEEKS.between(createdMonday, targetMonday) + 1;
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
     * Requires the session to be owned by the user AND within the edit window.
     * Edit window = session is COMPLETED and finished_at is before 6:00 AM IST
     * the day after the session was finished.
     */
    private WorkoutSession requireOwnedAndEditable(UUID sessionId, UUID userId) {
        WorkoutSession session = requireOwned(sessionId, userId);
        if ("IN_PROGRESS".equals(session.getStatus())) {
            return session; // mid-workout edits are always allowed
        }
        if (!"COMPLETED".equals(session.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_IN_PROGRESS",
                    "This session is already " + session.getStatus() + ".");
        }
        if (session.getFinishedAt() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_NOT_EDITABLE",
                    "Session has no finish timestamp.");
        }
        // Edit window closes at 6:00 AM IST the day after the session was finished.
        // e.g., finish at 8pm Tue → closes 6am Wed. Finish at 2am Wed → closes 6am Thu.
        java.time.ZoneId ist = java.time.ZoneId.of("Asia/Kolkata");
        java.time.ZonedDateTime finishedZoned = session.getFinishedAt().atZone(ist);
        java.time.LocalDate finishDate = finishedZoned.toLocalDate();
        java.time.ZonedDateTime cutoff = finishDate.plusDays(1)
                .atTime(6, 0)
                .atZone(ist);
        if (java.time.ZonedDateTime.now(ist).isAfter(cutoff)) {
            throw new ApiException(HttpStatus.CONFLICT, "EDIT_WINDOW_CLOSED",
                    "Edit window has closed for this session.");
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
