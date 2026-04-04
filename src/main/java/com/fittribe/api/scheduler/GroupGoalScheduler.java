package com.fittribe.api.scheduler;

import com.fittribe.api.service.CoinService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GroupGoalScheduler {

    private static final Logger log = LoggerFactory.getLogger(GroupGoalScheduler.class);

    private final JdbcTemplate jdbcTemplate;
    private final CoinService  coinService;

    public GroupGoalScheduler(JdbcTemplate jdbcTemplate, CoinService coinService) {
        this.jdbcTemplate = jdbcTemplate;
        this.coinService  = coinService;
    }

    /**
     * Runs Sunday 23:59 IST.
     * Awards +40 coins to every member of a group where all non-exempt
     * members hit their weekly goal this week.
     */
    @Scheduled(cron = "0 59 23 * * SUN", zone = "Asia/Kolkata")
    public void evaluateGroupGoals() {
        List<UUID> groupIds = jdbcTemplate.queryForList(
                "SELECT id FROM \"groups\"", UUID.class);

        log.info("GroupGoalScheduler: evaluating {} groups", groupIds.size());

        for (UUID groupId : groupIds) {
            try {
                processGroup(groupId);
            } catch (Exception e) {
                log.error("GroupGoalScheduler: failed for groupId={}", groupId, e);
            }
        }
    }

    private void processGroup(UUID groupId) {
        // 1. Idempotency: skip if this group was already awarded this week
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coin_transactions " +
                "WHERE type = 'GROUP_GOAL' AND reference_id = ? " +
                "  AND created_at >= DATE_TRUNC('week', NOW())",
                Integer.class, groupId.toString());
        if (existing != null && existing > 0) {
            log.debug("GroupGoalScheduler: groupId={} already awarded this week", groupId);
            return;
        }

        // 2. Get all members of this group
        List<UUID> allMembers = jdbcTemplate.queryForList(
                "SELECT user_id FROM group_members WHERE group_id = ?",
                UUID.class, groupId);

        if (allMembers.isEmpty()) return;

        // 3. Find exempt members: had REST/BUSY/SICK on ALL 7 days this week
        List<UUID> mustHit = new java.util.ArrayList<>();
        for (UUID memberId : allMembers) {
            Integer exemptDays = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_day_status " +
                    "WHERE user_id = ? " +
                    "  AND status_date >= DATE_TRUNC('week', CURRENT_DATE) " +
                    "  AND status_date <  DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '7 days' " +
                    "  AND status IN ('REST', 'BUSY', 'SICK')",
                    Integer.class, memberId);
            boolean fullyExempt = exemptDays != null && exemptDays >= 7;
            if (!fullyExempt) mustHit.add(memberId);
        }

        // 4. Need at least 2 active members to qualify
        if (mustHit.size() < 2) {
            log.debug("GroupGoalScheduler: groupId={} skipped — fewer than 2 active members", groupId);
            return;
        }

        // 5. Check if ALL mustHit members hit their individual weekly goal
        for (UUID memberId : mustHit) {
            int weeklyGoal = toInt(jdbcTemplate.queryForObject(
                    "SELECT COALESCE(weekly_goal, 4) FROM users WHERE id = ?",
                    Integer.class, memberId));

            Integer completedThisWeek = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workout_sessions " +
                    "WHERE user_id = ? AND status = 'COMPLETED' " +
                    "  AND finished_at >= DATE_TRUNC('week', NOW()) " +
                    "  AND finished_at <  DATE_TRUNC('week', NOW()) + INTERVAL '7 days'",
                    Integer.class, memberId);

            if (completedThisWeek == null || completedThisWeek < weeklyGoal) {
                log.debug("GroupGoalScheduler: groupId={} member={} did not hit goal ({}/{})",
                        groupId, memberId, completedThisWeek, weeklyGoal);
                return; // not all members hit goal — skip group
            }
        }

        // 6. ALL mustHit members hit their goal — award ALL members (including exempt)
        log.info("GroupGoalScheduler: groupId={} — all active members hit goal, awarding {} members",
                groupId, allMembers.size());

        for (UUID memberId : allMembers) {
            coinService.awardCoins(memberId, 40, "GROUP_GOAL",
                    "Whole group hit their goals", groupId.toString());
        }
    }

    private int toInt(Integer val) {
        return val != null ? val : 0;
    }
}
