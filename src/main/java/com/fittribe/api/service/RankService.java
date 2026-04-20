package com.fittribe.api.service;

import com.fittribe.api.entity.User;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class RankService {

    private static final Logger log = LoggerFactory.getLogger(RankService.class);

    // Thresholds — minimum training days to hold each rank.
    // ROOKIE: 0-10, GRINDER: 11-50, ATHLETE: 51-150, LEGEND: 151+
    public static final int GRINDER_MIN = 11;
    public static final int ATHLETE_MIN = 51;
    public static final int LEGEND_MIN  = 151;

    // Coin rewards paid on promotion.
    public static final Map<String, Integer> PROMOTION_REWARD = Map.of(
            "GRINDER", 100,
            "ATHLETE", 200,
            "LEGEND",  300
    );

    private final UserRepository            userRepo;
    private final WorkoutSessionRepository  sessionRepo;
    private final CoinService               coinService;

    public RankService(UserRepository userRepo,
                       WorkoutSessionRepository sessionRepo,
                       CoinService coinService) {
        this.userRepo    = userRepo;
        this.sessionRepo = sessionRepo;
        this.coinService = coinService;
    }

    /** Pure function: compute rank from training days. */
    public static String rankFor(int trainingDays) {
        if (trainingDays >= LEGEND_MIN)  return "LEGEND";
        if (trainingDays >= ATHLETE_MIN) return "ATHLETE";
        if (trainingDays >= GRINDER_MIN) return "GRINDER";
        return "ROOKIE";
    }

    /** Next rank after current. Returns null if at LEGEND. */
    public static String nextRank(String currentRank) {
        return switch (currentRank) {
            case "ROOKIE"  -> "GRINDER";
            case "GRINDER" -> "ATHLETE";
            case "ATHLETE" -> "LEGEND";
            default        -> null; // LEGEND or unknown
        };
    }

    /** Days remaining until next rank. Returns 0 at LEGEND. */
    public static int daysToNext(int trainingDays) {
        if (trainingDays < GRINDER_MIN) return GRINDER_MIN - trainingDays;
        if (trainingDays < ATHLETE_MIN) return ATHLETE_MIN - trainingDays;
        if (trainingDays < LEGEND_MIN)  return LEGEND_MIN  - trainingDays;
        return 0;
    }

    /** Ordering for "only promote, never downgrade" check. */
    private static int rankOrder(String rank) {
        return switch (rank) {
            case "GRINDER" -> 1;
            case "ATHLETE" -> 2;
            case "LEGEND"  -> 3;
            default        -> 0; // ROOKIE
        };
    }

    /**
     * Checks whether the user has crossed a rank threshold and promotes them if so.
     * Rank is never downgraded at runtime — only moves up.
     * Awards coins via CoinService on promotion, idempotent by (userId, RANK_PROMOTION, refId).
     */
    @Transactional
    public void checkAndPromote(UUID userId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return;

        int trainingDays   = sessionRepo.countDistinctTrainingDays(userId);
        String currentRank = user.getRank() != null ? user.getRank() : "ROOKIE";
        String targetRank  = rankFor(trainingDays);

        if (rankOrder(targetRank) > rankOrder(currentRank)) {
            user.setRank(targetRank);
            userRepo.save(user);

            Integer reward = PROMOTION_REWARD.get(targetRank);
            if (reward != null && reward > 0) {
                String refId = "RANK_PROMOTION:" + targetRank;
                coinService.awardCoins(userId, reward, "RANK_PROMOTION",
                        "Promoted to " + targetRank, refId);
            }

            log.info("User {} promoted from {} to {} (training days: {})",
                    userId, currentRank, targetRank, trainingDays);
        }
    }
}
