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
import com.fittribe.api.entity.PrEvent;
import com.fittribe.api.entity.SavedRoutine;
import com.fittribe.api.entity.SessionFeedback;
import com.fittribe.api.entity.SetLog;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.prv2.detector.ExerciseType;
import com.fittribe.api.prv2.detector.LoggedSet;
import com.fittribe.api.prv2.detector.PrCategory;
import com.fittribe.api.prv2.detector.PRDetector;
import com.fittribe.api.prv2.detector.PRResult;
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
import com.fittribe.api.repository.UserExerciseBestsRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.jobs.JobEnqueuer;
import com.fittribe.api.jobs.JobType;
import com.fittribe.api.jobs.JobWorker;
import com.fittribe.api.service.AiService;
import com.fittribe.api.service.CoinService;
import com.fittribe.api.service.PlanService;
import com.fittribe.api.service.RankService;
import com.fittribe.api.strengthscore.ProgressSnapshotService;
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
    private final PrEventRepository         prEventRepo;
    private final UserExerciseBestsRepository userExerciseBestsRepo;
    private final PRDetector                 prDetector;
    private final ExerciseRepository         exerciseRepo;

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
                             PrEventRepository prEventRepo,
                             UserExerciseBestsRepository userExerciseBestsRepo,
                             PRDetector prDetector,
                             ExerciseRepository exerciseRepo) {
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
        this.prEventRepo = prEventRepo;
        this.userExerciseBestsRepo = userExerciseBestsRepo;
        this.prDetector = prDetector;
        this.exerciseRepo = exerciseRepo;
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
    @PatchMapping("/{id}/log-set/{exerciseId}/{setNumber}")
    @Transactional
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

        // Parse setId from JSONB (canonical source after finish)
        UUID setId = targetSet.get("setId") != null
                ? UUID.fromString(targetSet.get("setId").toString())
                : null;

        // Capture old values for cascade (before JSONB update)
        BigDecimal oldWeightKg = targetSet.get("weightKg") != null
                ? new BigDecimal(targetSet.get("weightKg").toString())
                : null;
        int oldReps = ((Number) targetSet.get("reps")).intValue();

        // Update JSONB
        targetSet.put("weightKg", request.weightKg());
        targetSet.put("reps", request.reps());
        if (request.holdSeconds() != null) {
            targetSet.put("holdSeconds", request.holdSeconds());
        }

        // Re-serialize exercises JSONB
        try {
            session.setExercises(objectMapper.writeValueAsString(exercises));
        } catch (Exception e) {
            log.error("Failed to serialize exercises JSONB for session={}", id, e);
            throw new RuntimeException("Could not update exercises", e);
        }

        sessionRepo.save(session);

        // Build cascade values using setId from JSONB
        LoggedSet oldValue = new LoggedSet(setId, exerciseId, oldWeightKg, oldReps, null);
        LoggedSet newValue = new LoggedSet(setId, exerciseId, request.weightKg(), request.reps(), request.holdSeconds());

        // Call cascade (if PR system enabled, this will handle supersession and re-detection)
        try {
            prEditCascadeService.processSetEdit(userId, session.getId(), setId, oldValue, newValue);
        } catch (Exception e) {
            log.warn("Failed to process PR cascade for session={} exercise={} set={}",
                    session.getId(), exerciseId, setNumber, e);
            // Cascade failures are non-fatal — the edit itself succeeded
        }

        // Query pr_events to determine isPr after cascade
        boolean isPr = false;
        if (setId != null) {
            List<com.fittribe.api.entity.PrEvent> events = prEventRepo
                    .findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, session.getId(), setId);
            isPr = !events.isEmpty();
        }

        // Re-read session from DB (cascade may have mutated derived fields)
        // and build the full today DTO so frontend can replace state atomically.
        WorkoutSession updated = sessionRepo.findById(session.getId()).orElse(session);
        TodaySessionResponse todayDto = buildTodayResponse(updated, userId);

        return ResponseEntity.ok(ApiResponse.success(
                new EditSetResponse(setId, isPr, todayDto)));
    }

    // ── DELETE /sessions/{id}/log-set/{exerciseId}/{setNumber} ───────
    @DeleteMapping("/{id}/log-set/{exerciseId}/{setNumber}")
    @Transactional
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

        sessionRepo.save(session);

        // Call cascade
        try {
            if (deletedSetId != null) {
                prEditCascadeService.processSetDelete(userId, session.getId(), deletedSetId, oldValue);
            }
        } catch (Exception e) {
            log.warn("Failed to process PR cascade for set delete: session={} exercise={} set={}",
                    session.getId(), exerciseId, setNumber, e);
        }

        // Re-read session and build the full today DTO
        WorkoutSession updated = sessionRepo.findById(session.getId()).orElse(session);
        TodaySessionResponse todayDto = buildTodayResponse(updated, userId);

        return ResponseEntity.ok(ApiResponse.success(
                new DeleteSetResponse(true, todayDto)));
    }

    // ── DELETE /sessions/{id}/log-set/exercise/{exerciseId} ──────────
    @DeleteMapping("/{id}/log-set/exercise/{exerciseId}")
    @Transactional
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

        sessionRepo.save(session);

        // Call cascade
        try {
            prEditCascadeService.processExerciseDelete(userId, session.getId(), exerciseId, oldValues);
        } catch (Exception e) {
            log.warn("Failed to process PR cascade for exercise delete: session={} exercise={}",
                    session.getId(), exerciseId, e);
        }

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

        // Streak update — atomic SQL to avoid detached-entity merge races
        // with RankService.checkAndPromote (which also writes to users).
        // Coin balance is managed entirely by CoinService via atomic SQL updates.
        int newStreak = 0;
        try {
            newStreak = Math.max(0, user.getStreak() + 1);
            userRepo.updateStreak(userId, newStreak);
            userRepo.updateMaxStreakIfHigher(userId, newStreak);
            user.setStreak(newStreak); // keep in-memory value in sync for the response
        } catch (Exception e) {
            log.error("Failed to update streak for user={}", userId, e);
        }

        // Snapshot streak to session for history view ("what was the streak when you finished")
        // Separate try/catch — failure here doesn't affect /finish 200 response or other blocks
        try {
            sessionRepo.updateStreak(session.getId(), newStreak);
        } catch (Exception e) {
            log.error("Failed to write streak snapshot to session {}", session.getId(), e);
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

        // Generate next-week AI plan when weekly goal is hit so user has it ready on Monday.
        // This is independent of weekly report generation, which is deferred to the Sunday cron
        // to ensure it captures the complete Mon–Sun week.
        if (weeklyGoalHit) {
            try {
                planService.generatePlan(userId);
            } catch (Exception e) {
                log.error("Failed to trigger plan generation for user={}", userId, e);
            }
        }

        // Strength score snapshot — fire on every session finish, not just weekly goal hit,
        // so the Trends tab can show mid-week progression. Isolated try/catch — failure
        // here must not affect /finish 200 or any sibling derived-data blocks.
        try {
            LocalDate snapshotWeekStart = LocalDate.ofInstant(session.getFinishedAt(), Zones.APP_ZONE)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            progressSnapshotService.computeForUserWeek(userId, snapshotWeekStart);
        } catch (Exception e) {
            log.error("Failed to compute strength snapshot for user {} session {}",
                    userId, session.getId(), e);
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

            // 2. Weekly goal +50 — only on the exact session that hits the goal
            if (count == weeklyGoal) {
                coinService.awardCoins(userId, 50, "WEEKLY_GOAL",
                        "Weekly goal hit", weekEpoch);
            }

            // 3. Volume improvement vs last week +30
            Instant lastWeekFrom = weekFrom.minus(7, ChronoUnit.DAYS);
            BigDecimal thisWeekVol = sessionRepo.sumVolumeByUserIdAndFinishedAtBetween(userId, weekFrom, weekTo);
            BigDecimal lastWeekVol = sessionRepo.sumVolumeByUserIdAndFinishedAtBetween(userId, lastWeekFrom, weekFrom);
            if (lastWeekVol != null && lastWeekVol.compareTo(BigDecimal.ZERO) > 0
                    && thisWeekVol != null && thisWeekVol.compareTo(lastWeekVol) > 0) {
                coinService.awardCoins(userId, 30, "IMPROVE_VOLUME",
                        "Improved vs last week", weekEpoch);
            }

            // 4. Streak milestones
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

        // ── PR System V2 write path ──────────────────────────────────────
        // Build LoggedSets from request payload. setIds come from setIdByExerciseAndNumber
        // (pre-fetched before CORE SAVE while set_logs still exist). set_logs is
        // deleted as the final derived block below (Option Y — JSONB is source of truth).
        try {
            List<LoggedSet> loggedSets = new ArrayList<>();
            if (exercises != null) {
                for (ExerciseLogRequest ex : exercises) {
                    if (ex.sets() == null) continue;
                    for (SetLogRequest setReq : ex.sets()) {
                        UUID setId = setIdByExerciseAndNumber.get(
                                ex.exerciseId() + ":" + setReq.setNumber());
                        loggedSets.add(new LoggedSet(
                                setId,
                                ex.exerciseId(),
                                setReq.weightKg(),
                                setReq.reps(),
                                null));  // holdSeconds not in SetLogRequest; TIMED support is future
                    }
                }
            }
            prWritePathService.processSessionFinish(userId, id, loggedSets);
        } catch (Exception e) {
            log.error("Failed to process PR detection for session={}", id, e);
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

            }
        } catch (Exception e) {
            log.error("Failed to post feed items for session={}", id, e);
        }

        // ── Delete set_logs (Option Y cleanup) ──────────────────────────
        // set_logs was a mid-workout crash-recovery buffer. Under Option Y the
        // exercises JSONB (with embedded setIds) written at finish is the single
        // source of truth. set_logs rows are no longer needed; PATCH/DELETE during
        // the edit window read setId from JSONB, not set_logs.
        try {
            setLogRepo.deleteBySessionId(id);
        } catch (Exception e) {
            log.error("Failed to delete set_logs for session={} — rows will persist but are not load-bearing", id, e);
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
        if (request.finishedAt().isAfter(Instant.now().plusSeconds(5))) {
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
        Map<String, String> muscleGroupById = exerciseRepo.findAll()
                .stream()
                .collect(Collectors.toMap(Exercise::getId,
                        e -> e.getMuscleGroup() != null ? e.getMuscleGroup() : ""));

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
                            session.getExercises(), prSetIds, firstEverExerciseIds, muscleGroupById);

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
            Map<String, String> muscleGroupById) {

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
                        muscleGroup, firstEver, sets));
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
        List<SetLog> logs = setLogRepo.findBySessionId(session.getId());

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
            plannedExercises = (raw != null && !raw.isBlank())
                    ? objectMapper.readValue(raw, new TypeReference<>() {})
                    : null;
        } catch (Exception e) {
            plannedExercises = null;
        }

        FeedbackInfo feedback = feedbackRepo.findBySessionId(session.getId())
                .map(fb -> new FeedbackInfo(fb.getRating(), fb.getNotes(), fb.getCreatedAt()))
                .orElse(null);

        // Parse exercises JSONB and enrich with set-level PR flags from pr_events.
        List<Map<String, Object>> exercises;
        try {
            String rawEx = session.getExercises();
            if (rawEx != null && !rawEx.isBlank()) {
                List<Map<String, Object>> parsed = objectMapper.readValue(
                        rawEx, new TypeReference<List<Map<String, Object>>>() {});

                // Exclude FIRST_EVER events — those mark the first time a
                // user logs an exercise (useful for analytics / future
                // achievements) but should not surface as trophies on the
                // Summary screen. Within-session PRs (REP_PR, WEIGHT_PR, etc.)
                // continue to render normally.
                java.util.Set<UUID> prSetIds = prEventRepo
                        .findBySessionIdAndSupersededAtIsNull(session.getId())
                        .stream()
                        .filter(pe -> !"FIRST_EVER".equals(pe.getPrCategory()))
                        .map(pe -> pe.getSetId())
                        .collect(java.util.stream.Collectors.toSet());

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
                            boolean isPr = setId != null && prSetIds.contains(setId);
                            enrichedSet.put("isPr", isPr);
                            if (isPr) anySetIsPr = true;
                            enrichedSets.add(enrichedSet);
                        }
                        enrichedEx.put("sets", enrichedSets);
                    }

                    enrichedEx.put("prAchieved", anySetIsPr);
                    exercises.add(enrichedEx);
                }
            } else {
                exercises = List.of();
            }
        } catch (Exception e) {
            log.warn("Failed to parse exercises JSONB for session={}", session.getId(), e);
            exercises = List.of();
        }

        return new TodaySessionResponse(
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
                exercises,
                feedback);
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
