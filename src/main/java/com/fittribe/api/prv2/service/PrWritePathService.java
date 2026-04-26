package com.fittribe.api.prv2.service;

import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.PrEvent;
import com.fittribe.api.entity.UserExerciseBests;
import com.fittribe.api.entity.WeeklyPrCount;
import com.fittribe.api.prv2.detector.ExerciseType;
import com.fittribe.api.prv2.detector.LoggedSet;
import com.fittribe.api.prv2.detector.PRDetector;
import com.fittribe.api.prv2.detector.PRResult;
import com.fittribe.api.prv2.detector.PrCategory;
import com.fittribe.api.repository.ExerciseRepository;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for writing PR detection results to the database.
 *
 * <p>Called from SessionController.finishSession() AFTER the existing
 * LOG_WORKOUT coin award and session persistence. Processes each logged
 * set through PRDetector, persists PR events, updates user_exercise_bests,
 * increments weekly_pr_counts, and awards coins.
 *
 * <h3>Transaction boundaries:</h3>
 * - processSessionFinish runs with REQUIRES_NEW propagation, ensuring it
 *   runs AFTER the session save transaction commits
 * - Each set's processing runs in its own TransactionTemplate for isolation
 *   and per-set error handling (P4 pattern per HLD §7.3)
 */
@Service
public class PrWritePathService {

    private static final Logger log = LoggerFactory.getLogger(PrWritePathService.class);

    private final PRDetector prDetector;
    private final UserExerciseBestsRepository userExerciseBestsRepo;
    private final PrEventRepository prEventRepo;
    private final WeeklyPrCountRepository weeklyPrCountRepo;
    private final CoinService coinService;
    private final TransactionTemplate transactionTemplate;
    private final ExerciseRepository exerciseRepo;

    public PrWritePathService(
            PRDetector prDetector,
            UserExerciseBestsRepository userExerciseBestsRepo,
            PrEventRepository prEventRepo,
            WeeklyPrCountRepository weeklyPrCountRepo,
            CoinService coinService,
            PlatformTransactionManager transactionManager,
            ExerciseRepository exerciseRepo) {
        this.prDetector = prDetector;
        this.userExerciseBestsRepo = userExerciseBestsRepo;
        this.prEventRepo = prEventRepo;
        this.weeklyPrCountRepo = weeklyPrCountRepo;
        this.coinService = coinService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.exerciseRepo = exerciseRepo;
    }

    /**
     * Process logged sets from a finished session through PR detection.
     *
     * <p>Runs AFTER the core session-finish transaction commits. Each set
     * is processed independently with its own transaction and error handling,
     * ensuring a failure on one set doesn't prevent others from processing.
     *
     * @param userId    the user who logged the session
     * @param sessionId the session that finished
     * @param sets      list of LoggedSet from the session's exercises JSONB
     */
    public void processSessionFinish(UUID userId, UUID sessionId, List<LoggedSet> sets) {
        if (sets == null || sets.isEmpty()) {
            log.debug("No sets to process for session={}", sessionId);
            return;
        }

        log.debug("Starting PR detection for session={} with {} sets", sessionId, sets.size());

        // Process each set independently with its own transaction (P4 pattern)
        for (LoggedSet set : sets) {
            processSet(userId, sessionId, set);
        }

        log.debug("Completed PR detection for session={}", sessionId);
    }

