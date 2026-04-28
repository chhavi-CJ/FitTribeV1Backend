package com.fittribe.api.service;

import com.fittribe.api.entity.GroupWeeklyTopPerformer;
import com.fittribe.api.entity.UserWeeklyStats;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.GroupRepository;
import com.fittribe.api.repository.GroupWeeklyTopPerformerRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.UserWeeklyStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TopPerformerService {

    private static final Logger log = LoggerFactory.getLogger(TopPerformerService.class);
    private static final String EFFORT = "EFFORT";

    private final GroupMemberRepository             memberRepo;
    private final GroupRepository                   groupRepo;
    private final UserWeeklyStatsRepository         statsRepo;
    private final GroupWeeklyTopPerformerRepository topPerformerRepo;
    private final EffortScoreCalculator             effortScoreCalculator;
    private final UserRepository                    userRepo;

    public TopPerformerService(GroupMemberRepository memberRepo,
                               GroupRepository groupRepo,
                               UserWeeklyStatsRepository statsRepo,
                               GroupWeeklyTopPerformerRepository topPerformerRepo,
                               EffortScoreCalculator effortScoreCalculator,
                               UserRepository userRepo) {
        this.memberRepo           = memberRepo;
        this.groupRepo            = groupRepo;
        this.statsRepo            = statsRepo;
        this.topPerformerRepo     = topPerformerRepo;
        this.effortScoreCalculator = effortScoreCalculator;
        this.userRepo             = userRepo;
    }

    /**
     * Iterates all groups and computes the top performer for each.
     * Called by SundayGroupCardLockJob immediately after card locking completes.
     */
    public void computeForAllGroups(int isoYear, int isoWeek) {
        List<UUID> groupIds = groupRepo.findAll().stream()
                .map(g -> g.getId())
                .collect(java.util.stream.Collectors.toList());

        log.info("TopPerformerService: computing for {} groups, week={}-W{}", groupIds.size(), isoYear, isoWeek);
        for (UUID groupId : groupIds) {
            try {
                computeForGroup(groupId, isoYear, isoWeek);
            } catch (Exception e) {
                log.error("TopPerformerService: failed for group={} week={}-W{}", groupId, isoYear, isoWeek, e);
            }
        }
    }

    /**
     * Determines and persists the EFFORT-dimension top performer for a single group and week.
     * Idempotent: if a row already exists for this (group, year, week, EFFORT), does nothing.
     */
    @Transactional
    public void computeForGroup(UUID groupId, int isoYear, int isoWeek) {
        // Idempotency guard
        if (topPerformerRepo.findByGroupIdAndIsoYearAndIsoWeekAndDimension(groupId, isoYear, isoWeek, EFFORT).isPresent()) {
            log.debug("TopPerformer already exists for group={} week={}-W{}", groupId, isoYear, isoWeek);
            return;
        }

        LocalDate weekStart = mondayOfIsoWeek(isoYear, isoWeek);

        // Score every active member
        List<UUID> memberIds = memberRepo.findByGroupId(groupId).stream()
                .map(m -> m.getUserId())
                .collect(java.util.stream.Collectors.toList());

        Map<UUID, Integer> scores = new LinkedHashMap<>();
        for (UUID userId : memberIds) {
            int score = effortScoreCalculator.calculateEffortScore(userId, weekStart);
            if (score > 0) scores.put(userId, score);
        }

        if (scores.isEmpty()) {
            log.info("TopPerformerService: no qualifying members for group={} week={}-W{}", groupId, isoYear, isoWeek);
            return;
        }

        // Sort descending by score
        List<UUID> ranked = new ArrayList<>(scores.keySet());
        ranked.sort(Comparator.comparingInt(scores::get).reversed());

        // Apply rotation rule: skip if candidate won EFFORT in BOTH last 2 weeks for this group
        UUID winner     = null;
        int  winnerScore = 0;
        for (UUID candidate : ranked) {
            if (!isRotationBlocked(groupId, candidate, isoYear, isoWeek)) {
                winner      = candidate;
                winnerScore = scores.get(candidate);
                break;
            }
        }

        if (winner == null) {
            log.info("TopPerformerService: all candidates rotation-blocked for group={} week={}-W{}",
                    groupId, isoYear, isoWeek);
            return;
        }

        // Build metric label
        UserWeeklyStats ws = statsRepo.findByUserIdAndWeekStartDate(winner, weekStart).orElse(null);
        String metricLabel = buildMetricLabel(winnerScore, ws);

        GroupWeeklyTopPerformer tp = new GroupWeeklyTopPerformer();
        tp.setGroupId(groupId);
        tp.setIsoYear(isoYear);
        tp.setIsoWeek(isoWeek);
        tp.setWinnerUserId(winner);
        tp.setDimension(EFFORT);
        tp.setScoreValue(winnerScore);
        tp.setMetricLabel(metricLabel);
        topPerformerRepo.save(tp);

        String winnerName = userRepo.findById(winner).map(u -> u.getDisplayName()).orElse("?");
        log.info("TopPerformerService: group={} week={}-W{} winner={} ({}) score={}",
                groupId, isoYear, isoWeek, winnerName, winner, winnerScore);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns true if the candidate won EFFORT in BOTH of the two weeks immediately
     * preceding isoYear/isoWeek for this group — triggering the fairness rotation.
     */
    private boolean isRotationBlocked(UUID groupId, UUID userId, int isoYear, int isoWeek) {
        List<GroupWeeklyTopPerformer> recentWins = topPerformerRepo
                .findByWinnerUserIdAndGroupIdAndDimensionOrderByIsoYearDescIsoWeekDesc(userId, groupId, EFFORT);

        if (recentWins.size() < 2) return false;

        LocalDate thisMonday     = mondayOfIsoWeek(isoYear, isoWeek);
        LocalDate prevMonday     = thisMonday.minusWeeks(1);
        LocalDate prevPrevMonday = thisMonday.minusWeeks(2);

        boolean wonLastWeek     = recentWins.stream().anyMatch(w -> mondayOfIsoWeek(w.getIsoYear(), w.getIsoWeek()).equals(prevMonday));
        boolean wonTwoWeeksAgo  = recentWins.stream().anyMatch(w -> mondayOfIsoWeek(w.getIsoYear(), w.getIsoWeek()).equals(prevPrevMonday));

        return wonLastWeek && wonTwoWeeksAgo;
    }

    private String buildMetricLabel(int score, UserWeeklyStats ws) {
        if (ws == null) return "Effort score " + score;
        StringBuilder sb = new StringBuilder("Effort score ").append(score);
        sb.append(" · ").append(ws.getSessionsCount()).append(ws.getSessionsCount() == 1 ? " session" : " sessions");
        if (ws.getPrsHit() > 0) {
            sb.append(" · ").append(ws.getPrsHit()).append(ws.getPrsHit() == 1 ? " PR" : " PRs");
        }
        return sb.toString();
    }

    public static LocalDate mondayOfIsoWeek(int isoYear, int isoWeek) {
        LocalDate jan4     = LocalDate.of(isoYear, 1, 4);
        LocalDate week1Mon = jan4.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return week1Mon.plusWeeks(isoWeek - 1);
    }
}
