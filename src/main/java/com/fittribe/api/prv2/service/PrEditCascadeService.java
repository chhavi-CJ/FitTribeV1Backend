package com.fittribe.api.prv2.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.PrEvent;
import com.fittribe.api.entity.UserExerciseBests;
import com.fittribe.api.entity.WeeklyPrCount;
import com.fittribe.api.prv2.detector.ExerciseType;
import com.fittribe.api.prv2.detector.LoggedSet;
import com.fittribe.api.prv2.detector.PRDetector;
import com.fittribe.api.prv2.detector.PRResult;
import com.fittribe.api.prv2.detector.PrCategory;
import com.fittribe.api.repository.PrEventRepository;
import com.fittribe.api.repository.UserExerciseBestsRepository;
import com.fittribe.api.repository.WeeklyPrCountRepository;
import com.fittribe.api.service.CoinService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles PR supersession, un-supersession, and coin reconciliation when a user
 * edits or deletes a set during the edit window. Operates on pr_events and
 * user_exercise_bests only — JSONB mutation is handled by the controller.
 *
 * <h3>Cascade steps (edit path)</h3>
 * <ol>
 *   <li>Query active pr_events for setId</li>
 *   <li>Filter out FIRST_EVER from supersedable list (permanent once earned)</li>
 *   <li>Supersede each non-FIRST_EVER event (superseded_by left null, backfilled in Step 6)</li>
 *   <li>Un-supersede prior PRs (category-aware, filtered by superseded_by + pr_category)</li>
 *   <li>If DELETE, short-circuit to Step 7</li>
 *   <li value="5">5.5. Rebuild user_exercise_bests from current non-superseded pr_events
 *       (so Step 6 sees accurate baseline)</li>
 *   <li value="6">Re-read UserExerciseBests freshly, run PRDetector on new value; if PR fires,
 *       write new event and backfill superseded_by on matching-category superseded events</li>
 *   <li>Final rebuild of user_exercise_bests (incorporates any new event written in Step 6)</li>
 * </ol>
 *
 * <p>Per-set processing runs in its own TransactionTemplate for error isolation
 * (P4 pattern per HLD §7.3). Failures on one set do not prevent others from processing.
 */
@Service
public class PrEditCascadeService {

    private static final Logger log = LoggerFactory.getLogger(PrEditCascadeService.class);

