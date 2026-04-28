package com.fittribe.api.service;

import com.fittribe.api.dto.group.GroupCarouselDto;
import com.fittribe.api.dto.group.GroupWeeklyProgressDto;
import com.fittribe.api.dto.group.MemberProgressDto;
import com.fittribe.api.entity.FeedItem;
import com.fittribe.api.entity.Group;
import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.entity.GroupMemberGoalSnapshot;
import com.fittribe.api.entity.GroupWeeklyCard;
import com.fittribe.api.entity.GroupWeeklyProgress;
import com.fittribe.api.entity.User;
import com.fittribe.api.repository.FeedItemRepository;
import com.fittribe.api.repository.GroupMemberGoalSnapshotRepository;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.GroupRepository;
import com.fittribe.api.repository.GroupWeeklyCardRepository;
import com.fittribe.api.repository.GroupWeeklyProgressRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.util.Zones;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GroupProgressService {

    private static final Logger log = LoggerFactory.getLogger(GroupProgressService.class);

    private final GroupMemberRepository              memberRepo;
    private final GroupWeeklyProgressRepository      progressRepo;
    private final GroupMemberGoalSnapshotRepository  snapshotRepo;
    private final GroupRepository                    groupRepo;
    private final UserRepository                     userRepo;
    private final FeedItemRepository                 feedItemRepo;
    private final GroupWeeklyCardRepository          groupWeeklyCardRepo;
    private final JdbcTemplate                       jdbc;

    public GroupProgressService(GroupMemberRepository memberRepo,
                                GroupWeeklyProgressRepository progressRepo,
                                GroupMemberGoalSnapshotRepository snapshotRepo,
                                GroupRepository groupRepo,
                                UserRepository userRepo,
                                FeedItemRepository feedItemRepo,
                                GroupWeeklyCardRepository groupWeeklyCardRepo,
                                JdbcTemplate jdbc) {
        this.memberRepo          = memberRepo;
        this.progressRepo        = progressRepo;
        this.snapshotRepo        = snapshotRepo;
        this.groupRepo           = groupRepo;
        this.userRepo            = userRepo;
        this.feedItemRepo        = feedItemRepo;
        this.groupWeeklyCardRepo = groupWeeklyCardRepo;
        this.jdbc                = jdbc;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called after every successful session finish.
     * Increments the user's contribution in every group they belong to and
     * recomputes tier for each group. Each group is wrapped in its own
     * try/catch so a failure in one group never blocks the others.
     * The CALLER must wrap this entire call in try/catch — failure here
     * must never block the session-finish 200 response.
     */
    @Transactional
    public void recordSessionForUser(UUID userId, LocalDate sessionDate) {
        int isoYear = sessionDate.get(WeekFields.ISO.weekBasedYear());
        int isoWeek = sessionDate.get(WeekFields.ISO.weekOfWeekBasedYear());

        List<GroupMember> memberships = memberRepo.findByUserId(userId);

        for (GroupMember gm : memberships) {
            UUID groupId = gm.getGroupId();
            try {
                incrementForGroup(userId, groupId, isoYear, isoWeek);
            } catch (Exception e) {
                log.error("Group progress increment failed for group={} user={}", groupId, userId, e);
            }
        }
    }

    /**
     * Called when a user joins a group mid-week.
     * Creates a snapshot with weekly_goal=0 so the joiner's goal is not
     * added to this week's target (which was already set).
     */
    @Transactional
    public void onMemberJoinedGroup(UUID groupId, UUID userId) {
        int isoYear = currentIsoYear();
        int isoWeek = currentIsoWeek();

        if (snapshotRepo.findByGroupIdAndUserIdAndIsoYearAndIsoWeek(groupId, userId, isoYear, isoWeek).isEmpty()) {
            GroupMemberGoalSnapshot s = new GroupMemberGoalSnapshot();
            s.setGroupId(groupId);
            s.setUserId(userId);
            s.setIsoYear(isoYear);
            s.setIsoWeek(isoWeek);
            s.setWeeklyGoal(0);   // joiner does not add to target
            s.setSessionsContributed(0);
            s.setIsActive(true);
            s.setJoinedThisWeek(true);
            s.setLeftThisWeek(false);
            snapshotRepo.save(s);
        }
        // DO NOT recalculate target — it was locked when the week's first session was recorded
    }

    /**
     * Called when a user leaves a group (or is removed).
     * Marks their snapshot inactive and recomputes the group's sessions_logged
     * and tier for the current week (may downgrade — that is the rule).
     */
    @Transactional
    public void onMemberLeftGroup(UUID groupId, UUID userId) {
        int isoYear = currentIsoYear();
        int isoWeek = currentIsoWeek();

        Optional<GroupMemberGoalSnapshot> snapshotOpt =
                snapshotRepo.findByGroupIdAndUserIdAndIsoYearAndIsoWeek(groupId, userId, isoYear, isoWeek);
        if (snapshotOpt.isEmpty()) return;

        GroupMemberGoalSnapshot snapshot = snapshotOpt.get();
        snapshot.setIsActive(false);
        snapshot.setLeftThisWeek(true);
        snapshot.setUpdatedAt(Instant.now());
        snapshotRepo.save(snapshot);

        Optional<GroupWeeklyProgress> progressOpt =
                progressRepo.findByGroupIdAndIsoYearAndIsoWeek(groupId, isoYear, isoWeek);
        if (progressOpt.isEmpty()) return;

        GroupWeeklyProgress progress = progressOpt.get();

        // Subtract the leaving member's weekly_goal from the target
        int newTarget = Math.max(0, progress.getTargetSessions() - snapshot.getWeeklyGoal());
        progress.setTargetSessions(newTarget);

        // Recompute sessions_logged from active snapshots only
        List<GroupMemberGoalSnapshot> allSnapshots =
                snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(groupId, isoYear, isoWeek);
        int newLogged = allSnapshots.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .mapToInt(GroupMemberGoalSnapshot::getSessionsContributed)
                .sum();
        progress.setSessionsLogged(newLogged);

        recomputeTier(progress);
        progress.setUpdatedAt(Instant.now());
        progressRepo.save(progress);
    }

    /** Returns the current week's progress DTO for a single group. */
    @Transactional(readOnly = true)
    public GroupWeeklyProgressDto getProgressForGroup(UUID groupId, UUID requestingUserId) {
        int isoYear = currentIsoYear();
        int isoWeek = currentIsoWeek();

        GroupWeeklyProgress progress = progressRepo
                .findByGroupIdAndIsoYearAndIsoWeek(groupId, isoYear, isoWeek)
                .orElse(emptyProgress(groupId, isoYear, isoWeek));

        List<GroupMemberGoalSnapshot> snapshots =
                snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(groupId, isoYear, isoWeek);

        List<MemberProgressDto> breakdown = new ArrayList<>();
        MemberProgressDto myStatus = null;

        for (GroupMemberGoalSnapshot s : snapshots) {
            String name = userRepo.findById(s.getUserId())
                    .map(User::getDisplayName).orElse(null);
            MemberProgressDto dto = new MemberProgressDto(
                    s.getUserId(), name, s.getWeeklyGoal(),
                    s.getSessionsContributed(),
                    Boolean.TRUE.equals(s.getIsActive()),
                    Boolean.TRUE.equals(s.getJoinedThisWeek()),
                    Boolean.TRUE.equals(s.getLeftThisWeek()));
            breakdown.add(dto);
            if (s.getUserId().equals(requestingUserId)) myStatus = dto;
        }

        int pct = progress.getTargetSessions() > 0
                ? (progress.getSessionsLogged() * 100) / progress.getTargetSessions()
                : 0;

        List<GroupWeeklyCard> cards = groupWeeklyCardRepo.findByGroupIdOrderByIsoYearDescIsoWeekDesc(groupId);
        int historicalGoldStreak = 0;
        for (GroupWeeklyCard card : cards) {
            if ("GOLD".equals(card.getFinalTier())) {
                historicalGoldStreak++;
            } else {
                break;
            }
        }
        int goldStreak = "GOLD".equals(progress.getCurrentTier())
                ? historicalGoldStreak + 1
                : historicalGoldStreak;

        GroupWeeklyProgressDto dto = new GroupWeeklyProgressDto(
                groupId, isoYear, isoWeek,
                progress.getTargetSessions(), progress.getSessionsLogged(),
                progress.getCurrentTier(), pct, progress.isOverachiever(),
                progress.getLockedAt(), breakdown, myStatus);
        dto.setGoldStreakIncludingThisWeek(goldStreak);
        return dto;
    }

    /** Returns one carousel entry per group the user belongs to. */
    @Transactional(readOnly = true)
    public List<GroupCarouselDto> getMyAllGroupsProgress(UUID userId) {
        int isoYear = currentIsoYear();
        int isoWeek = currentIsoWeek();

        List<GroupMember> memberships = memberRepo.findByUserId(userId);
        List<GroupCarouselDto> result = new ArrayList<>();

        for (GroupMember gm : memberships) {
            UUID groupId = gm.getGroupId();
            Group group = groupRepo.findById(groupId).orElse(null);
            if (group == null) continue;

            GroupWeeklyProgress progress = progressRepo
                    .findByGroupIdAndIsoYearAndIsoWeek(groupId, isoYear, isoWeek)
                    .orElse(emptyProgress(groupId, isoYear, isoWeek));

            int myContributed = snapshotRepo
                    .findByGroupIdAndUserIdAndIsoYearAndIsoWeek(groupId, userId, isoYear, isoWeek)
                    .map(GroupMemberGoalSnapshot::getSessionsContributed)
                    .orElse(0);

            int pct = progress.getTargetSessions() > 0
                    ? (progress.getSessionsLogged() * 100) / progress.getTargetSessions()
                    : 0;

            result.add(new GroupCarouselDto(
                    groupId, group.getName(), group.getIcon(),
                    isoYear, isoWeek,
                    progress.getCurrentTier(),
                    progress.getSessionsLogged(),
                    progress.getTargetSessions(),
                    pct, myContributed));
        }
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void incrementForGroup(UUID userId, UUID groupId, int isoYear, int isoWeek) {
        // 1. Get-or-create the user's snapshot for this week
        GroupMemberGoalSnapshot snapshot = snapshotRepo
                .findByGroupIdAndUserIdAndIsoYearAndIsoWeek(groupId, userId, isoYear, isoWeek)
                .orElseGet(() -> createSnapshot(groupId, userId, isoYear, isoWeek));

        snapshot.setSessionsContributed(snapshot.getSessionsContributed() + 1);
        snapshot.setUpdatedAt(Instant.now());
        snapshotRepo.save(snapshot);

        // 2. Get-or-create group progress for this week
        GroupWeeklyProgress progress = progressRepo
                .findByGroupIdAndIsoYearAndIsoWeek(groupId, isoYear, isoWeek)
                .orElseGet(() -> createProgress(groupId, isoYear, isoWeek));

        // 3. Recompute sessions_logged from all active snapshots
        List<GroupMemberGoalSnapshot> allSnapshots =
                snapshotRepo.findByGroupIdAndIsoYearAndIsoWeek(groupId, isoYear, isoWeek);
        int sessionsLogged = allSnapshots.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .mapToInt(GroupMemberGoalSnapshot::getSessionsContributed)
                .sum();
        progress.setSessionsLogged(sessionsLogged);

        // 4. Recompute tier and detect upgrade
        String previousTier = progress.getCurrentTier();
        recomputeTier(progress);
        String newTier = progress.getCurrentTier();

        boolean upgraded = tierRank(newTier) > tierRank(previousTier);
        if (upgraded) {
            writeTierUpgradeFeedEvent(groupId, userId, previousTier, newTier, isoYear, isoWeek);
        }

        progress.setUpdatedAt(Instant.now());
        progressRepo.save(progress);
    }

    private GroupMemberGoalSnapshot createSnapshot(UUID groupId, UUID userId, int isoYear, int isoWeek) {
        int weeklyGoal = userRepo.findById(userId)
                .map(u -> u.getWeeklyGoal() != null ? u.getWeeklyGoal() : 4)
                .orElse(4);

        GroupMemberGoalSnapshot s = new GroupMemberGoalSnapshot();
        s.setGroupId(groupId);
        s.setUserId(userId);
        s.setIsoYear(isoYear);
        s.setIsoWeek(isoWeek);
        s.setWeeklyGoal(weeklyGoal);
        s.setSessionsContributed(0);
        s.setIsActive(true);
        s.setJoinedThisWeek(false);
        s.setLeftThisWeek(false);
        return snapshotRepo.save(s);
    }

    private GroupWeeklyProgress createProgress(UUID groupId, int isoYear, int isoWeek) {
        // Target = SUM of weekly_goal across all current group members
        Integer target = jdbc.queryForObject(
                "SELECT COALESCE(SUM(COALESCE(u.weekly_goal, 4)), 0) " +
                "FROM group_members gm JOIN users u ON u.id = gm.user_id " +
                "WHERE gm.group_id = ?",
                Integer.class, groupId);
        if (target == null) target = 0;

        GroupWeeklyProgress p = new GroupWeeklyProgress();
        p.setGroupId(groupId);
        p.setIsoYear(isoYear);
        p.setIsoWeek(isoWeek);
        p.setTargetSessions(target);
        p.setSessionsLogged(0);
        p.setCurrentTier("NONE");
        p.setOverachiever(false);
        return progressRepo.save(p);
    }

    private void recomputeTier(GroupWeeklyProgress progress) {
        int target = progress.getTargetSessions();
        int logged = progress.getSessionsLogged();
        int pct    = target > 0 ? (logged * 100) / target : 0;

        String tier;
        if      (pct >= 100) tier = "GOLD";
        else if (pct >= 85)  tier = "SILVER";
        else if (pct >= 70)  tier = "BRONZE";
        else                 tier = "NONE";

        progress.setCurrentTier(tier);
        progress.setOverachiever(pct > 100 && "GOLD".equals(tier));
    }

    private void writeTierUpgradeFeedEvent(UUID groupId, UUID userId,
                                           String oldTier, String newTier,
                                           int isoYear, int isoWeek) {
        try {
            String body = String.format(
                    "{\"oldTier\":\"%s\",\"newTier\":\"%s\",\"isoYear\":%d,\"isoWeek\":%d}",
                    oldTier, newTier, isoYear, isoWeek);
            FeedItem fi = new FeedItem();
            fi.setGroupId(groupId);
            fi.setUserId(userId);
            fi.setType("CARD_TIER_UPGRADED");
            fi.setBody(body);
            feedItemRepo.save(fi);
        } catch (Exception e) {
            log.error("Failed to write tier upgrade feed event for group={}", groupId, e);
        }
    }

    private static int tierRank(String tier) {
        return switch (tier) {
            case "GOLD"   -> 3;
            case "SILVER" -> 2;
            case "BRONZE" -> 1;
            default       -> 0;
        };
    }

    private static GroupWeeklyProgress emptyProgress(UUID groupId, int isoYear, int isoWeek) {
        GroupWeeklyProgress p = new GroupWeeklyProgress();
        p.setGroupId(groupId);
        p.setIsoYear(isoYear);
        p.setIsoWeek(isoWeek);
        p.setTargetSessions(0);
        p.setSessionsLogged(0);
        p.setCurrentTier("NONE");
        return p;
    }

    private static int currentIsoYear() {
        return LocalDate.now(Zones.APP_ZONE).get(WeekFields.ISO.weekBasedYear());
    }

    private static int currentIsoWeek() {
        return LocalDate.now(Zones.APP_ZONE).get(WeekFields.ISO.weekOfWeekBasedYear());
    }
}
