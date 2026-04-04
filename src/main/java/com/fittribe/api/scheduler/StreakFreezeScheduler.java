package com.fittribe.api.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Component
public class StreakFreezeScheduler {

    private static final Logger log = LoggerFactory.getLogger(StreakFreezeScheduler.class);

    private final JdbcTemplate jdbcTemplate;

    public StreakFreezeScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Runs at 00:01 IST every night.
     * For users who missed yesterday but still have streak freeze charges,
     * consumes one freeze and preserves their streak.
     * Does NOT reset the streak — that is the point of the freeze.
     */
    @Scheduled(cron = "0 1 0 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void applyStreakFreezes() {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1);

        // Find users who:
        //   1. Have an active streak (streak > 0)
        //   2. Have at least one freeze in the bank
        //   3. Did NOT complete a workout yesterday
        //   4. Did NOT set a REST/BUSY/SICK day status for yesterday
        List<UUID> eligible = jdbcTemplate.queryForList(
                "SELECT u.id FROM users u " +
                "WHERE u.streak > 0 " +
                "  AND u.streak_freeze_balance > 0 " +
                "  AND NOT EXISTS (" +
                "    SELECT 1 FROM workout_sessions ws " +
                "    WHERE ws.user_id = u.id " +
                "      AND ws.status = 'COMPLETED' " +
                "      AND ws.finished_at::date = ?" +
                "  ) " +
                "  AND NOT EXISTS (" +
                "    SELECT 1 FROM user_day_status uds " +
                "    WHERE uds.user_id = u.id " +
                "      AND uds.status_date = ? " +
                "      AND uds.status IN ('REST', 'SICK', 'BUSY')" +
                "  )",
                UUID.class,
                yesterday, yesterday);

        if (eligible.isEmpty()) {
            log.info("StreakFreezeScheduler: no eligible users for {}", yesterday);
            return;
        }

        log.info("StreakFreezeScheduler: applying freeze for {} user(s) who missed {}", eligible.size(), yesterday);

        for (UUID userId : eligible) {
            try {
                jdbcTemplate.update(
                        "UPDATE users SET streak_freeze_balance = streak_freeze_balance - 1 WHERE id = ?",
                        userId);

                jdbcTemplate.update(
                        "INSERT INTO coin_transactions " +
                        "(id, user_id, amount, direction, label, type, reference_id, created_at) " +
                        "VALUES (gen_random_uuid(), ?, 0, 'DEBIT', 'Streak freeze used', 'STREAK_FREEZE_USED', NULL, NOW())",
                        userId);

                log.info("StreakFreezeScheduler: freeze applied for userId={}", userId);
            } catch (Exception e) {
                log.error("StreakFreezeScheduler: failed for userId={}", userId, e);
            }
        }
    }
}
