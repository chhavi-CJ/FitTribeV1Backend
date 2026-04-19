package com.fittribe.api.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class AccountPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeScheduler.class);

    private final JdbcTemplate jdbcTemplate;

    public AccountPurgeScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void purgeDeletedAccounts() {
        List<UUID> userIds = jdbcTemplate.queryForList(
                "SELECT id FROM users " +
                "WHERE deletion_requested_at IS NOT NULL " +
                "AND deletion_requested_at <= NOW() - INTERVAL '30 days'",
                UUID.class);

        if (userIds.isEmpty()) {
            log.info("AccountPurgeScheduler: no accounts eligible for purge");
            return;
        }

        log.info("AccountPurgeScheduler: purging {} account(s)", userIds.size());

        for (UUID userId : userIds) {
            try {
                purgeUser(userId);
            } catch (Exception e) {
                log.error("AccountPurgeScheduler: failed to purge userId={}", userId, e);
            }
        }
    }

    private void purgeUser(UUID userId) {
        int ct  = jdbcTemplate.update("DELETE FROM coin_transactions WHERE user_id = ?", userId);
        int ws  = jdbcTemplate.update("DELETE FROM workout_sessions  WHERE user_id = ?", userId);
        int gm  = jdbcTemplate.update("DELETE FROM group_members     WHERE user_id = ?", userId);

        // user_session_feedback may not exist in all environments — skip gracefully
        int sf = 0;
        try {
            sf = jdbcTemplate.update("DELETE FROM user_session_feedback WHERE user_id = ?", userId);
        } catch (Exception e) {
            log.debug("AccountPurgeScheduler: user_session_feedback skip for userId={} ({})",
                    userId, e.getMessage());
        }

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

        log.info("AccountPurgeScheduler: purged userId={} " +
                 "[coin_transactions={}, workout_sessions={}, " +
                 "group_members={}, session_feedback={}]",
                userId, ct, ws, gm, sf);
    }
}
