package com.fittribe.api.scheduler;

import com.fittribe.api.entity.User;
import com.fittribe.api.repository.UserPlanRepository;
import com.fittribe.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Monday weekly-goal promotion job.
 *
 * Runs at 00:00 IST every Monday (18:30 UTC Sunday).
 * Promotes pending_weekly_goal → weekly_goal for any user who changed
 * their goal mid-week via PUT /users/profile.
 *
 * Also invalidates the current week's user_plans row so generatePlan()
 * regenerates it with the correct split for the new goal.
 *
 * Why pending: changing weekly_goal mid-week would corrupt the
 * current week's completedThisWeek / weeklyGoalHit calculations.
 * The new goal takes effect cleanly at the start of the next week.
 */
@Component
public class WeeklyGoalScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyGoalScheduler.class);

    private final UserRepository     userRepo;
    private final UserPlanRepository planRepo;

    public WeeklyGoalScheduler(UserRepository userRepo, UserPlanRepository planRepo) {
        this.userRepo = userRepo;
        this.planRepo = planRepo;
    }

    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Kolkata")
    @Transactional
    public void promoteWeeklyGoals() {
        List<User> pending = userRepo.findAllByPendingWeeklyGoalIsNotNull();

        // This Monday — the new week the promoted goal applies to
        LocalDate monday = LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        int promoted = 0;
        for (User user : pending) {
            user.setWeeklyGoal(user.getPendingWeeklyGoal());
            user.setPendingWeeklyGoal(null);
            userRepo.save(user);

            // Invalidate any existing plan for this week so it regenerates
            // with the correct split for the newly promoted weekly_goal
            planRepo.findByUserIdAndWeekStartDate(user.getId(), monday)
                    .ifPresent(planRepo::delete);

            promoted++;
        }

        log.info("WeeklyGoalScheduler: promoted={} users to new weekly_goal, plans invalidated", promoted);
    }
}
