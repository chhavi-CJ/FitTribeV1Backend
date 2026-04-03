package com.fittribe.api.scheduler;

import com.fittribe.api.entity.User;
import com.fittribe.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Monday weekly-goal promotion job.
 *
 * Runs at 00:00 IST every Monday (18:30 UTC Sunday).
 * Promotes pending_weekly_goal → weekly_goal for any user who changed
 * their goal mid-week via PUT /users/profile.
 *
 * Why pending: changing weekly_goal mid-week would corrupt the
 * current week's completedThisWeek / weeklyGoalHit calculations.
 * The new goal takes effect cleanly at the start of the next week.
 */
@Component
public class WeeklyGoalScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyGoalScheduler.class);

    private final UserRepository userRepo;

    public WeeklyGoalScheduler(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Scheduled(cron = "0 0 0 * * MON", zone = "Asia/Kolkata")
    @Transactional
    public void promoteWeeklyGoals() {
        List<User> pending = userRepo.findAllByPendingWeeklyGoalIsNotNull();

        int promoted = 0;
        for (User user : pending) {
            user.setWeeklyGoal(user.getPendingWeeklyGoal());
            user.setPendingWeeklyGoal(null);
            userRepo.save(user);
            promoted++;
        }

        log.info("WeeklyGoalScheduler: promoted={} users to new weekly_goal", promoted);
    }
}
