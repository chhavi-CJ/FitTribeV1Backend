package com.fittribe.api.scheduler;

import com.fittribe.api.entity.BonusFreezeGrant;
import com.fittribe.api.entity.User;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.service.BonusFreezeGrantService;
import com.fittribe.api.service.FreezeTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Sunday 23:59 IST — evaluates whether each user's streak survived the current week.
 *
 * Logic:
 * 1. Load all users with streak > 0.
 * 2. Skip accounts younger than 7 days (grace period).
 * 3. Count distinct IST days the user completed a session in this ISO week.
 * 4. If shortfall == 0: streak survives as-is.
 * 5. If shortfall > 0: attempt to cover with bonus freeze grants first,
 *    then purchased freeze tokens.
 * 6. If uncovered shortfall remains: streak resets to 0.
 *
 * NOTE — @Transactional risk: all users run inside ONE transaction. The per-user
 * try/catch prevents application-level exceptions from aborting the job, but if
 * Hibernate marks the transaction rollback-only (e.g. on a ConstraintViolation or
 * OptimisticLockException), saves for all subsequent users will silently fail and
 * the whole transaction rolls back at commit time. For v1 this is acceptable.
 * Recommendation for v2: extract per-user DB writes into a @Transactional(REQUIRES_NEW)
 * helper method so each user's commit is independent.
 */
@Component
public class WeeklyStreakEvaluationScheduler {

    private static final Logger  log        = LoggerFactory.getLogger(WeeklyStreakEvaluationScheduler.class);
    private static final ZoneId  IST        = ZoneId.of("Asia/Kolkata");
    private static final long    GRACE_DAYS = 7;

    private final UserRepository           userRepo;
    private final WorkoutSessionRepository sessionRepo;
    private final BonusFreezeGrantService  bonusFreezeGrantService;
    private final FreezeTransactionService freezeTransactionService;

    public WeeklyStreakEvaluationScheduler(UserRepository userRepo,
                                           WorkoutSessionRepository sessionRepo,
                                           BonusFreezeGrantService bonusFreezeGrantService,
                                           FreezeTransactionService freezeTransactionService) {
        this.userRepo                = userRepo;
        this.sessionRepo             = sessionRepo;
        this.bonusFreezeGrantService  = bonusFreezeGrantService;
        this.freezeTransactionService = freezeTransactionService;
    }