    private final PRDetector prDetector;
    private final UserExerciseBestsRepository userExerciseBestsRepo;
    private final PrEventRepository prEventRepo;
    private final WeeklyPrCountRepository weeklyPrCountRepo;
    private final CoinService coinService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public PrEditCascadeService(
            PRDetector prDetector,
            UserExerciseBestsRepository userExerciseBestsRepo,
            PrEventRepository prEventRepo,
            WeeklyPrCountRepository weeklyPrCountRepo,
            CoinService coinService,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper) {
        this.prDetector = prDetector;
        this.userExerciseBestsRepo = userExerciseBestsRepo;
        this.prEventRepo = prEventRepo;
        this.weeklyPrCountRepo = weeklyPrCountRepo;
        this.coinService = coinService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    /**
     * Process a set edit: supersede old PR events (except FIRST_EVER), un-supersede
     * prior PRs restored by the revert, re-detect, rebuild bests.
     *
     * <p>Called AFTER workout_sessions.exercises JSONB has been updated with new values.
     */
    public void processSetEdit(UUID userId, UUID sessionId, UUID setId,
                               LoggedSet oldValue, LoggedSet newValue) {
        log.debug("Starting edit cascade for user={} session={} exercise={} setId={}",
                userId, sessionId, oldValue.exerciseId(), setId);

        processEditOrDelete(userId, sessionId, setId, oldValue, newValue, false);
    }

    /**
     * Process a set deletion: supersede old PR events (except FIRST_EVER),
     * un-supersede prior PRs, rebuild bests. No re-detection.
     *
     * <p>Called AFTER the set has been removed from workout_sessions.exercises JSONB.
     */
    public void processSetDelete(UUID userId, UUID sessionId, UUID setId, LoggedSet oldValue) {
        log.debug("Starting delete cascade for user={} session={} exercise={} setId={}",
                userId, sessionId, oldValue.exerciseId(), setId);

        processEditOrDelete(userId, sessionId, setId, oldValue, null, true);
    }

    /**
     * UNREACHABLE VIA CURRENT UI — the frontend enforces ≥1 set per exercise,
     * so exercise-level deletion is not offered to users. Kept for completeness
     * and possible future admin paths. If you're here because this ran, check
     * the frontend guard before assuming a bug in this method.
     *
     * <p>Supersedes ALL active pr_events for (session, exercise) — including
     * FIRST_EVER, because removing the exercise removes the history.
     */
    public void processExerciseDelete(UUID userId, UUID sessionId, String exerciseId,
                                      List<LoggedSet> oldValues) {
        log.debug("Starting exercise delete cascade for user={} session={} exercise={}",
                userId, sessionId, exerciseId);

        try {
            transactionTemplate.executeWithoutResult(txStatus -> {
                List<PrEvent> activeEvents = prEventRepo
                        .findByUserIdAndSessionIdAndExerciseIdAndSupersededAtNull(userId, sessionId, exerciseId);

                Instant supersededAt = Instant.now();

                for (PrEvent event : activeEvents) {
                    event.setSupersededAt(supersededAt);
                    prEventRepo.save(event);
                    log.debug("Superseded PR event: id={} category={}", event.getId(), event.getPrCategory());

                    if (event.getCoinsAwarded() > 0) {
                        coinService.awardCoins(userId, -event.getCoinsAwarded(), "PR_REVOKED",
                                buildRevocationLabel(exerciseId, event.getPrCategory()),
                                event.getId() + ":revoke:" + supersededAt.toEpochMilli());
                    }

                    decrementWeeklyPrCounts(userId, event.getWeekStart(), event.getPrCategory(), event.getCoinsAwarded());
                }

                rebuildExerciseBests(userId, exerciseId);
            });
        } catch (Exception e) {
            log.warn("Failed to process exercise delete cascade for user={} session={} exercise={}",
                    userId, sessionId, exerciseId, e);
        }
    }

    // ── Core cascade ──────────────────────────────────────────────────────

    /**
     * Internal: handle both edit and delete cascades with the full 7-step
     * pipeline from the HLD.
     */
    private void processEditOrDelete(UUID userId, UUID sessionId, UUID setId,
                                      LoggedSet oldValue, LoggedSet newValue, boolean isDelete) {
        try {
            transactionTemplate.executeWithoutResult(txStatus -> {
                String exerciseId = oldValue.exerciseId();

                // Step 1: Find non-superseded PR events for this session+set
                List<PrEvent> activeEvents = prEventRepo
                        .findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId);

                log.debug("Found {} active PR events for session={} set={}", activeEvents.size(), sessionId, setId);

                // Step 2: Filter out FIRST_EVER — permanent once earned
                List<PrEvent> supersedable = activeEvents.stream()
                        .filter(e -> !"FIRST_EVER".equals(e.getPrCategory()))
                        .toList();

                log.debug("{} events supersedable (excluded {} FIRST_EVER)",
                        supersedable.size(), activeEvents.size() - supersedable.size());

                // Step 3: Supersede each non-FIRST_EVER event
                Instant supersededAt = Instant.now();
                for (PrEvent event : supersedable) {
                    event.setSupersededAt(supersededAt);
                    // superseded_by left null — backfilled in Step 6 if a new event is created
                    prEventRepo.save(event);
                    log.debug("Superseded PR event: id={} category={} coins={}",
                            event.getId(), event.getPrCategory(), event.getCoinsAwarded());

                    if (event.getCoinsAwarded() > 0) {
                        coinService.awardCoins(userId, -event.getCoinsAwarded(), "PR_REVOKED",
                                buildRevocationLabel(exerciseId, event.getPrCategory()),
                                event.getId() + ":revoke:" + supersededAt.toEpochMilli());
                    }

                    decrementWeeklyPrCounts(userId, event.getWeekStart(), event.getPrCategory(), event.getCoinsAwarded());
                }

                // Step 4: Un-supersede prior PRs (category-aware)
                Instant restoredAt = Instant.now();
                for (PrEvent superseded : supersedable) {
                    String category = superseded.getPrCategory();
                    List<PrEvent> toRestore = prEventRepo
                            .findBySupersededByAndPrCategory(superseded.getId(), category);

                    for (PrEvent old : toRestore) {
                        old.setSupersededAt(null);
                        old.setSupersededBy(null);
                        prEventRepo.save(old);
                        log.debug("Un-superseded PR event: id={} category={}", old.getId(), old.getPrCategory());

                        if (old.getCoinsAwarded() > 0) {
                            coinService.awardCoins(userId, old.getCoinsAwarded(), "PR_RESTORED",
                                    buildRestorationLabel(exerciseId, old.getPrCategory()),
                                    old.getId() + ":restore:" + restoredAt.toEpochMilli());
                        }

                        incrementWeeklyPrCountsForEdit(userId, old.getWeekStart(),
                                PrCategory.valueOf(old.getPrCategory()), old.getCoinsAwarded());
                    }
                }

                // Step 5: If delete, skip re-detection — rebuild and return
                if (isDelete) {
                    rebuildExerciseBests(userId, exerciseId);
                    return;
                }

                // Step 5.5: Rebuild bests BEFORE re-detection so PRDetector sees the
                // current non-superseded state (reflects Steps 3-4: supersedes + un-supersedes).
                rebuildExerciseBests(userId, exerciseId);

                // Step 6: Re-detect on the new value (edit path only)
                // Re-read bests freshly — rebuild just reconciled them.
                UserExerciseBests currentBests = userExerciseBestsRepo
                        .findByUserIdAndExerciseId(userId, exerciseId)
                        .orElse(null);

                ExerciseType exerciseType = determineExerciseType(currentBests);
                var prResult = prDetector.detect(newValue, currentBests, exerciseType);

                log.debug("Edit cascade PR detection: isPR={} category={} for exercise={}",
                        prResult.isPR(), prResult.category(), exerciseId);

                if (prResult.isPR()) {
                    LocalDate weekStart = weekStartFor(Instant.now());
                    PrEvent newEvent = new PrEvent();
                    newEvent.setUserId(userId);
                    newEvent.setExerciseId(exerciseId);
                    newEvent.setSessionId(sessionId);
                    newEvent.setSetId(setId);
                    newEvent.setPrCategory(prResult.category().toString());
                    newEvent.setWeekStart(weekStart);
                    try {
                        newEvent.setSignalsMet(objectMapper.writeValueAsString(prResult.signalsMet()));
                        newEvent.setValuePayload(objectMapper.writeValueAsString(prResult.valuePayload()));
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(
                                "Failed to serialize pr_event payload for user=" + userId + " set=" + setId, e);
                    }
                    newEvent.setCoinsAwarded(prResult.suggestedCoins());
                    newEvent.setDetectorVersion(prResult.detectorVersion());

                    PrEvent saved = prEventRepo.save(newEvent);
                    log.debug("Created new PR event after edit: id={} category={} coins={}",
                            saved.getId(), saved.getPrCategory(), saved.getCoinsAwarded());

                    // Backfill superseded_by on matching-category events from Step 3
                    String newCategory = prResult.category().toString();
                    for (PrEvent event : supersedable) {
                        if (event.getPrCategory().equals(newCategory)) {
                            event.setSupersededBy(saved.getId());
                            prEventRepo.save(event);
                        }
                    }

                    incrementWeeklyPrCountsForEdit(userId, weekStart, prResult.category(), prResult.suggestedCoins());

                    if (prResult.suggestedCoins() > 0) {
                        String coinType = coinTypeFor(prResult.category());
                        String coinLabel = buildNewPrLabel(exerciseId, prResult.category());
                        coinService.awardCoins(userId, prResult.suggestedCoins(), coinType, coinLabel,
                                saved.getId().toString());
                    }
                }

                // Step 7: Final rebuild — incorporates the newly-written event if one was created
                rebuildExerciseBests(userId, exerciseId);
            });
        } catch (Exception e) {
            log.warn("Failed to process edit/delete cascade for user={} session={} setId={}",
                    userId, sessionId, setId, e);
        }
    }