    /**
     * Process a single logged set: detect PR, persist events, update bests, award coins.
     *
     * <p>Wrapped in its own transaction. Failures here are logged at WARN level
     * and do not prevent subsequent sets from processing.
     */
    private void processSet(UUID userId, UUID sessionId, LoggedSet set) {
        try {
            transactionTemplate.executeWithoutResult(txStatus -> {
                // Step 1: Load current bests for this (user, exercise)
                UserExerciseBests currentBests = userExerciseBestsRepo
                        .findByUserIdAndExerciseId(userId, set.exerciseId())
                        .orElse(null);

                // Step 2: Fetch exercise metadata and determine type
                Exercise exercise = exerciseRepo.findById(set.exerciseId()).orElse(null);
                ExerciseType exerciseType = determineExerciseType(currentBests, set, exercise);

                // Step 3: Run PR detection
                var prResult = prDetector.detect(set, currentBests, exerciseType);

                log.debug("PR detection for user={} exercise={}: isPR={} category={}",
                        userId, set.exerciseId(), prResult.isPR(), prResult.category());

                if (!prResult.isPR()) {
                    // Non-PR sets still update last_logged_at and increment session count
                    updateNonPrBests(userId, set.exerciseId(), exerciseType, currentBests, set);
                    return;
                }

                // Step 4: Handle PR event
                LocalDate weekStart = weekStartFor(Instant.now());

                // Create pr_event row
                PrEvent prEvent = new PrEvent();
                prEvent.setUserId(userId);
                prEvent.setExerciseId(set.exerciseId());
                prEvent.setSessionId(sessionId);
                prEvent.setSetId(set.setId());
                prEvent.setPrCategory(prResult.category().toString());
                prEvent.setWeekStart(weekStart);

                // Pass Maps directly — Hibernate 6 + @JdbcTypeCode(SqlTypes.JSON)
                // handles Jackson serialization natively on Map fields.
                prEvent.setSignalsMet(new java.util.HashMap<>(prResult.signalsMet()));
                prEvent.setValuePayload(new java.util.HashMap<>(prResult.valuePayload()));

                prEvent.setDetectorVersion(prResult.detectorVersion());
                prEvent.setCoinsAwarded(prResult.suggestedCoins());

                PrEvent saved = prEventRepo.save(prEvent);
                log.debug("Saved PR event: id={} category={} coins={}",
                        saved.getId(), saved.getPrCategory(), saved.getCoinsAwarded());

                // Step 5: Update or create user_exercise_bests
                updateOrCreateBests(userId, set.exerciseId(), exerciseType, set, prResult, currentBests);

                // Step 6: Increment weekly_pr_counts
                incrementWeeklyPrCounts(userId, weekStart, prResult.category(), prResult.suggestedCoins());

                // Step 7: Award coins (only if suggestedCoins > 0)
                if (prResult.suggestedCoins() > 0) {
                    String coinType = coinTypeFor(prResult.category());
                    String coinLabel = coinLabelFor(set.exerciseId(), prResult.category());
                    coinService.awardCoins(userId, prResult.suggestedCoins(), coinType,
                            coinLabel, saved.getId().toString());
                }
            });
        } catch (Exception e) {
            // Log at WARN and continue — failures on one set must not block others
            log.warn("Failed to process PR detection for user={} exercise={} session={}",
                    userId, set.exerciseId(), sessionId, e);
        }
    }

