package com.fittribe.api.service;

import com.fittribe.api.prv2.service.PrWritePathService;
import com.fittribe.api.repository.PrEventRepository;
import com.fittribe.api.repository.SetLogRepository;
import com.fittribe.api.repository.UserDayStatusRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.strengthscore.ProgressSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs the post-finish side-effect pipeline for a completed workout
 * session OFF the HTTP thread. SessionController.finishSession returns
 * the response after the hard-sync work (core save + streak); everything
 * else moves here.
 *
 * <p>Single ordered pipeline (NOT 10 independent async calls) so that
 * dependent steps run in the right order:
 * <ul>
 *   <li>baseCoinAwards must run before streakMilestoneFeed (feed event
 *       references the just-awarded coins)</li>
 *   <li>prDetection must run before workoutFinishedFeed (feed payload
 *       includes the prCount derived from pr_events)</li>
 *   <li>setLogCleanup runs LAST — confirmed PrWritePathService uses
 *       the loggedSets list passed in, not a re-read of set_logs</li>
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

    // Dependencies — full set listed up-front so we don't touch the
    // constructor again as blocks move in. Some are unused until the
    // matching block is moved.
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
            SetLogRepository setLogRepo) {
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

        tryStep("clearTodayStatus",      () -> clearTodayStatus(ctx));
        tryStep("streakSnapshot",        () -> writeStreakSnapshot(ctx));
        tryStep("strengthSnapshot",      () -> computeStrengthSnapshot(ctx));
        tryStep("rankPromotion",         () -> checkRankPromotion(ctx));
        tryStep("baseCoinAwards",        () -> awardBaseCoins(ctx));
        tryStep("streakMilestoneFeed",   () -> writeStreakMilestoneFeed(ctx));
        tryStep("prDetection",           () -> runPrDetection(ctx));
        tryStep("workoutFinishedFeed",   () -> writeWorkoutFinishedFeed(ctx));
        tryStep("groupProgress",         () -> recordGroupProgress(ctx));
        tryStep("setLogCleanup",         () -> deleteSetLogs(ctx));

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

    // ── Step bodies — STUBS for now ─────────────────────────────────
    // Each method moves over from SessionController.finishSession in a
    // separate commit. Keep stubs as no-ops until that step lands.

    private void clearTodayStatus(SessionFinishContext ctx) {
        // TODO: relocate from SessionController lines 931-939
    }

    private void writeStreakSnapshot(SessionFinishContext ctx) {
        // TODO: relocate from SessionController lines 965-973
    }

    private void computeStrengthSnapshot(SessionFinishContext ctx) {
        // TODO: relocate from SessionController lines 1011-1023
    }

    private void checkRankPromotion(SessionFinishContext ctx) {
        // TODO: relocate from SessionController lines 1026-1033
    }

    private void awardBaseCoins(SessionFinishContext ctx) {
        // TODO: relocate from SessionController lines 1037-1069
        //       (LOG_WORKOUT, WEEKLY_GOAL, IMPROVE_VOLUME, STREAK_MILESTONE)
        //       Likely needs context extension: sessionName, count, weeklyGoal,
        //       weekFrom, weekTo. Confirm before this step.
    }

    private void writeStreakMilestoneFeed(SessionFinishContext ctx) {
        // TODO: relocate from SessionController lines 1071-1080
    }

    private void runPrDetection(SessionFinishContext ctx) {
        // TODO: relocate from SessionController lines 1084-1103.
        //       Drop the post-call prEventRepo.findBySessionIdAndSupersededAtIsNull()
        //       lookup — it was only used for the timing log line, which is
        //       no longer meaningful here.
    }

    private void writeWorkoutFinishedFeed(SessionFinishContext ctx) {
        // TODO: relocate from SessionController lines 1106-1129.
        //       Will need to load the WorkoutSession on this thread via
        //       sessionRepo.findById(ctx.sessionId()) since FeedEventWriter
        //       takes the entity. One extra DB read, ~50ms. Or refactor
        //       FeedEventWriter to take primitives — out of scope per prompt.
    }

    private void recordGroupProgress(SessionFinishContext ctx) {
        // TODO: relocate from SessionController lines 1132-1140
    }

    private void deleteSetLogs(SessionFinishContext ctx) {
        setLogRepo.deleteBySessionId(ctx.sessionId());
    }
}
