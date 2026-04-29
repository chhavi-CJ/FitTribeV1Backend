package com.fittribe.api.scheduler;

import com.fittribe.api.entity.GroupWeeklyCard;
import com.fittribe.api.service.FeedEventWriter;
import com.fittribe.api.service.GroupWeeklyCardService;
import com.fittribe.api.service.TopPerformerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;

import com.fittribe.api.util.Zones;

/**
 * Runs Sunday 23:59 IST — after WeeklyStatsComputeJob (23:58 IST).
 * Locks the current ISO week for all groups: creates earned cards (BRONZE+),
 * awards GROUP_TIER_EARNED coins to contributors, resets streak for groups
 * that missed, then computes the EFFORT-dimension top performer for each group.
 */
@Component
public class SundayGroupCardLockJob {

    private static final Logger log = LoggerFactory.getLogger(SundayGroupCardLockJob.class);

    private final GroupWeeklyCardService cardService;
    private final TopPerformerService    topPerformerService;
    private final FeedEventWriter        feedEventWriter;

    public SundayGroupCardLockJob(GroupWeeklyCardService cardService,
                                  TopPerformerService topPerformerService,
                                  FeedEventWriter feedEventWriter) {
        this.cardService         = cardService;
        this.topPerformerService = topPerformerService;
        this.feedEventWriter     = feedEventWriter;
    }

    @Scheduled(cron = "0 59 23 * * SUN", zone = "Asia/Kolkata")
    public void lockCurrentWeek() {
        LocalDate now   = LocalDate.now(Zones.APP_ZONE);
        int isoYear     = now.get(WeekFields.ISO.weekBasedYear());
        int isoWeek     = now.get(WeekFields.ISO.weekOfWeekBasedYear());

        log.info("SundayGroupCardLockJob: starting for week={}-W{}", isoYear, isoWeek);

        List<GroupWeeklyCard> createdCards = List.of();
        try {
            createdCards = cardService.lockWeekForAllGroups(isoYear, isoWeek);
            log.info("SundayGroupCardLockJob: cards created={}", createdCards.size());
        } catch (Exception e) {
            log.error("SundayGroupCardLockJob: card lock failed for week={}-W{}", isoYear, isoWeek, e);
        }

        for (GroupWeeklyCard card : createdCards) {
            try {
                feedEventWriter.writeTierLocked(card);
            } catch (Exception e) {
                log.warn("SundayGroupCardLockJob: TIER_LOCKED feed failed for group={}: {}",
                        card.getGroupId(), e.getMessage());
            }
        }

        try {
            topPerformerService.computeForAllGroups(isoYear, isoWeek);
            log.info("SundayGroupCardLockJob: top performer compute complete for week={}-W{}", isoYear, isoWeek);
        } catch (Exception e) {
            log.error("SundayGroupCardLockJob: top performer compute failed for week={}-W{}", isoYear, isoWeek, e);
        }
    }
}