    /**
     * Fires Sunday 23:59:00 IST (cron zone = Asia/Kolkata).
     *
     * ISO week bounds:
     *   weekStart = Monday 00:00 IST of the current week
     *   weekEnd   = next Monday 00:00 IST (= weekStart + 7 days)
     *
     * today.with(DayOfWeek.MONDAY): ISO 8601 defines Monday as day 1 of the week.
     * When today is Sunday (ISO day 7), LocalDate.with(DayOfWeek.MONDAY) adjusts
     * backward to the nearest Monday — i.e. the Monday that opened this ISO week.
     * This is the correct Java behavior and is verified by the ISO adjuster spec
     * in java.time.temporal.WeekFields. If the cron ever fires on a Monday
     * (it won't), with(DayOfWeek.MONDAY) would return Monday itself — still correct.
     */
    @Scheduled(cron = "0 59 23 * * SUN", zone = "Asia/Kolkata")
    @Transactional
    public void evaluateWeeklyStreaks() {

        Instant now = Instant.now();

        // ISO week bounds in IST
        LocalDate today     = LocalDate.now(IST);
        LocalDate weekStart = today.with(DayOfWeek.MONDAY); // Sunday → previous Monday
        Instant   from      = weekStart.atStartOfDay(IST).toInstant();
        Instant   to        = weekStart.plusDays(7).atStartOfDay(IST).toInstant();

        List<User> candidates = userRepo.findAllByStreakGreaterThan(0);

        log.info("WeeklyStreakEval starting: candidates={} week=[{}, {})",
                candidates.size(), from, to);

        int evaluated = 0, broken = 0, savedByBonus = 0, savedByPurchased = 0, skippedGrace = 0;

        for (User user : candidates) {
            try {

                // a. Grace period: skip accounts created within the last 7 days
                if (user.getCreatedAt() != null &&
                        Duration.between(user.getCreatedAt(), now).toDays() < GRACE_DAYS) {
                    skippedGrace++;
                    continue;
                }

                evaluated++;
                int weeklyGoal = user.getWeeklyGoal() != null ? user.getWeeklyGoal() : 4;

                // b. How many distinct IST days did this user work out this week?
                long workedOutDays = sessionRepo.countDistinctCompletedDaysInRange(
                        user.getId(), from, to);

                // c. Shortfall
                int shortfall = (int) Math.max(0, weeklyGoal - workedOutDays);

                // d. Streak survives with no freeze spending
                if (shortfall == 0) {
                    log.info("WeeklyStreakEval: userId={} streak={} SURVIVED (goal met: {}/{})",
                            user.getId(), user.getStreak(), workedOutDays, weeklyGoal);
                    continue;
                }

                // e. Try to cover shortfall with freezes
                int remaining = shortfall;

                // Spend bonus freezes first (soonest-expiring first — getActiveGrants returns ASC by expiresAt)
                List<BonusFreezeGrant> activeBonus = bonusFreezeGrantService.getActiveGrants(user.getId());
                int bonusSpent = 0;
                for (BonusFreezeGrant grant : activeBonus) {
                    if (remaining <= 0) break;
                    bonusFreezeGrantService.consumeGrant(grant, "AUTO_APPLY");
                    try {
                        freezeTransactionService.record(user.getId(), "BONUS_USED", 1,
                                Map.of("weekProtected", from.toString(),
                                       "grantId", grant.getId()));
                    } catch (Exception e) {
                        log.warn("Failed to record BONUS_USED freeze tx for user={}", user.getId(), e);
                    }
                    bonusSpent++;
                    remaining--;
                }

                // Spend purchased freezes next
                int purchasedSpent = 0;
                if (remaining > 0) {
                    int available = user.getPurchasedFreezeBalance() != null
                            ? user.getPurchasedFreezeBalance() : 0;
                    purchasedSpent = Math.min(available, remaining);
                    if (purchasedSpent > 0) {
                        user.setPurchasedFreezeBalance(available - purchasedSpent);
                        remaining -= purchasedSpent;
                    }
                }

                if (remaining > 0) {
                    // Streak breaks — shortfall not covered by any freeze
                    int previousStreak = user.getStreak();
                    user.setStreak(0);
                    userRepo.save(user);
                    broken++;
                    log.info("WeeklyStreakEval: userId={} previousStreak={} BROKEN " +
                            "(shortfall={}, bonusSpent={}, purchasedSpent={}, uncovered={})",
                            user.getId(), previousStreak, shortfall, bonusSpent, purchasedSpent, remaining);
                    if (purchasedSpent > 0) {
                        try {
                            freezeTransactionService.record(user.getId(), "USED_AUTO_APPLY", purchasedSpent,
                                    Map.of("weekProtected", from.toString(), "streakBroke", true));
                        } catch (Exception e) {
                            log.warn("Failed to record USED_AUTO_APPLY (break branch) freeze tx for user={}", user.getId(), e);
                        }
                    }
                    // TODO: notify user — "Your streak ended at " + previousStreak +
                    //       " days. But your lifetime workouts never reset. " +
                    //       "Comeback starts whenever you're ready."
                } else {
                    // Streak survives via freeze(s)
                    if (purchasedSpent > 0) {
                        userRepo.save(user); // persist purchasedFreezeBalance decrement
                        try {
                            freezeTransactionService.record(user.getId(), "USED_AUTO_APPLY", purchasedSpent,
                                    Map.of("weekProtected", from.toString()));
                        } catch (Exception e) {
                            log.warn("Failed to record USED_AUTO_APPLY freeze tx for user={}", user.getId(), e);
                        }
                    }
                    if (bonusSpent > 0) savedByBonus++;
                    if (purchasedSpent > 0) savedByPurchased++;
                    log.info("WeeklyStreakEval: userId={} streak={} SAVED " +
                            "(bonusSpent={}, purchasedSpent={})",
                            user.getId(), user.getStreak(), bonusSpent, purchasedSpent);
                    // TODO: notify user — "We protected your streak with " +
                    //       (bonusSpent + purchasedSpent) + " freeze token(s). " +
                    //       (activeBonus.size() - bonusSpent) + " bonus freezes left, " +
                    //       user.getPurchasedFreezeBalance() + " purchased."
                }

            } catch (Exception e) {
                log.error("WeeklyStreakEval: failed for userId={}", user.getId(), e);
                // Continue to next user — one failure must not abort the whole job
            }
        }

        log.info("WeeklyStreakEval complete: evaluated={} broken={} savedByBonus={} " +
                "savedByPurchased={} skippedGrace={}",
                evaluated, broken, savedByBonus, savedByPurchased, skippedGrace);
    }
}
