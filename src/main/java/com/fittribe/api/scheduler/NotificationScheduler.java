package com.fittribe.api.scheduler;

import com.fittribe.api.entity.Group;
import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.UserDayStatus;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.GroupRepository;
import com.fittribe.api.repository.NotificationRepository;
import com.fittribe.api.repository.UserDayStatusRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.service.NotificationCopy;
import com.fittribe.api.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduled notification jobs for time-based events.
 *
 * <p>All times are in IST (Asia/Kolkata, UTC+5:30). Every job wraps per-user
 * work in a try-catch so a single failure never aborts the whole run.
 *
 * <p>Mutual exclusion order for evening notifications:
 * <ol>
 *   <li>STREAK_RISK fires first (8 PM IST)</li>
 *   <li>GROUP_TRAINED_WITHOUT_YOU skips users already sent STREAK_RISK today</li>
 *   <li>COMEBACK_NUDGE (6 PM IST) skips users who received STREAK_RISK or
 *       GROUP_TRAINED_WITHOUT_YOU today</li>
 * </ol>
 */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Set<String> EXEMPT_STATUSES = Set.of("REST", "SICK", "TRAVELLING", "BUSY");

    private final NotificationService      notificationService;
    private final UserRepository           userRepo;
    private final WorkoutSessionRepository sessionRepo;
    private final GroupMemberRepository    groupMemberRepo;
    private final GroupRepository          groupRepo;
    private final NotificationRepository   notificationRepo;
    private final UserDayStatusRepository  userDayStatusRepo;

    public NotificationScheduler(NotificationService notificationService,
                                 UserRepository userRepo,
                                 WorkoutSessionRepository sessionRepo,
                                 GroupMemberRepository groupMemberRepo,
                                 GroupRepository groupRepo,
                                 NotificationRepository notificationRepo,
                                 UserDayStatusRepository userDayStatusRepo) {
        this.notificationService = notificationService;
        this.userRepo            = userRepo;
        this.sessionRepo         = sessionRepo;
        this.groupMemberRepo     = groupMemberRepo;
        this.groupRepo           = groupRepo;
        this.notificationRepo    = notificationRepo;
        this.userDayStatusRepo   = userDayStatusRepo;
    }

    // ── JOB 1: Daily Workout Reminder — 8 PM IST ─────────────────────────────

    /**
     * All users who haven't logged a COMPLETED session today (6 AM IST fitness-day
     * rollover) get a reminder. Respects day status (REST/SICK/TRAVELLING/BUSY
     * suppress). Copy selection: streak ≥ 3 uses "don't break streak" urgency;
     * streak 0-2 uses general "log today" reminder. Fires at 8 PM IST.
     */
    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Kolkata")
    public void dailyWorkoutReminderJob() {
        LocalDate today    = LocalDate.now(IST);
        Instant   dayStart = today.atStartOfDay(IST).toInstant();
        Instant   dayEnd   = today.plusDays(1).atStartOfDay(IST).toInstant();

        // Load all users (no streak threshold)
        List<User> allUsers = userRepo.findAll();
        log.info("dailyWorkoutReminderJob: totalUsers={}", allUsers.size());

        List<UUID> candidates = new ArrayList<>();

        for (User user : allUsers) {
            try {
                // Skip if already trained today
                boolean trainedToday = sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                        user.getId(), "COMPLETED", dayStart, dayEnd);
                if (trainedToday) continue;

                // Skip if status is exempt (REST, SICK, TRAVELLING, BUSY) or unset → OK
                Optional<com.fittribe.api.entity.UserDayStatus> dayStatus =
                        userDayStatusRepo.findByIdUserIdAndIdDate(user.getId(), today);
                if (dayStatus.isPresent() && EXEMPT_STATUSES.contains(dayStatus.get().getStatus())) {
                    continue;
                }

                candidates.add(user.getId());
            } catch (Exception e) {
                log.error("dailyWorkoutReminderJob: filter failed for userId={}", user.getId(), e);
            }
        }

        log.info("dailyWorkoutReminderJob: candidates={}", candidates.size());

        for (UUID userId : candidates) {
            try {
                User user = userRepo.findById(userId).orElse(null);
                if (user == null) continue;

                String notificationType;
                NotificationCopy.Copy copy;

                // Copy selection based on streak
                if (user.getStreak() != null && user.getStreak() >= 3) {
                    notificationType = "STREAK_RISK";
                    copy = NotificationCopy.streakRisk(user.getStreak());
                } else {
                    notificationType = "DAILY_REMINDER";
                    copy = NotificationCopy.dailyWorkoutReminder();
                }

                notificationService.notifyUser(
                        userId, notificationType,
                        copy.title(), copy.body(),
                        null, null,
                        Map.of("type", notificationType, "targetScreen", "/home"),
                        true);
            } catch (Exception e) {
                log.error("dailyWorkoutReminderJob: failed for userId={}", userId, e);
            }
        }
    }

    // ── JOB 2: Group Trained Without You — 9:30 PM IST ───────────────────────

    /**
     * Members who didn't train today, when at least one teammate did, get a
     * FOMO push. Skips users who already got STREAK_RISK today. Each user
     * gets at most one notification (their first eligible group wins). Fires
     * at 9:30 PM IST.
     */
    @Scheduled(cron = "0 30 21 * * *", zone = "Asia/Kolkata")
    public void groupTrainedWithoutYouJob() {
        LocalDate today    = LocalDate.now(IST);
        Instant   dayStart = today.atStartOfDay(IST).toInstant();
        Instant   dayEnd   = today.plusDays(1).atStartOfDay(IST).toInstant();

        List<Group> allGroups = groupRepo.findAll();
        log.info("groupTrainedWithoutYouJob: groups={}", allGroups.size());

        Set<UUID> notifiedUsers = new HashSet<>();

        for (Group group : allGroups) {
            try {
                List<GroupMember> members = groupMemberRepo.findByGroupId(group.getId());
                if (members.size() < 2) continue;

                List<UUID> trained    = new ArrayList<>();
                List<UUID> notTrained = new ArrayList<>();

                for (GroupMember m : members) {
                    boolean t = sessionRepo.existsByUserIdAndStatusAndFinishedAtBetween(
                            m.getUserId(), "COMPLETED", dayStart, dayEnd);
                    if (t) trained.add(m.getUserId());
                    else   notTrained.add(m.getUserId());
                }

                if (trained.isEmpty()) continue; // nobody trained today in this group

                for (UUID userId : notTrained) {
                    if (notifiedUsers.contains(userId)) continue;

                    if (hasReceivedPushToday(userId, "STREAK_RISK", "DAILY_REMINDER")) continue;

                    Optional<UserDayStatus> ds =
                            userDayStatusRepo.findByIdUserIdAndIdDate(userId, today);
                    if (ds.isPresent() && EXEMPT_STATUSES.contains(ds.get().getStatus())) continue;

                    try {
                        NotificationCopy.Copy copy =
                                NotificationCopy.groupTrainedWithoutYou(group.getName(), trained.size());
                        notificationService.notifyUser(
                                userId, "GROUP_TRAINED_WITHOUT_YOU",
                                copy.title(), copy.body(),
                                null, group.getId(),
                                Map.of("type",         "GROUP_TRAINED_WITHOUT_YOU",
                                       "targetScreen", "/group",
                                       "groupId",      group.getId().toString()),
                                true);
                        notifiedUsers.add(userId);
                    } catch (Exception e) {
                        log.error("groupTrainedWithoutYouJob: notify failed userId={}", userId, e);
                    }
                }
            } catch (Exception e) {
                log.error("groupTrainedWithoutYouJob: failed for groupId={}", group.getId(), e);
            }
        }
    }

    // ── JOB 3: Weekly Report Ready — Monday 9 AM IST ─────────────────────────

    /**
     * Notifies all active users that their weekly report is ready to view.
     * Fires Monday at 9 AM IST, after WeeklyReportCron (4:30 AM IST) has had
     * time to enqueue and process all user reports.
     */
    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Kolkata")
    public void weeklyReportNotifyJob() {
        List<UUID> userIds = userRepo.findActiveUserIds();
        log.info("weeklyReportNotifyJob: users={}", userIds.size());

        for (UUID userId : userIds) {
            try {
                NotificationCopy.Copy copy = NotificationCopy.weeklyReportReady();
                notificationService.notifyUser(
                        userId, "WEEKLY_REPORT_READY",
                        copy.title(), copy.body(),
                        null, null,
                        Map.of("type", "WEEKLY_REPORT_READY", "targetScreen", "/weekly-summary"),
                        true);
            } catch (Exception e) {
                log.error("weeklyReportNotifyJob: failed for userId={}", userId, e);
            }
        }
    }

    // ── JOB 4: Comeback Nudge — 6 PM IST ─────────────────────────────────────

    /**
     * Users with streak == 0 and no workout in the past 48 hours get a gentle
     * re-engagement push. Guards: 3-day cooldown per user, skips users who
     * already got STREAK_RISK or GROUP_TRAINED_WITHOUT_YOU today, skips
     * REST/SICK/TRAVELLING users. Fires at 6 PM IST.
     */
    @Scheduled(cron = "0 0 18 * * *", zone = "Asia/Kolkata")
    public void comebackNudgeJob() {
        LocalDate today            = LocalDate.now(IST);
        Instant   fortyEightHoAgo = Instant.now().minus(48, ChronoUnit.HOURS);
        Instant   threeDaysAgo    = Instant.now().minus(3, ChronoUnit.DAYS);

        List<User> candidates = userRepo.findAllByStreak(0);
        log.info("comebackNudgeJob: candidates={}", candidates.size());

        for (User user : candidates) {
            try {
                Optional<WorkoutSession> last = sessionRepo
                        .findFirstByUserIdAndStatusOrderByFinishedAtDesc(user.getId(), "COMPLETED");
                if (last.isEmpty()) continue; // never trained

                Instant lastFinished = last.get().getFinishedAt();
                if (lastFinished == null || lastFinished.isAfter(fortyEightHoAgo)) continue;

                if (hasReceivedPushToday(user.getId(), "STREAK_RISK", "DAILY_REMINDER", "GROUP_TRAINED_WITHOUT_YOU"))
                    continue;

                OffsetDateTime cooldownStart = threeDaysAgo.atOffset(ZoneOffset.UTC);
                if (notificationRepo.existsByRecipientIdAndTypeAndCreatedAtAfter(
                        user.getId(), "COMEBACK_NUDGE", cooldownStart)) continue;

                Optional<UserDayStatus> ds =
                        userDayStatusRepo.findByIdUserIdAndIdDate(user.getId(), today);
                if (ds.isPresent() && EXEMPT_STATUSES.contains(ds.get().getStatus())) continue;

                long daysSince = ChronoUnit.DAYS.between(
                        lastFinished.atZone(IST).toLocalDate(), today);

                NotificationCopy.Copy copy = NotificationCopy.comebackNudge((int) daysSince);
                notificationService.notifyUser(
                        user.getId(), "COMEBACK_NUDGE",
                        copy.title(), copy.body(),
                        null, null,
                        Map.of("type", "COMEBACK_NUDGE", "targetScreen", "/home"),
                        true);
            } catch (Exception e) {
                log.error("comebackNudgeJob: failed for userId={}", user.getId(), e);
            }
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Returns true if ANY of the given notification types was saved for this
     * user after today's midnight IST. Used for mutual exclusion between the
     * evening jobs so the noisiest signal wins and the rest are suppressed.
     */
    boolean hasReceivedPushToday(UUID userId, String... types) {
        OffsetDateTime midnightIst =
                LocalDate.now(IST).atStartOfDay(IST).toInstant().atOffset(ZoneOffset.UTC);
        for (String type : types) {
            if (notificationRepo.existsByRecipientIdAndTypeAndCreatedAtAfter(
                    userId, type, midnightIst)) {
                return true;
            }
        }
        return false;
    }
}