    // ── Bests rebuild ─────────────────────────────────────────────────────

    /**
     * Rebuild user_exercise_bests from non-superseded pr_events for (user, exercise).
     * This is the authoritative reconstruction — pr_events is the audit log,
     * user_exercise_bests is a cache derived from it.
     *
     * <p>Reads value_payload JSONB from each active event to extract weight/reps/volume/hold
     * values, then computes the max across all active events per field.
     */
    private void rebuildExerciseBests(UUID userId, String exerciseId) {
        List<PrEvent> activeEvents = prEventRepo
                .findByUserIdAndExerciseIdAndSupersededAtIsNull(userId, exerciseId);

        if (activeEvents.isEmpty()) {
            // No active PRs remain — delete the bests row
            userExerciseBestsRepo.findByUserIdAndExerciseId(userId, exerciseId)
                    .ifPresent(bests -> {
                        userExerciseBestsRepo.delete(bests);
                        log.debug("Deleted user_exercise_bests (no active pr_events): user={} exercise={}",
                                userId, exerciseId);
                    });
            return;
        }

        UserExerciseBests bests = userExerciseBestsRepo
                .findByUserIdAndExerciseId(userId, exerciseId)
                .orElseGet(() -> {
                    UserExerciseBests b = new UserExerciseBests();
                    b.setUserId(userId);
                    b.setExerciseId(exerciseId);
                    b.setExerciseType(ExerciseType.WEIGHTED.toString());
                    return b;
                });

        // Reset fields we'll recompute — leave totalSessionsWithExercise and bestSessionVolumeKg untouched
        BigDecimal bestWtKg = null;
        Integer repsAtBestWt = null;
        Instant bestWtCreatedAt = Instant.MIN;
        Integer bestReps = null;
        BigDecimal wtAtBestReps = null;
        BigDecimal best1rm = null;
        BigDecimal bestSetVolume = null;
        Integer bestHoldSeconds = null;

        for (PrEvent event : activeEvents) {
            PayloadValues pv = extractPayload(event);

            // bestWtKg: max weight, tie-break by most recent created_at
            if (pv.weightKg != null) {
                if (bestWtKg == null || pv.weightKg.compareTo(bestWtKg) > 0
                        || (pv.weightKg.compareTo(bestWtKg) == 0
                            && event.getCreatedAt() != null
                            && event.getCreatedAt().isAfter(bestWtCreatedAt))) {
                    bestWtKg = pv.weightKg;
                    repsAtBestWt = pv.reps;
                    bestWtCreatedAt = event.getCreatedAt() != null ? event.getCreatedAt() : Instant.MIN;
                }
            }

            // bestReps
            if (pv.reps != null && (bestReps == null || pv.reps > bestReps)) {
                bestReps = pv.reps;
                wtAtBestReps = pv.weightKg;
            }

            // best1rm (Epley: weight * (1 + reps/30))
            if (pv.weightKg != null && pv.reps != null && pv.reps > 0) {
                BigDecimal epley = pv.weightKg.multiply(
                        BigDecimal.ONE.add(BigDecimal.valueOf(pv.reps).divide(BigDecimal.valueOf(30), 2, java.math.RoundingMode.HALF_UP)));
                if (best1rm == null || epley.compareTo(best1rm) > 0) {
                    best1rm = epley;
                }
            }

            // bestSetVolume
            if (pv.weightKg != null && pv.reps != null) {
                BigDecimal volume = pv.weightKg.multiply(BigDecimal.valueOf(pv.reps));
                if (bestSetVolume == null || volume.compareTo(bestSetVolume) > 0) {
                    bestSetVolume = volume;
                }
            }

            // bestHoldSeconds
            if (pv.holdSeconds != null && (bestHoldSeconds == null || pv.holdSeconds > bestHoldSeconds)) {
                bestHoldSeconds = pv.holdSeconds;
            }
        }

        bests.setBestWtKg(bestWtKg);
        bests.setRepsAtBestWt(repsAtBestWt);
        bests.setBestReps(bestReps);
        bests.setWtAtBestRepsKg(wtAtBestReps);
        bests.setBest1rmEpleyKg(best1rm);
        bests.setBestSetVolumeKg(bestSetVolume);
        bests.setBestHoldSeconds(bestHoldSeconds);
        // bestSessionVolumeKg: session-level, not reconstructable from per-set pr_events — leave unchanged
        // totalSessionsWithExercise: counted at finish, not PR-event-derived — leave unchanged
        bests.setLastLoggedAt(Instant.now());

        userExerciseBestsRepo.save(bests);
        log.debug("Rebuilt user_exercise_bests from {} active pr_events: user={} exercise={} bestWtKg={}",
                activeEvents.size(), userId, exerciseId, bestWtKg);
    }

