package com.fittribe.api.prv2.service;

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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

/**
 * Handles PR supersession, un-supersession, and coin reconciliation when a user
 * edits or deletes a set during the edit window. Operates on pr_events and
 * user_exercise_bests only — JSONB mutation is handled by the controller.
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

    public PrEditCascadeService(
            PRDetector prDetector,
            UserExerciseBestsRepository userExerciseBestsRepo,
            PrEventRepository prEventRepo,
            WeeklyPrCountRepository weeklyPrCountRepo,
            CoinService coinService,
            PlatformTransactionManager transactionManager) {
        this.prDetector = prDetector;
        this.userExerciseBestsRepo = userExerciseBestsRepo;
        this.prEventRepo = prEventRepo;
        this.weeklyPrCountRepo = weeklyPrCountRepo;
        this.coinService = coinService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Process a set edit: supersede old PR events, revoke coins, detect new PRs.
     *
     * <p>Called AFTER workout_sessions.exercises JSONB has been updated with new
     * values. This method determines which PRs are invalidated and handles the
     * cascade: revocation, redetection, coin award/revocation.
     *
     * @param userId    the user who edited
     * @param sessionId the session containing the edited set
     * @param setId     the edited set's ID (for set-level pr_events lookup)
     * @param oldValue  the set's pre-edit values (LoggedSet)
     * @param newValue  the set's post-edit values (LoggedSet)
     */
    public void processSetEdit(UUID userId, UUID sessionId, UUID setId,
                               LoggedSet oldValue, LoggedSet newValue) {
        log.debug("Starting edit cascade for user={} session={} exercise={} setId={}",
                userId, sessionId, oldValue.exerciseId(), setId);

        processEditOrDelete(userId, sessionId, setId, oldValue, newValue, false);
    }

    /**
     * Process a set deletion: supersede old PR events, revoke coins, no new PR detection.
     *
     * <p>Called AFTER the set has been removed from workout_sessions.exercises JSONB.
     * Marks any associated pr_events as superseded.
     *
     * @param userId    the user who deleted
     * @param sessionId the session containing the deleted set
     * @param setId     the deleted set's ID
     * @param oldValue  the set's pre-delete values (LoggedSet)
     */
    public void processSetDelete(UUID userId, UUID sessionId, UUID setId, LoggedSet oldValue) {
        log.debug("Starting delete cascade for user={} session={} exercise={} setId={}",
                userId, sessionId, oldValue.exerciseId(), setId);

        processEditOrDelete(userId, sessionId, setId, oldValue, null, true);
    }

    /**
     * Process deletion of all sets of an exercise: supersede all related PR events.
     *
     * @param userId      the user who deleted
     * @param sessionId   the session containing the exercise
     * @param exerciseId  the exercise being deleted
     * @param oldValues   list of deleted sets (for coin revocation reference)
     */
    public void processExerciseDelete(UUID userId, UUID sessionId, String exerciseId,
                                      List<LoggedSet> oldValues) {
        log.debug("Starting exercise delete cascade for user={} session={} exercise={}",
                userId, sessionId, exerciseId);

        // Find all non-superseded pr_events for this session+exercise
        try {
            transactionTemplate.executeWithoutResult(txStatus -> {
                List<PrEvent> activeEvents = prEventRepo
                        .findByUserIdAndSessionIdAndExerciseIdAndSupersededAtNull(userId, sessionId, exerciseId);

                for (PrEvent event : activeEvents) {
                    // Mark superseded
                    event.setSupersededAt(Instant.now());
                    prEventRepo.save(event);
                    log.debug("Superseded PR event: id={} category={}", event.getId(), event.getPrCategory());

                    // Revoke coins via deferred debt settlement
                    if (event.getCoinsAwarded() > 0) {
                        coinService.awardCoins(userId, -event.getCoinsAwarded(), "PR_REVOKED",
                                buildRevocationLabel(exerciseId, event.getPrCategory()),
                                event.getId().toString());
                    }

                    // Decrement weekly_pr_counts
                    decrementWeeklyPrCounts(userId, event.getWeekStart(), event.getPrCategory(), event.getCoinsAwarded());
                }

                // Rebuild user_exercise_bests for this (user, exercise)
                rebuildExerciseBests(userId, exerciseId);
            });
        } catch (Exception e) {
            log.warn("Failed to process exercise delete cascade for user={} session={} exercise={}",
                    userId, sessionId, exerciseId, e);
        }
    }

    /**
     * Internal: handle both edit and delete cascades.
     */
    private void processEditOrDelete(UUID userId, UUID sessionId, UUID setId,
                                      LoggedSet oldValue, LoggedSet newValue, boolean isDelete) {
        try {
            transactionTemplate.executeWithoutResult(txStatus -> {
                // Step 1: Find non-superseded PR events for this session+set
                List<PrEvent> activeEvents = prEventRepo
                        .findByUserIdAndSessionIdAndSetIdAndSupersededAtNull(userId, sessionId, setId);

                log.debug("Found {} active PR events for session={} set={}", activeEvents.size(), sessionId, setId);

                // Step 2: Supersede them and revoke coins
                for (PrEvent event : activeEvents) {
                    event.setSupersededAt(Instant.now());
                    prEventRepo.save(event);
                    log.debug("Superseded PR event: id={} category={} coins={}",
                            event.getId(), event.getPrCategory(), event.getCoinsAwarded());

                    // Revoke coins via deferred debt settlement (HLD §4)
                    if (event.getCoinsAwarded() > 0) {
                        coinService.awardCoins(userId, -event.getCoinsAwarded(), "PR_REVOKED",
                                buildRevocationLabel(oldValue.exerciseId(), event.getPrCategory()),
                                event.getId().toString());
                    }

                    // Decrement weekly_pr_counts for the week the event was earned
                    decrementWeeklyPrCounts(userId, event.getWeekStart(), event.getPrCategory(), event.getCoinsAwarded());
                }

                // Step 3: If delete, stop here. If edit, re-run detection on new value
                if (!isDelete && newValue != null) {
                    // Load current bests for this (user, exercise) — these are unchanged by the edit
                    UserExerciseBests currentBests = userExerciseBestsRepo
                            .findByUserIdAndExerciseId(userId, oldValue.exerciseId())
                            .orElse(null);

                    // Determine exercise type
                    ExerciseType exerciseType = determineExerciseType(currentBests);

                    // Run PR detection on new value
                    var prResult = prDetector.detect(newValue, currentBests, exerciseType);

                    log.debug("Edit cascade PR detection: isPR={} category={} for exercise={}",
                            prResult.isPR(), prResult.category(), oldValue.exerciseId());

                    if (prResult.isPR()) {
                        // Write new pr_event row
                        LocalDate weekStart = weekStartFor(Instant.now());
                        PrEvent newEvent = new PrEvent();
                        newEvent.setUserId(userId);
                        newEvent.setExerciseId(oldValue.exerciseId());
                        newEvent.setSessionId(sessionId);
                        newEvent.setSetId(setId);
                        newEvent.setPrCategory(prResult.category().toString());
                        newEvent.setWeekStart(weekStart);
                        newEvent.setSignalsMet(prResult.signalsMet().toString());
                        newEvent.setValuePayload(prResult.valuePayload().toString());
                        newEvent.setCoinsAwarded(prResult.suggestedCoins());
                        newEvent.setDetectorVersion(prResult.detectorVersion());

                        PrEvent saved = prEventRepo.save(newEvent);
                        log.debug("Created new PR event after edit: id={} category={} coins={}",
                                saved.getId(), saved.getPrCategory(), saved.getCoinsAwarded());

                        // Update user_exercise_bests if PR fired
                        updateOrCreateBestsForEdit(userId, oldValue.exerciseId(), exerciseType, newValue, prResult, currentBests);

                        // Increment weekly_pr_counts
                        incrementWeeklyPrCountsForEdit(userId, weekStart, prResult.category(), prResult.suggestedCoins());

                        // Award coins if applicable
                        if (prResult.suggestedCoins() > 0) {
                            String coinType = coinTypeFor(prResult.category());
                            String coinLabel = buildNewPrLabel(oldValue.exerciseId(), prResult.category());
                            coinService.awardCoins(userId, prResult.suggestedCoins(), coinType, coinLabel,
                                    saved.getId().toString());
                        }
                    } else {
                        // No PR after edit — still update bests with new values if they improved
                        updateNonPrBestsForEdit(userId, oldValue.exerciseId(), exerciseType, newValue, currentBests);
                    }
                } else if (isDelete) {
                    // Set was deleted — rebuild bests for the exercise
                    rebuildExerciseBests(userId, oldValue.exerciseId());
                }
            });
        } catch (Exception e) {
            log.warn("Failed to process edit/delete cascade for user={} session={} setId={}",
                    userId, sessionId, setId, e);
        }
    }

    /**
     * Determine exercise type from current bests or default to WEIGHTED.
     */
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

    /**
     * Update or create bests when edit results in a PR.
     */
    private void updateOrCreateBestsForEdit(UUID userId, String exerciseId, ExerciseType exerciseType,
                                             LoggedSet newValue, PRResult prResult, UserExerciseBests currentBests) {
        UserExerciseBests bests = currentBests != null ? currentBests : new UserExerciseBests();

        bests.setUserId(userId);
        bests.setExerciseId(exerciseId);
        bests.setExerciseType(exerciseType.toString());
        // Note: do NOT change total_sessions_with_exercise — session count doesn't change on edits
        bests.setLastLoggedAt(Instant.now());

        // Update category-specific fields based on PR category
        PrCategory category = prResult.category();
        if (category == PrCategory.FIRST_EVER || category == PrCategory.WEIGHT_PR) {
            bests.setBestWtKg(newValue.weightKg());
            bests.setRepsAtBestWt(newValue.reps());
        } else if (category == PrCategory.REP_PR) {
            bests.setRepsAtBestWt(newValue.reps());
        }

        userExerciseBestsRepo.save(bests);
        log.debug("Updated user_exercise_bests for edit: user={} exercise={} category={}",
                userId, exerciseId, category);
    }

    /**
     * Update bests when edit does NOT result in a PR but may have changed values.
     */
    private void updateNonPrBestsForEdit(UUID userId, String exerciseId, ExerciseType exerciseType,
                                         LoggedSet newValue, UserExerciseBests currentBests) {
        UserExerciseBests bests = currentBests;
        if (bests == null) {
            // First-time exercise (shouldn't happen on edit, but handle it)
            bests = new UserExerciseBests();
            bests.setUserId(userId);
            bests.setExerciseId(exerciseId);
            bests.setExerciseType(exerciseType.toString());
        }

        bests.setLastLoggedAt(Instant.now());
        userExerciseBestsRepo.save(bests);
        log.debug("Updated user_exercise_bests (non-PR) for edit: user={} exercise={}", userId, exerciseId);
    }

    /**
     * Rebuild bests for an exercise after deletion.
     * Future work: actually rebuild from session history. For Phase 3b, just mark updated.
     */
    private void rebuildExerciseBests(UUID userId, String exerciseId) {
        // TODO: Phase 3c — actually rebuild from session history
        // For now, just touch the timestamp
        userExerciseBestsRepo.findByUserIdAndExerciseId(userId, exerciseId)
                .ifPresent(bests -> {
                    bests.setLastLoggedAt(Instant.now());
                    userExerciseBestsRepo.save(bests);
                    log.debug("Marked user_exercise_bests updated after delete: user={} exercise={}",
                            userId, exerciseId);
                });
    }

    /**
     * Decrement weekly_pr_counts when a PR event is superseded.
     * Also decrements totalCoinsAwarded, guarding against negative values.
     */
    private void decrementWeeklyPrCounts(UUID userId, LocalDate weekStart, String prCategory, int coinsAwarded) {
        WeeklyPrCount counts = weeklyPrCountRepo.findByUserIdAndWeekStart(userId, weekStart)
                .orElse(null);

        if (counts == null) {
            log.warn("Weekly PR counts not found for user={} week={} — cannot decrement", userId, weekStart);
            return;
        }

        // Decrement the appropriate counter based on category
        if ("FIRST_EVER".equals(prCategory)) {
            counts.setFirstEverCount(Math.max(0, (counts.getFirstEverCount() != null ? counts.getFirstEverCount() : 0) - 1));
        } else if ("MAX_ATTEMPT".equals(prCategory)) {
            counts.setMaxAttemptCount(Math.max(0, (counts.getMaxAttemptCount() != null ? counts.getMaxAttemptCount() : 0) - 1));
        } else {
            // WEIGHT_PR, REP_PR, VOLUME_PR
            counts.setPrCount(Math.max(0, (counts.getPrCount() != null ? counts.getPrCount() : 0) - 1));
        }

        // Decrement total coins awarded, guard against going negative
        int currentTotal = counts.getTotalCoinsAwarded() != null ? counts.getTotalCoinsAwarded() : 0;
        int newTotal = currentTotal - coinsAwarded;
        if (newTotal < 0) {
            log.warn("Weekly PR counts totalCoinsAwarded went negative: user={} week={} currentTotal={} coinsAwarded={}",
                    userId, weekStart, currentTotal, coinsAwarded);
            counts.setTotalCoinsAwarded(0);
        } else {
            counts.setTotalCoinsAwarded(newTotal);
        }

        weeklyPrCountRepo.save(counts);
        log.debug("Decremented weekly_pr_counts for user={} week={} category={}",
                userId, weekStart, prCategory);
    }

    /**
     * Increment weekly_pr_counts when a new PR is detected during edit.
     */
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
            // WEIGHT_PR, REP_PR, VOLUME_PR
            counts.setPrCount((counts.getPrCount() != null ? counts.getPrCount() : 0) + 1);
        }

        // Increment total coins awarded
        counts.setTotalCoinsAwarded((counts.getTotalCoinsAwarded() != null ? counts.getTotalCoinsAwarded() : 0) + coinsAwarded);

        weeklyPrCountRepo.save(counts);
        log.debug("Incremented weekly_pr_counts for edit: user={} week={} category={} coins={}",
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
     * Build label for a revoked PR coin transaction.
     */
    private String buildRevocationLabel(String exerciseId, String prCategory) {
        return "PR revoked · " + prCategory + " on " + exerciseId;
    }

    /**
     * Build label for a new PR coin transaction during edit.
     */
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

    /**
     * Calculate Monday of the week for a given instant.
     */
    private LocalDate weekStartFor(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
