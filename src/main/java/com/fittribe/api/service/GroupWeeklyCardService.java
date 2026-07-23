package com.fittribe.api.service;

import com.fittribe.api.entity.Group;
import com.fittribe.api.entity.GroupMemberGoalSnapshot;
import com.fittribe.api.entity.GroupWeeklyCard;
import com.fittribe.api.entity.GroupWeeklyProgress;
import com.fittribe.api.repository.GroupMemberGoalSnapshotRepository;
import com.fittribe.api.repository.GroupRepository;
import com.fittribe.api.repository.GroupWeeklyCardRepository;
import com.fittribe.api.repository.GroupWeeklyProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class GroupWeeklyCardService {

    private static final Logger log = LoggerFactory.getLogger(GroupWeeklyCardService.class);

    private final GroupWeeklyProgressRepository     progressRepo;
    private final GroupWeeklyCardRepository         cardRepo;
    private final GroupMemberGoalSnapshotRepository snapshotRepo;
    private final GroupRepository                   groupRepo;
    private final CoinService                       coinService;
    private final JdbcTemplate                      jdbc;

    public GroupWeeklyCardService(GroupWeeklyProgressRepository progressRepo,
                                  GroupWeeklyCardRepository cardRepo,
                                  GroupMemberGoalSnapshotRepository snapshotRepo,
                                  GroupRepository groupRepo,
                                  CoinService coinService,
                                  JdbcTemplate jdbc) {
        this.progressRepo = progressRepo;
        this.cardRepo     = cardRepo;
        this.snapshotRepo = snapshotRepo;
        this.groupRepo    = groupRepo;
        this.coinService  = coinService;
        this.jdbc         = jdbc;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sunday 23:59 IST entry point.
     * Iterates all unlocked progress rows for the given ISO week,
     * creates earned cards (BRONZE+), awards coins to contributors,
     * and resets the streak for groups that did not earn a card.
     * Idempotent: calling twice for the same week is safe.
     *
     * @return list of cards created this run (BRONZE+ groups only); empty if none qualified
     */
    @Transactional
    public List<GroupWeeklyCard> lockWeekForAllGroups(int isoYear, int isoWeek) {
        List<GroupWeeklyProgress> unlocked =
                progressRepo.findByIsoYearAndIsoWeekAndLockedAtIsNull(isoYear, isoWeek);

        Set<UUID> groupsWithCard = new HashSet<>();
        List<GroupWeeklyCard> createdCards = new ArrayList<>();

        for (GroupWeeklyProgress progress : unlocked) {
            try {
                GroupWeeklyCard card = lockWeekForGroup(progress, isoYear, isoWeek);
                if (card != null) {
                    groupsWithCard.add(progress.getGroupId());
                    createdCards.add(card);
                }
            } catch (Exception e) {
                log.error("Failed to lock week for group={}", progress.getGroupId(), e);
            }
        }

        // Reset streak for every group that did NOT earn a card this week
        resetStreakForNonEarners(isoYear, isoWeek, groupsWithCard);

        return createdCards;
    }

    /** Returns the last 10 earned cards for a group, most recent first. */
    @Transactional(readOnly = true)
    public List<GroupWeeklyCard> getCardsForGroup(UUID groupId) {
        return cardRepo.findTop10ByGroupIdOrderByLockedAtDesc(groupId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * @return the saved GroupWeeklyCard if one was created, null if the group did not earn a card
     */
    private GroupWeeklyCard lockWeekForGroup(GroupWeeklyProgress progress, int isoYear, int isoWeek) {
        UUID groupId = progress.getGroupId();

        // Idempotency: card already exists — just mark progress locked and return it
        Optional<GroupWeeklyCard> existing = cardRepo.findByGroupIdAndIsoYearAndIsoWeek(groupId, isoYear, isoWeek);
        if (existing.isPresent()) {
            log.debug("Card already exists for group={} week={}-W{}", groupId, isoYear, isoWeek);
            ensureProgressLocked(progress);
            return existing.get();
        }

        String tier = progress.getCurrentTier();

        // No tier earned — mark locked, no card, streak will reset in caller
        if ("NONE".equals(tier)) {
            ensureProgressLocked(progress);
            return null;
        }

        // Compute final percentage
        int target      = progress.getTargetSessions();
        int logged      = progress.getSessionsLogged();
        int finalPct    = target > 0 ? (logged * 100) / target : 0;

        // Compute streak_at_lock from previous week's card
        LocalDate prevMonday  = mondayOfIsoWeek(isoYear, isoWeek).minusWeeks(1);
        int prevYear = prevMonday.get(WeekFields.ISO.weekBasedYear());
        int prevWeek = prevMonday.get(WeekFields.ISO.weekOfWeekBasedYear());
        Optional<GroupWeeklyCard> prevCard = cardRepo.findByGroupIdAndIsoYearAndIsoWeek(groupId, prevYear, prevWeek);
        int streakAtLock = prevCard.map(c -> c.getStreakAtLock() + 1).orElse(1);

        // Determine contributors: active snapshots with at least 1 session
        List<GroupMemberGoalSnapshot> snapshots =
                snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(groupId, isoYear, isoWeek);
        UUID[] contributorIds = snapshots.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()) && s.getSessionsContributed() >= 1)
                .map(GroupMemberGoalSnapshot::getUserId)
                .toArray(UUID[]::new);

        // Create the card
        GroupWeeklyCard card = new GroupWeeklyCard();
        card.setGroupId(groupId);
        card.setIsoYear(isoYear);
        card.setIsoWeek(isoWeek);
        card.setFinalTier(tier);
        card.setSessionsLogged(logged);
        card.setTargetSessions(target);
        card.setFinalPercentage(finalPct);
        card.setOverachiever(progress.isOverachiever());
        card.setStreakAtLock(streakAtLock);
        card.setContributorUserIds(contributorIds);
        cardRepo.save(card);

        // Update group streak
        groupRepo.findById(groupId).ifPresent(g -> {
            g.setStreak(streakAtLock);
            groupRepo.save(g);
        });

        ensureProgressLocked(progress);

        // Award coins to contributors (idempotent via CoinService referenceId)
        int coins = coinsForTier(tier);
        for (UUID contributorId : contributorIds) {
            String refId = "group:" + groupId + ":week:" + isoYear + "-" + isoWeek + ":user:" + contributorId;
            String label = capitalize(tier) + " tier group card — W" + isoYear + "-" + isoWeek;
            try {
                coinService.awardCoins(contributorId, coins, "GROUP_TIER_EARNED", label, refId);
            } catch (Exception e) {
                log.error("Failed to award coins for group={} user={} tier={}", groupId, contributorId, tier, e);
            }
        }

        log.info("Locked group={} week={}-W{} tier={} streak={} contributors={}",
                groupId, isoYear, isoWeek, tier, streakAtLock, contributorIds.length);
        return card;
    }

    private void resetStreakForNonEarners(int isoYear, int isoWeek, Set<UUID> groupsWithCard) {
        List<UUID> allGroupIds = jdbc.queryForList("SELECT id FROM \"groups\"", UUID.class);
        for (UUID gId : allGroupIds) {
            if (groupsWithCard.contains(gId)) continue;
            try {
                jdbc.update("UPDATE \"groups\" SET streak = 0 WHERE id = ?", gId);
            } catch (Exception e) {
                log.error("Failed to reset streak for group={}", gId, e);
            }
        }
    }

    private void ensureProgressLocked(GroupWeeklyProgress progress) {
        if (progress.getLockedAt() == null) {
            progress.setLockedAt(Instant.now());
            progress.setUpdatedAt(Instant.now());
            progressRepo.save(progress);
        }
    }

    private static int coinsForTier(String tier) {
        return switch (tier) {
            case "GOLD"   -> 100;
            case "SILVER" -> 50;
            default       -> 25; // BRONZE
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    /**
     * Returns the Monday (day 1) of the given ISO year+week.
     * Jan 4th is always in ISO week 1 of its year, so we use that as an anchor.
     */
    static LocalDate mondayOfIsoWeek(int isoYear, int isoWeek) {
        LocalDate jan4      = LocalDate.of(isoYear, 1, 4);
        LocalDate week1Mon  = jan4.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return week1Mon.plusWeeks(isoWeek - 1);
    }
}