    /**
     * Extract weight/reps/hold values from a pr_event's value_payload JSONB.
     * Handles both "new_best" nested format (from FIRST_EVER/WEIGHT_PR) and
     * flat format (from MAX_ATTEMPT/REP_PR/VOLUME_PR).
     */
    private PayloadValues extractPayload(PrEvent event) {
        BigDecimal weightKg = null;
        Integer reps = null;
        Integer holdSeconds = null;

        try {
            Map<String, Object> payload = objectMapper.readValue(
                    event.getValuePayload(), new TypeReference<Map<String, Object>>() {});

            // Try nested "new_best" first (FIRST_EVER, WEIGHT_PR payloads)
            @SuppressWarnings("unchecked")
            Map<String, Object> newBest = payload.containsKey("new_best")
                    ? (Map<String, Object>) payload.get("new_best")
                    : null;

            Map<String, Object> source = newBest != null ? newBest : payload;

            if (source.containsKey("weight_kg")) {
                weightKg = new BigDecimal(source.get("weight_kg").toString());
            }
            if (source.containsKey("reps")) {
                reps = ((Number) source.get("reps")).intValue();
            }
            if (source.containsKey("new_reps")) {
                reps = ((Number) source.get("new_reps")).intValue();
            }
            if (source.containsKey("hold_seconds")) {
                holdSeconds = ((Number) source.get("hold_seconds")).intValue();
            }
            if (source.containsKey("new_seconds")) {
                holdSeconds = ((Number) source.get("new_seconds")).intValue();
            }
        } catch (Exception e) {
            log.warn("Failed to parse value_payload for pr_event id={}: {}", event.getId(), e.getMessage());
        }

        return new PayloadValues(weightKg, reps, holdSeconds);
    }