    /**
     * Determine exercise type from set data, exercise metadata, or existing bests.
     *
     * <p>Priority order:
     * <ol>
     *   <li>TIMED: set has holdSeconds — Plank etc. classified correctly for future TIMED PR support</li>
     *   <li>BODYWEIGHT_UNASSISTED: no existing bests row and exercise.isBodyweight() = true</li>
     *   <li>Existing bests exerciseType: preserves the type written on the first session</li>
     *   <li>WEIGHTED: fallback default</li>
     * </ol>
     */
    private ExerciseType determineExerciseType(UserExerciseBests currentBests, LoggedSet set, Exercise exercise) {
        // TIMED takes priority: if the set has hold seconds it's a timed exercise
        // regardless of is_bodyweight (prevents Plank from being classified as BODYWEIGHT_UNASSISTED)
        if (set.holdSeconds() != null && set.holdSeconds() > 0) {
            return ExerciseType.TIMED;
        }

        // For new bests rows, look up exercise metadata to determine type
        if (currentBests == null || currentBests.getExerciseType() == null) {
            // Bodyweight: classify via exercise catalog is_bodyweight flag
            if (exercise != null && exercise.isBodyweight()) {
                return ExerciseType.BODYWEIGHT_UNASSISTED;
            }
            log.info("PR detection defaulting to WEIGHTED for exerciseId={}",
                    set.exerciseId());
            return ExerciseType.WEIGHTED;
        }

        // Existing bests row: preserve the type already written
        try {
            return ExerciseType.valueOf(currentBests.getExerciseType());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid exerciseType in user_exercise_bests: {}",
                    currentBests.getExerciseType(), e);
            return ExerciseType.WEIGHTED;
        }
    }

    /**
     * Update bests for a non-PR set: increment totalSessionsWithExercise,
     * update last_logged_at, and track bestReps even when no PR fires.
     */
    private void updateNonPrBests(UUID userId, String exerciseId, ExerciseType exerciseType,
            UserExerciseBests currentBests, LoggedSet set) {
        UserExerciseBests bests;
        if (currentBests == null) {
            // First time logging this exercise (non-PR case shouldn't happen but handle it)
            bests = new UserExerciseBests();
            bests.setUserId(userId);
            bests.setExerciseId(exerciseId);
            bests.setExerciseType(exerciseType.toString());
            bests.setTotalSessionsWithExercise(1);
        } else {
            bests = currentBests;
            bests.setTotalSessionsWithExercise((bests.getTotalSessionsWithExercise() != null ?
                    bests.getTotalSessionsWithExercise() : 0) + 1);
        }
        // Track bestReps on every set so the baseline stays current for future REP_PR comparisons
        if (set.reps() != null) {
            int current = bests.getBestReps() != null ? bests.getBestReps() : 0;
            bests.setBestReps(Math.max(current, set.reps()));
        }
        bests.setLastLoggedAt(Instant.now());
        userExerciseBestsRepo.save(bests);
    }

    /**
     * Create or update user_exercise_bests with the PR data.
     *
     * <p>If currentBests is null, create new row. Otherwise, update existing row.
     * Match category to fields: WEIGHT_PR updates best_wt_kg/reps_at_best_wt,
     * REP_PR updates reps fields, VOLUME_PR updates best_set_volume_kg, etc.
     */
    private void updateOrCreateBests(UUID userId, String exerciseId, ExerciseType exerciseType,
            LoggedSet set, PRResult prResult, UserExerciseBests currentBests) {
        UserExerciseBests bests = currentBests != null ? currentBests : new UserExerciseBests();

        bests.setUserId(userId);
        bests.setExerciseId(exerciseId);
        bests.setExerciseType(exerciseType.toString());
        bests.setTotalSessionsWithExercise((bests.getTotalSessionsWithExercise() != null ?
                bests.getTotalSessionsWithExercise() : 0) + 1);
        bests.setLastLoggedAt(Instant.now());

        // Update category-specific fields
        PrCategory category = prResult.category();
        if (category == PrCategory.FIRST_EVER || category == PrCategory.WEIGHT_PR) {
            bests.setBestWtKg(set.weightKg());
            bests.setRepsAtBestWt(set.reps());
        } else if (category == PrCategory.REP_PR) {
            bests.setRepsAtBestWt(set.reps());
        }
        // bestReps: set on FIRST_EVER (establishes baseline) and REP_PR (records new max)
        if ((category == PrCategory.FIRST_EVER || category == PrCategory.REP_PR) && set.reps() != null) {
            bests.setBestReps(set.reps());
        }

        if (category == PrCategory.VOLUME_PR && set.weightKg() != null && set.reps() != null) {
            BigDecimal volume = set.weightKg().multiply(BigDecimal.valueOf(set.reps()));
            bests.setBestSetVolumeKg(volume);
        }

        if (category == PrCategory.FIRST_EVER && set.holdSeconds() != null) {
            bests.setBestHoldSeconds(set.holdSeconds());
        }

        userExerciseBestsRepo.save(bests);
        log.debug("Updated user_exercise_bests for user={} exercise={}", userId, exerciseId);
    }

    /**
     * Increment the appropriate counter in weekly_pr_counts.
     * Also increments totalCoinsAwarded to track total coins earned this week.
     */
    private void incrementWeeklyPrCounts(UUID userId, LocalDate weekStart, PrCategory category, int coinsAwarded) {
        WeeklyPrCount counts = weeklyPrCountRepo.findByUserIdAndWeekStart(userId, weekStart)
                .orElse(new WeeklyPrCount());

        counts.setUserId(userId);
        counts.setWeekStart(weekStart);

        if (category == PrCategory.FIRST_EVER) {
            counts.setFirstEverCount((counts.getFirstEverCount() != null ? counts.getFirstEverCount() : 0) + 1);
        } else if (category == PrCategory.MAX_ATTEMPT) {
            counts.setMaxAttemptCount((counts.getMaxAttemptCount() != null ? counts.getMaxAttemptCount() : 0) + 1);
        } else {
            // WEIGHT_PR, REP_PR, VOLUME_PR all increment prCount
            counts.setPrCount((counts.getPrCount() != null ? counts.getPrCount() : 0) + 1);
        }

        // Increment total coins awarded this week (Phase 3b bug fix)
        int currentTotal = counts.getTotalCoinsAwarded() != null ? counts.getTotalCoinsAwarded() : 0;
        counts.setTotalCoinsAwarded(currentTotal + coinsAwarded);

        weeklyPrCountRepo.save(counts);
        log.debug("Incremented weekly_pr_counts for user={} week={} category={} coinsAwarded={}",
                userId, weekStart, category, coinsAwarded);
    }

    /**
     * Map PR category to coin transaction type.
     */
    private String coinTypeFor(PrCategory category) {
        return switch (category) {
            case FIRST_EVER -> "FIRST_EVER";
            case WEIGHT_PR -> "PR_AWARDED";
            case REP_PR -> "PR_AWARDED";
            case VOLUME_PR -> "PR_AWARDED";
            case MAX_ATTEMPT -> "MAX_ATTEMPT";
        };
    }

    /**
     * Build human-readable coin label.
     */
    private String coinLabelFor(String exerciseId, PrCategory category) {
        String categoryName = switch (category) {
            case FIRST_EVER -> "First ever";
            case WEIGHT_PR -> "Weight PR";
            case REP_PR -> "Rep PR";
            case VOLUME_PR -> "Volume PR";
            case MAX_ATTEMPT -> "Max attempt";
        };
        return categoryName + " · " + exerciseId;
    }

    /**
     * Calculate Monday of the week for a given instant.
     * Uses UTC for consistency with SessionController.
     */
    private LocalDate weekStartFor(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
