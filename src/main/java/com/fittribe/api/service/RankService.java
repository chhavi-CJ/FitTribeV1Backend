package com.fittribe.api.service;

import com.fittribe.api.entity.User;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RankService {

    private static final Logger log = LoggerFactory.getLogger(RankService.class);

    private final UserRepository            userRepo;
    private final WorkoutSessionRepository  sessionRepo;
    private final JdbcTemplate              jdbcTemplate;

    public RankService(UserRepository userRepo,
                       WorkoutSessionRepository sessionRepo,
                       JdbcTemplate jdbcTemplate) {
        this.userRepo     = userRepo;
        this.sessionRepo  = sessionRepo;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Checks whether the user has crossed a rank threshold and promotes them
     * if so. Rank is never downgraded — only moves up.
     */
    public void checkAndPromote(UUID userId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return;

        String currentRank = user.getRank() != null ? user.getRank() : "ROOKIE";
        int coins          = user.getCoins() != null ? user.getCoins() : 0;

        int sessions      = sessionRepo.countByUserIdAndStatus(userId, "COMPLETED");
        int weeklyGoalsHit = fetchWeeklyGoalsHit(userId);

        // Evaluate highest rank first
        String targetRank;
        if (sessions >= 75 && weeklyGoalsHit >= 20 && coins >= 300) {
            targetRank = "LEGEND";
        } else if (sessions >= 30 && weeklyGoalsHit >= 8 && coins >= 100) {
            targetRank = "ATHLETE";
        } else if (sessions >= 10 && weeklyGoalsHit >= 3) {
            targetRank = "GRINDER";
        } else {
            targetRank = "ROOKIE";
        }

        // Only promote — never downgrade
        if (rankOrder(targetRank) > rankOrder(currentRank)) {
            user.setRank(targetRank);
            userRepo.save(user);
            log.info("User {} promoted from {} to {}", userId, currentRank, targetRank);
        }
    }

    /**
     * Counts coin_transactions rows with type='WEEKLY_GOAL'.
     * The type column may not exist yet — returns 0 gracefully if the query fails.
     */
    private int fetchWeeklyGoalsHit(UUID userId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM coin_transactions WHERE user_id = ? AND type = 'WEEKLY_GOAL'",
                    Integer.class,
                    userId);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.debug("weekly_goals_hit query failed (type column may not exist yet): {}", e.getMessage());
            return 0;
        }
    }

    private int rankOrder(String rank) {
        return switch (rank) {
            case "GRINDER" -> 1;
            case "ATHLETE" -> 2;
            case "LEGEND"  -> 3;
            default        -> 0; // ROOKIE
        };
    }
}