    private record PayloadValues(BigDecimal weightKg, Integer reps, Integer holdSeconds) {}

    // ── Helpers ────────────────────────────────────────────────────────────

    private ExerciseType determineExerciseType(UserExerciseBests currentBests) {
        if (currentBests != null && currentBests.getExerciseType() != null) {
            try {
                return ExerciseType.valueOf(currentBests.getExerciseType());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid exerciseType in user_exercise_bests: {}", currentBests.getExerciseType(), e);
                return ExerciseType.WEIGHTED;
            }
        }
        log.info("Edit cascade defaulting to WEIGHTED for exercise; proper exercise_type column is a future schema improvement");
        return ExerciseType.WEIGHTED;
    }

    private void decrementWeeklyPrCounts(UUID userId, LocalDate weekStart, String prCategory, int coinsAwarded) {
        WeeklyPrCount counts = weeklyPrCountRepo.findByUserIdAndWeekStart(userId, weekStart)
                .orElse(null);

        if (counts == null) {
            log.warn("Weekly PR counts not found for user={} week={} — cannot decrement", userId, weekStart);
            return;
        }

        if ("FIRST_EVER".equals(prCategory)) {
            counts.setFirstEverCount(Math.max(0, (counts.getFirstEverCount() != null ? counts.getFirstEverCount() : 0) - 1));
        } else if ("MAX_ATTEMPT".equals(prCategory)) {
            counts.setMaxAttemptCount(Math.max(0, (counts.getMaxAttemptCount() != null ? counts.getMaxAttemptCount() : 0) - 1));
        } else {
            counts.setPrCount(Math.max(0, (counts.getPrCount() != null ? counts.getPrCount() : 0) - 1));
        }

        int currentTotal = counts.getTotalCoinsAwarded() != null ? counts.getTotalCoinsAwarded() : 0;
        int newTotal = currentTotal - coinsAwarded;
        counts.setTotalCoinsAwarded(Math.max(0, newTotal));

        weeklyPrCountRepo.save(counts);
        log.debug("Decremented weekly_pr_counts for user={} week={} category={}",
                userId, weekStart, prCategory);
    }

