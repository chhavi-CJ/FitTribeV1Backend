package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.request.FinishSessionRequest;
import com.fittribe.api.dto.request.LogSetRequest;
import com.fittribe.api.dto.request.StartSessionRequest;
import com.fittribe.api.dto.response.FinishSessionResponse;
import com.fittribe.api.dto.response.LogSetResponse;
import com.fittribe.api.dto.response.SessionHistoryItem;
import com.fittribe.api.dto.response.StartSessionResponse;
import com.fittribe.api.entity.CoinTransaction;
import com.fittribe.api.entity.SetLog;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.CoinTransactionRepository;
import com.fittribe.api.repository.SetLogRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.service.AiService;
import com.fittribe.api.service.WeeklyReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private static final int COOLDOWN_HOURS    = 8;
    private static final int COINS_PER_SESSION = 10;

    private final WorkoutSessionRepository sessionRepo;
    private final SetLogRepository         setLogRepo;
    private final UserRepository           userRepo;
    private final CoinTransactionRepository coinRepo;
    private final AiService                aiService;
    private final WeeklyReportService      weeklyReportService;

    public SessionController(WorkoutSessionRepository sessionRepo,
                             SetLogRepository setLogRepo,
                             UserRepository userRepo,
                             CoinTransactionRepository coinRepo,
                             AiService aiService,
                             WeeklyReportService weeklyReportService) {
        this.sessionRepo         = sessionRepo;
        this.setLogRepo          = setLogRepo;
        this.userRepo            = userRepo;
        this.coinRepo            = coinRepo;
        this.aiService           = aiService;
        this.weeklyReportService = weeklyReportService;
    }

    // ── POST /sessions/start ──────────────────────────────────────────
    @PostMapping("/start")
    @Transactional
    public ResponseEntity<ApiResponse<?>> startSession(
            @RequestBody StartSessionRequest request,
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

        WorkoutSession session = requireOwnedInProgress(id, userId(auth));

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
            // Bodyweight set — only a PR if no prior set exists for this exercise
            isPr = setLogRepo
                    .findTopByUserIdAndExerciseIdOrderByWeightKgDesc(userId(auth), request.exerciseId())
                    .isEmpty();
        } else {
            isPr = setLogRepo
                    .findTopByUserIdAndExerciseIdOrderByWeightKgDesc(userId(auth), request.exerciseId())
                    .map(best -> best.getWeightKg() == null
                            || request.weightKg().compareTo(best.getWeightKg()) > 0)
                    .orElse(true); // first-ever set for this exercise is always a PR
        }

        SetLog log = new SetLog();
        log.setSessionId(session.getId());
        log.setExerciseId(request.exerciseId());
        log.setExerciseName(request.exerciseName());
        log.setSetNumber(request.setNumber());
        log.setWeightKg(request.weightKg());
        log.setReps(request.reps());
        log.setIsPr(isPr);
        SetLog saved = setLogRepo.save(log);

        return ResponseEntity.ok(ApiResponse.success(
                new LogSetResponse(saved.getId(), saved.getIsPr())));
    }

    // ── POST /sessions/{id}/finish ────────────────────────────────────
    @PostMapping("/{id}/finish")
    @Transactional
    public ResponseEntity<ApiResponse<FinishSessionResponse>> finishSession(
            @PathVariable UUID id,
            @RequestBody FinishSessionRequest request,
            Authentication auth) {

        UUID userId = userId(auth);
        WorkoutSession session = requireOwnedInProgress(id, userId);

        List<SetLog> logs = setLogRepo.findBySessionId(session.getId());

        BigDecimal totalVolume = logs.stream()
                .map(sl -> sl.getWeightKg() != null && sl.getReps() != null
                        ? sl.getWeightKg().multiply(BigDecimal.valueOf(sl.getReps()))
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Instant finishedAt = Instant.now();

        // Lock the user row before reading coins — prevents concurrent requests from
        // both passing a future balance check and both deducting.
        User user = userRepo.findByIdForUpdate(userId).orElseThrow(() -> ApiException.notFound("User"));
        LocalDate monday = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
        int weekNumber = weeklyReportService.weekNumberFor(user, monday);
        int weeklyGoal = user.getWeeklyGoal() != null ? user.getWeeklyGoal() : 4;

        Instant weekFrom = monday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant weekTo   = monday.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Save session as COMPLETED first so the DB row exists before we count
        session.setStatus("COMPLETED");
        session.setFinishedAt(finishedAt);
        session.setTotalSets(logs.size());
        session.setTotalVolumeKg(totalVolume);
        session.setDurationMins(request.durationMins());
        session.setWeekNumber(weekNumber);
        sessionRepo.save(session);

        // Count COMPLETED sessions this week AFTER save — no +1 needed
        // Session 1 finish → count=1, 1>=2=false
        // Session 2 finish → count=2, 2>=2=true
        int count = sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                userId, "COMPLETED", weekFrom, weekTo);
        boolean weeklyGoalHit = count >= weeklyGoal;
        session.setWeeklyGoalHit(weeklyGoalHit);
        sessionRepo.save(session);

        // Update user streak + coins
        user.setStreak(user.getStreak() + 1);
        user.setCoins(user.getCoins() + COINS_PER_SESSION);
        userRepo.save(user);

        // Record coin transaction
        CoinTransaction tx = new CoinTransaction();
        tx.setUserId(userId);
        tx.setAmount(COINS_PER_SESSION);
        tx.setDirection("CREDIT");
        tx.setLabel("Workout completed");
        coinRepo.save(tx);

        // Trigger async AI insight (non-blocking)
        aiService.generateAndSaveInsight(userId, session.getId());

        // Trigger async weekly report when goal is hit
        if (weeklyGoalHit) {
            weeklyReportService.generateWeeklyReport(userId, weekNumber);
        }

        return ResponseEntity.ok(ApiResponse.success(new FinishSessionResponse(
                session.getId(),
                session.getName(),
                totalVolume,
                logs.size(),
                request.durationMins(),
                session.getFinishedAt(),
                user.getStreak(),
                COINS_PER_SESSION,
                weeklyGoalHit,
                weekNumber)));
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
}
