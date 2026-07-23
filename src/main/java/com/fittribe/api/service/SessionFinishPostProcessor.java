package com.fittribe.api.service;

import com.fittribe.api.entity.Exercise;
import com.fittribe.api.entity.Group;
import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.prv2.detector.LoggedSet;
import com.fittribe.api.prv2.service.PrWritePathService;
import com.fittribe.api.repository.ExerciseRepository;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.GroupRepository;
import com.fittribe.api.repository.NotificationRepository;
import com.fittribe.api.repository.PrEventRepository;
import com.fittribe.api.repository.SetLogRepository;
import com.fittribe.api.repository.UserDayStatusRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.strengthscore.ProgressSnapshotService;
import com.fittribe.api.util.Zones;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Runs the post-finish side-effect pipeline for a completed workout
 * session OFF the HTTP thread. SessionController.finishSession returns
 * the response after the hard-sync work (core save + streak); everything
 * else moves here.
 *
 * <p>Single ordered pipeline (NOT 11 independent async calls) so that
 * dependent steps run in the right order:
 * <ul>
 *   <li>streakSnapshot must run before workoutFinishedFeed
 *       (FeedEventWriter.writeWorkoutFinished reads session.getStreak())</li>
 *   <li>baseCoinAwards must run before streakMilestoneFeed
 *       (the feed event references the just-awarded coin)</li>
 *   <li>setLogCleanup runs LAST — PrWritePathService uses the loggedSets
 *       list passed in via context, never re-reads set_logs</li>
 * </ul>
 *
 * <p>Each step is wrapped in {@link #tryStep} so a single failure does
 * not block subsequent steps. Failures are logged at ERROR; the
 * pipeline continues regardless.
 *
 * <p>Runs on the {@code prDetectionExecutor} pool (see {@link
 * com.fittribe.api.config.AsyncConfig}). Must be called via the bean
 * reference (not {@code this.}) so Spring's @Async proxy applies —
 * SessionController is a different bean, so the call site there is
 * correct.
 */
@Service
public class SessionFinishPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SessionFinishPostProcessor.class);

    // Constants moved from SessionController on the all-blocks migration.
    // SessionController still references COINS_PER_SESSION there for the
    // response build; we duplicate it here for the LOG_WORKOUT award.
    private static final int          COINS_PER_SESSION            = 10;
    private static final Set<Integer> STREAK_MILESTONES             = Set.of(5, 10, 30, 50, 100, 365);
    private static final int          STREAK_MILESTONE_COINS_FLAT    = 10;

    // Dependencies
    private final UserDayStatusRepository  dayStatusRepo;
    private final WorkoutSessionRepository sessionRepo;
    private final UserRepository           userRepo;
    private final ProgressSnapshotService  progressSnapshotService;
    private final RankService              rankService;
    private final CoinService              coinService;
    private final PrWritePathService       prWritePathService;
    private final PrEventRepository        prEventRepo;
    private final FeedEventWriter          feedEventWriter;
    private final GroupProgressService     groupProgressService;
    private final SetLogRepository         setLogRepo;
    private final ExerciseRepository       exerciseRepo;
    private final PlanService              planService;
    private final BonusFreezeGrantService  bonusFreezeGrantService;
    private final NotificationService      notificationService;
    private final NotificationRepository   notificationRepo;
    private final GroupMemberRepository    groupMemberRepo;
    private final GroupRepository          groupRepo;

    public SessionFinishPostProcessor(
            UserDayStatusRepository dayStatusRepo,
            WorkoutSessionRepository sessionRepo,
            UserRepository userRepo,
            ProgressSnapshotService progressSnapshotService,
            RankService rankService,
            CoinService coinService,
            PrWritePathService prWritePathService,
            PrEventRepository prEventRepo,
            FeedEventWriter feedEventWriter,
            GroupProgressService groupProgressService,
            SetLogRepository setLogRepo,
            ExerciseRepository exerciseRepo,
            PlanService planService,
            BonusFreezeGrantService bonusFreezeGrantService,
            NotificationService notificationService,
            NotificationRepository notificationRepo,
            GroupMemberRepository groupMemberRepo,
            GroupRepository groupRepo) {
        this.dayStatusRepo           = dayStatusRepo;
        this.sessionRepo             = sessionRepo;
        this.userRepo                = userRepo;
        this.progressSnapshotService = progressSnapshotService;
        this.rankService             = rankService;
        this.coinService             = coinService;
        this.prWritePathService      = prWritePathService;
        this.prEventRepo             = prEventRepo;
        this.feedEventWriter         = feedEventWriter;
        this.groupProgressService    = groupProgressService;
        this.setLogRepo              = setLogRepo;
        this.exerciseRepo            = exerciseRepo;
        this.planService             = planService;
        this.bonusFreezeGrantService = bonusFreezeGrantService;
        this.notificationService     = notificationService;
        this.notificationRepo        = notificationRepo;
        this.groupMemberRepo         = groupMemberRepo;
        this.groupRepo               = groupRepo;
    }

    /**
     * Run the full post-finish pipeline. Fire-and-forget — caller
     * (SessionController) does not await completion. Returns void.
     */
    @Async("prDetectionExecutor")
    public void runPostFinishPipeline(SessionFinishContext ctx) {
        long t0 = System.nanoTime();
        log.info("postFinish START sessionId={} thread={}",
                ctx.sessionId(), Thread.currentThread().getName());

        tryStep("clearTodayStatus",       () -> clearTodayStatus(ctx));
        tryStep("streakSnapshot",         () -> writeStreakSnapshot(ctx));
        tryStep("strengthSnapshot",       () -> computeStrengthSnapshot(ctx));
        tryStep("rankPromotion",          () -> checkRankPromotion(ctx));
        tryStep("baseCoinAwards",         () -> awardBaseCoins(ctx));
        tryStep("streakMilestoneFeed",    () -> writeStreakMilestoneFeed(ctx));
        tryStep("streakMilestoneNotify",  () -> notifyStreakMilestone(ctx));
        tryStep("prDetection",            () -> runPrDetection(ctx));
        tryStep("workoutFinishedFeed",    () -> writeWorkoutFinishedFeed(ctx));
        tryStep("groupProgress",          () -> recordGroupProgress(ctx));
        tryStep("notifyGroupMemberLogged",() -> notifyGroupMemberLogged(ctx));
        tryStep("nextWeekPlan",           () -> nextWeekPlan(ctx));
        tryStep("weeklyGoalNotify",       () -> notifyWeeklyGoalHit(ctx));
        tryStep("groupGoalNotify",        () -> notifyGroupGoalHit(ctx));
        tryStep("setLogCleanup",          () -> deleteSetLogs(ctx));

        log.info("postFinish DONE sessionId={} totalMs={}",
                ctx.sessionId(), (System.nanoTime() - t0) / 1_000_000);
    }

    /**
     * Run one pipeline step. Times it, logs success or failure, and
     * NEVER lets an exception propagate — the next step always runs.
     */
    private void tryStep(String name, Runnable step) {
        long t0 = System.nanoTime();
        try {
            step.run();
            log.info("postFinish step={} ms={}",
                    name, (System.nanoTime() - t0) / 1_000_000);
        } catch (Exception e) {
            log.error("postFinish step={} FAILED — pipeline continues",
                    name, e);
        }
    }

    // ── Step bodies ─────────────────────────────────────────────────

    /**
     * Clear any REST/SICK/BUSY/TRAVELLING status the user set earlier
     * today — completing a workout auto-flips their day status back to
     * READY (no status row).
     */
    private void clearTodayStatus(SessionFinishContext ctx) {
        LocalDate finishFitnessDay = Zones.fitnessDay(ctx.finishedAt());
        dayStatusRepo.findByIdUserIdAndIdDate(ctx.userId(), finishFitnessDay)
                .ifPresent(dayStatusRepo::delete);
    }

    /**
     * Write the post-finish streak value to the session row for the
     * history view ("what was the streak when you finished").
     * The user.streak field was already updated synchronously on the
     * HTTP thread in the streak-update block (block 3 of finishSession).
     */
    private void writeStreakSnapshot(SessionFinishContext ctx) {
        sessionRepo.updateStreak(ctx.sessionId(), ctx.currentStreak());
    }

    /**
     * Compute and persist the user's per-muscle strength scores for the
     * fitness week containing this session. Drives the Trends tab.
     */
    private void computeStrengthSnapshot(SessionFinishContext ctx) {
        LocalDate snapshotWeekStart = LocalDate.ofInstant(ctx.finishedAt(), Zones.APP_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        progressSnapshotService.computeForUserWeek(ctx.userId(), snapshotWeekStart);
    }

    /**
     * Check if the user qualifies for a rank promotion based on their
     * total distinct training days.
     */
    private void checkRankPromotion(SessionFinishContext ctx) {
        rankService.checkAndPromote(ctx.userId());
    }

    /**
     * Award the four base session coins:
     *   1. LOG_WORKOUT (+10) — every finish
     *   2. WEEKLY_GOAL (+50) — only on the exact session that hits the goal
     *   3. IMPROVE_VOLUME (+30) — when this week's volume beats last week's
     *   4. STREAK_MILESTONE (+10) — when post-finish streak ∈ {5,10,30,50,100,365}
     *
     * <p>Loads the WorkoutSession entity for the LOG_WORKOUT label.
     * The session is also reloaded inside writeWorkoutFinishedFeed —
     * accepts the duplicate read since both blocks need it independently
     * and the pipeline is off the HTTP thread anyway.
     */
    private void awardBaseCoins(SessionFinishContext ctx) {
        String weekEpoch = String.valueOf(ctx.weekFrom().getEpochSecond());

        // 1. Log workout +10
        String sessionName = sessionRepo.findById(ctx.sessionId())
                .map(WorkoutSession::getName).orElse("Workout");
        coinService.awardCoins(ctx.userId(), COINS_PER_SESSION, "LOG_WORKOUT",
                "Logged " + sessionName, ctx.sessionId().toString());

        // 2. Weekly goal +50 — only on the exact session that hits the goal
        if (ctx.completedThisWeek() == ctx.weeklyGoal()) {
            coinService.awardCoins(ctx.userId(), 50, "WEEKLY_GOAL",
                    "Weekly goal hit", weekEpoch);
        }

        // 3. Volume improvement vs last week +30
        Instant lastWeekFrom = ctx.weekFrom().minus(7, ChronoUnit.DAYS);
        BigDecimal thisWeekVol = sessionRepo.sumVolumeByUserIdAndFinishedAtBetween(
                ctx.userId(), ctx.weekFrom(), ctx.weekTo());
        BigDecimal lastWeekVol = sessionRepo.sumVolumeByUserIdAndFinishedAtBetween(
                ctx.userId(), lastWeekFrom, ctx.weekFrom());
        if (lastWeekVol != null && lastWeekVol.compareTo(BigDecimal.ZERO) > 0
                && thisWeekVol != null && thisWeekVol.compareTo(lastWeekVol) > 0) {
            coinService.awardCoins(ctx.userId(), 30, "IMPROVE_VOLUME",
                    "Improved vs last week", weekEpoch);
        }

        // 4. Streak milestones
        int streak = ctx.currentStreak();
        if (STREAK_MILESTONES.contains(streak)) {
            coinService.awardCoins(ctx.userId(), STREAK_MILESTONE_COINS_FLAT, "STREAK_MILESTONE",
                    streak + "-day streak milestone",
                    "streak-" + streak + "-" + ctx.sessionId());
        }
    }

    /**
     * Write a STREAK_MILESTONE feed event when the user crosses a
     * milestone day count. Runs after baseCoinAwards so the feed event
     * narrates the just-awarded coin.
     */
    private void writeStreakMilestoneFeed(SessionFinishContext ctx) {
        int streak = ctx.currentStreak();
        if (STREAK_MILESTONES.contains(streak)) {
            feedEventWriter.writeStreakMilestone(ctx.userId(), streak, STREAK_MILESTONE_COINS_FLAT);
        }
    }

    /**
     * Run PR detection over every set in the finished session.
     * Writes pr_events, user_exercise_bests, weekly_pr_counts,
     * coin_transactions for each PR detected.
     *
     * <p>The pre-canary timing log used to read pr_events back to
     * report a prCount — that lookup is dropped here. tryStep already
     * gives per-step duration; we don't need an extra DB round-trip
     * just for a log field.
     */
    private void runPrDetection(SessionFinishContext ctx) {
        prWritePathService.processSessionFinish(
                ctx.userId(), ctx.sessionId(), ctx.loggedSets());
    }

    /**
     * Post the WORKOUT_FINISHED feed event to every group the user
     * belongs to. Reloads the WorkoutSession on this thread — needed
     * because FeedEventWriter takes the entity, and detached entities
     * from the HTTP thread can't be passed across the async boundary.
     *
     * <p>Depends on streakSnapshot (block 2) having committed first
     * so the loaded session row reflects the post-finish streak that
     * the feed payload includes.
     */
    private void writeWorkoutFinishedFeed(SessionFinishContext ctx) {
        WorkoutSession session = sessionRepo.findById(ctx.sessionId()).orElse(null);
        if (session == null) {
            log.warn("writeWorkoutFinishedFeed: session {} not found, skipping", ctx.sessionId());
            return;
        }

        List<String> exerciseIds = ctx.loggedSets() == null ? List.of()
                : ctx.loggedSets().stream()
                        .map(LoggedSet::exerciseId)
                        .filter(Objects::nonNull).distinct()
                        .collect(Collectors.toList());
        List<String> muscleGroups = exerciseIds.isEmpty() ? List.of()
                : exerciseRepo.findAllById(exerciseIds).stream()
                        .map(Exercise::getMuscleGroup)
                        .filter(Objects::nonNull).distinct()
                        .collect(Collectors.toList());
        BigDecimal topLiftKg = ctx.loggedSets() == null ? null
                : ctx.loggedSets().stream()
                        .map(LoggedSet::weightKg)
                        .filter(Objects::nonNull)
                        .max(BigDecimal::compareTo)
                        .orElse(null);

        feedEventWriter.writeWorkoutFinished(session, muscleGroups, topLiftKg);
    }

    /**
     * Increment the current week's group progress counter for every
     * group the user belongs to.
     */
    private void recordGroupProgress(SessionFinishContext ctx) {
        groupProgressService.recordSessionForUser(
                ctx.userId(),
                ctx.finishedAt().atZone(Zones.APP_ZONE).toLocalDate());
    }

    /**
     * EVENT 1 — STREAK_MILESTONE: push + in-app when the user's streak
     * lands on a milestone value (5, 10, 30, 50, 100, 365).
     */
    void notifyStreakMilestone(SessionFinishContext ctx) {
        int streak = ctx.currentStreak();
        if (!STREAK_MILESTONES.contains(streak)) return;
        notificationService.notifyUser(
                ctx.userId(),
                "STREAK_MILESTONE",
                "🔥 " + streak + " day streak!",
                "You've trained " + streak + " days in a row. Keep the fire going!",
                null, null,
                Map.of("type", "STREAK_MILESTONE", "targetScreen", "/home"),
                true);
    }

    /**
     * EVENT 2 — WEEKLY_GOAL_HIT: push + in-app on the exact session that
     * crosses the user's weekly session goal. Fires once per week
     * (exact-match guard: completed == goal, not >=).
     */
    void notifyWeeklyGoalHit(SessionFinishContext ctx) {
        if (ctx.completedThisWeek() != ctx.weeklyGoal()) return;
        notificationService.notifyUser(
                ctx.userId(),
                "WEEKLY_GOAL_HIT",
                "🎯 Weekly goal smashed!",
                "You hit your " + ctx.weeklyGoal()
                        + "-session goal this week. Consistency wins.",
                null, null,
                Map.of("type", "WEEKLY_GOAL_HIT", "targetScreen", "/progress"),
                true);
    }

    /**
     * EVENT 3 — GROUP_GOAL_HIT: push + in-app to ALL members of each
     * group where every member has now met the group's weekly goal.
     * Dedup: skipped if a GROUP_GOAL_HIT notification was already
     * written for this group in the current week.
     */
    void notifyGroupGoalHit(SessionFinishContext ctx) {
        List<GroupMember> myMemberships = groupMemberRepo.findByUserId(ctx.userId());
        if (myMemberships.isEmpty()) return;

        List<UUID> groupIds = myMemberships.stream()
                .map(GroupMember::getGroupId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());

        java.time.OffsetDateTime weekStartOdt = ctx.weekFrom().atOffset(ZoneOffset.UTC);

        for (UUID groupId : groupIds) {
            Group group = groupRepo.findById(groupId).orElse(null);
            if (group == null) continue;
            int groupGoal = group.getWeeklyGoal() != null ? group.getWeeklyGoal() : 4;

            // Dedup — only fire once per group per week
            if (notificationRepo.existsByGroupIdAndTypeAndCreatedAtAfter(
                    groupId, "GROUP_GOAL_HIT", weekStartOdt)) continue;

            List<GroupMember> members = groupMemberRepo.findByGroupId(groupId);
            boolean allHit = members.stream().allMatch(m ->
                    sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                            m.getUserId(), "COMPLETED", ctx.weekFrom(), ctx.weekTo())
                    >= groupGoal);

            if (!allHit) continue;

            String title = "🏆 " + group.getName() + " crushed it!";
            String body  = "Everyone in " + group.getName()
                    + " hit their weekly goal. Total team effort!";
            Map<String, String> data = Map.of(
                    "type",         "GROUP_GOAL_HIT",
                    "targetScreen", "/group",
                    "groupId",      groupId.toString());

            for (GroupMember m : members) {
                notificationService.notifyUser(
                        m.getUserId(), "GROUP_GOAL_HIT", title, body,
                        null, groupId, data, true);
            }
        }
    }

    /**
     * EVENT 4 — GROUP_MEMBER_LOGGED: in-app ONLY (no push — too noisy)
     * to every other member of every group the finisher belongs to.
     * Replaces the old sendPushToGroupExceptUser call so the in-app feed
     * is populated as well.
     */
    void notifyGroupMemberLogged(SessionFinishContext ctx) {
        String displayName = userRepo.findById(ctx.userId())
                .map(User::getDisplayName).orElse("Someone");

        List<GroupMember> memberships = groupMemberRepo.findByUserId(ctx.userId());
        if (memberships.isEmpty()) return;

        List<UUID> groupIds = memberships.stream()
                .map(GroupMember::getGroupId).filter(Objects::nonNull).distinct()
                .collect(Collectors.toList());

        Map<UUID, String> groupNames = groupRepo.findAllById(groupIds).stream()
                .collect(Collectors.toMap(Group::getId, Group::getName));

        for (UUID groupId : groupIds) {
            String groupName = groupNames.getOrDefault(groupId, "your group");
            String title = "💪 " + displayName + " just trained";
            String body  = displayName + " logged a workout in " + groupName;
            Map<String, String> data = Map.of(
                    "type",           "GROUP_MEMBER_LOGGED",
                    "targetScreen",   "/group",
                    "groupId",        groupId.toString(),
                    "finishedUserId", ctx.userId().toString());

            for (GroupMember m : groupMemberRepo.findByGroupId(groupId)) {
                if (ctx.userId().equals(m.getUserId())) continue;
                notificationService.notifyUser(
                        m.getUserId(), "GROUP_MEMBER_LOGGED", title, body,
                        ctx.userId(), groupId, data, false);
            }
        }
    }

    /**
     * When the weekly goal was hit on this finish, generate next-week's
     * AI plan shell and grant any eligible bonus freeze tokens.
     * Both operations are no-ops when weeklyGoalHit is false.
     */
    private void nextWeekPlan(SessionFinishContext ctx) {
        if (!ctx.weeklyGoalHit()) return;

        planService.generatePlan(ctx.userId());

        LocalDate weekMonday = LocalDate.now(Zones.APP_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        bonusFreezeGrantService.grantIfEligible(ctx.userId(), ctx.weeklyGoal(), weekMonday);
    }

    /**
     * Delete set_logs rows for the finished session. Last in pipeline
     * order — PR detection is the only block that operates on per-set
     * data, and it uses the LoggedSet list from context, not set_logs.
     */
    private void deleteSetLogs(SessionFinishContext ctx) {
        setLogRepo.deleteBySessionId(ctx.sessionId());
    }
}