    private void incrementWeeklyPrCountsForEdit(UUID userId, LocalDate weekStart, PrCategory category, int coinsAwarded) {
        WeeklyPrCount counts = weeklyPrCountRepo.findByUserIdAndWeekStart(userId, weekStart)
                .orElse(new WeeklyPrCount());

        counts.setUserId(userId);
        counts.setWeekStart(weekStart);

        if (category == PrCategory.FIRST_EVER) {
            counts.setFirstEverCount((counts.getFirstEverCount() != null ? counts.getFirstEverCount() : 0) + 1);
        } else if (category == PrCategory.MAX_ATTEMPT) {
            counts.setMaxAttemptCount((counts.getMaxAttemptCount() != null ? counts.getMaxAttemptCount() : 0) + 1);
        } else {
            counts.setPrCount((counts.getPrCount() != null ? counts.getPrCount() : 0) + 1);
        }

        counts.setTotalCoinsAwarded((counts.getTotalCoinsAwarded() != null ? counts.getTotalCoinsAwarded() : 0) + coinsAwarded);

        weeklyPrCountRepo.save(counts);
        log.debug("Incremented weekly_pr_counts for edit: user={} week={} category={} coins={}",
                userId, weekStart, category, coinsAwarded);
    }

    private String coinTypeFor(PrCategory category) {
        return switch (category) {
            case FIRST_EVER -> "FIRST_EVER";
            case WEIGHT_PR -> "PR_AWARDED";
            case REP_PR -> "PR_AWARDED";
            case VOLUME_PR -> "PR_AWARDED";
            case MAX_ATTEMPT -> "MAX_ATTEMPT";
        };
    }

    private String buildRevocationLabel(String exerciseId, String prCategory) {
        return "PR revoked · " + prCategory + " on " + exerciseId;
    }

    private String buildRestorationLabel(String exerciseId, String prCategory) {
        return "PR restored · " + prCategory + " on " + exerciseId;
    }

    private String buildNewPrLabel(String exerciseId, PrCategory category) {
        String categoryName = switch (category) {
            case FIRST_EVER -> "First ever";
            case WEIGHT_PR -> "Weight PR";
            case REP_PR -> "Rep PR";
            case VOLUME_PR -> "Volume PR";
            case MAX_ATTEMPT -> "Max attempt";
        };
        return categoryName + " · " + exerciseId;
    }

    private LocalDate weekStartFor(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
