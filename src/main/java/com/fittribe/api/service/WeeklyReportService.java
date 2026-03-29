package com.fittribe.api.service;

import com.fittribe.api.config.AiPrompts;
import com.fittribe.api.util.PromptSanitiser;
import com.fittribe.api.entity.AiInsight;
import com.fittribe.api.entity.Notification;
import com.fittribe.api.entity.SetLog;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.AiInsightRepository;
import com.fittribe.api.repository.NotificationRepository;
import com.fittribe.api.repository.SetLogRepository;
import com.fittribe.api.repository.UserPlanRepository;
import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WeeklyReportService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportService.class);
    private static final String TYPE = "weekly_summary";

    // ── Exercise → primary muscle group (matches the 19 seeded exercises) ──
    private static final Map<String, String> EXERCISE_MUSCLE = Map.ofEntries(
            Map.entry("bench-press",            "Chest"),
            Map.entry("incline-dumbbell-press",  "Chest"),
            Map.entry("cable-fly",               "Chest"),
            Map.entry("pull-up",                 "Back"),
            Map.entry("barbell-row",             "Back"),
            Map.entry("seated-cable-row",        "Back"),
            Map.entry("lat-pulldown",            "Back"),
            Map.entry("overhead-press",          "Shoulders"),
            Map.entry("lateral-raise",           "Shoulders"),
            Map.entry("face-pull",               "Shoulders"),
            Map.entry("squat",                   "Legs"),
            Map.entry("deadlift",                "Legs"),
            Map.entry("leg-press",               "Legs"),
            Map.entry("romanian-deadlift",       "Legs"),
            Map.entry("leg-curl",                "Legs"),
            Map.entry("barbell-curl",            "Arms"),
            Map.entry("tricep-pushdown",         "Arms"),
            Map.entry("hammer-curl",             "Arms"),
            Map.entry("plank",                   "Core")
    );
    private static final List<String> STANDARD_MUSCLES =
            List.of("Chest", "Back", "Shoulders", "Legs", "Arms", "Core");

    @Value("${openai.api-key:}")
    private String openAiKey;

    private final WorkoutSessionRepository sessionRepo;
    private final SetLogRepository         setLogRepo;
    private final UserRepository           userRepo;
    private final AiInsightRepository      insightRepo;
    private final UserPlanRepository       planRepo;
    private final NotificationRepository   notifRepo;
    private final PlanService              planService;
    private final RestTemplate             restTemplate = new RestTemplate();

    public WeeklyReportService(WorkoutSessionRepository sessionRepo,
                               SetLogRepository setLogRepo,
                               UserRepository userRepo,
                               AiInsightRepository insightRepo,
                               UserPlanRepository planRepo,
                               NotificationRepository notifRepo,
                               PlanService planService) {
        this.sessionRepo = sessionRepo;
        this.setLogRepo  = setLogRepo;
        this.userRepo    = userRepo;
        this.insightRepo = insightRepo;
        this.planRepo    = planRepo;
        this.notifRepo   = notifRepo;
        this.planService = planService;
    }

    @Async
    public void generateWeeklyReport(UUID userId, int weekNumber) {
        try {
            // Idempotent: skip if already generated
            Optional<AiInsight> existing = insightRepo
                    .findTopByUserIdAndInsightTypeAndExerciseNameOrderByGeneratedAtDesc(
                            userId, TYPE, String.valueOf(weekNumber));
            if (existing.isPresent()) return;

            User user = userRepo.findById(userId).orElse(null);
            if (user == null) return;

            // Derive week date range for weekNumber
            LocalDate monday = mondayForWeekNumber(user, weekNumber);
            LocalDate sunday = monday.plusDays(6);
            Instant from = monday.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant to   = sunday.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();

            List<WorkoutSession> sessions = sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                    userId, "COMPLETED", from, to);

            int sessionsDone = sessions.size();
            int weeklyGoal   = user.getWeeklyGoal() != null ? user.getWeeklyGoal() : 4;
            boolean goalHit  = sessionsDone >= weeklyGoal;

            // Aggregate sets from all sessions this week
            List<UUID> sessionIds = sessions.stream().map(WorkoutSession::getId).collect(Collectors.toList());
            List<SetLog> allSets = sessionIds.isEmpty() ? List.of() :
                    setLogRepo.findBySessionIdIn(sessionIds);

            // Guard against null weightKg/reps (bodyweight sets) — these caused a silent NPE
            BigDecimal totalVolume = allSets.stream()
                    .filter(sl -> sl.getWeightKg() != null && sl.getReps() != null)
                    .map(sl -> sl.getWeightKg().multiply(BigDecimal.valueOf(sl.getReps())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // PRs this week: sets flagged isPr
            List<String> weekPrs = allSets.stream()
                    .filter(sl -> Boolean.TRUE.equals(sl.getIsPr()))
                    .map(sl -> sl.getExerciseName() != null ? sl.getExerciseName() : sl.getExerciseId())
                    .distinct()
                    .collect(Collectors.toList());

            // Top exercise by volume
            Map<String, BigDecimal> volByExercise = allSets.stream()
                    .filter(sl -> sl.getWeightKg() != null && sl.getReps() != null)
                    .collect(Collectors.groupingBy(
                            sl -> sl.getExerciseName() != null ? sl.getExerciseName() : sl.getExerciseId(),
                            Collectors.reducing(BigDecimal.ZERO,
                                    sl -> sl.getWeightKg().multiply(BigDecimal.valueOf(sl.getReps())),
                                    BigDecimal::add)));
            String topExercise = volByExercise.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("none");

            String finding;
            if (openAiKey == null || openAiKey.isBlank()) {
                finding = buildMockFinding(weekNumber, sessionsDone, weeklyGoal, goalHit,
                        totalVolume, weekPrs, topExercise, user.getDisplayName());
            } else {
                finding = callOpenAiWeeklySummary(user, weekNumber, sessionsDone, weeklyGoal,
                        goalHit, totalVolume, weekPrs, topExercise);
            }

            AiInsight insight = new AiInsight();
            insight.setUserId(userId);
            insight.setInsightType(TYPE);
            insight.setExerciseName(String.valueOf(weekNumber));   // repurposed field — stores weekNumber
            insight.setMuscleGroup("weekly");                      // marker
            insight.setFinding(finding);
            insightRepo.save(insight);

            // Create WEEKLY_REPORT notification
            int prCount       = weekPrs.size();
            String topPr      = prCount > 0 ? weekPrs.get(0) : null;
            int recoveryScore = Math.min(100, (7 - sessionsDone) * 13 + (goalHit ? 9 : 0));
            int findingsCount = (int) insightRepo.countByUserIdAndInsightType(userId, TYPE);

            Notification notif = new Notification();
            notif.setUserId(userId);
            notif.setType("WEEKLY_REPORT");
            notif.setTitle("Your Week " + weekNumber + " report is ready");
            notif.setBody(buildNotificationBody(sessionsDone, topPr, recoveryScore, findingsCount));
            notif.setIsRead(false);
            log.info("Saving WEEKLY_REPORT notification for user {}", userId);
            notifRepo.save(notif);
            log.info("Notification saved: {}", notif.getId());

            log.info("Weekly report complete for user {} — triggering next week plan generation", userId);

            // Chain next week plan generation — runs in this async thread, non-blocking for the user.
            planService.generatePlan(userId);

        } catch (Exception e) {
            log.error("Weekly report failed for user {} week {}", userId, weekNumber, e);
        }
    }

    // ── Public read methods ────────────────────────────────────────────

    public Map<String, Object> getCurrentReport(UUID userId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return Map.of();

        LocalDate monday = LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int weekNumber = weekNumberFor(user, monday);

        return buildReportResponse(userId, user, weekNumber, monday);
    }

    public List<Map<String, Object>> getHistory(UUID userId) {
        List<AiInsight> reports = insightRepo.findByUserIdAndInsightType(userId, TYPE);
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return List.of();

        // Collect week numbers that already have a stored report
        Set<Integer> storedWeeks = reports.stream()
                .map(r -> parseWeekNumber(r.getExerciseName()))
                .collect(Collectors.toSet());

        List<Map<String, Object>> history = new ArrayList<>(reports.stream().map(r -> {
            int wn = parseWeekNumber(r.getExerciseName());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("weekNumber",  wn);
            m.put("finding",     r.getFinding());
            m.put("generatedAt", r.getGeneratedAt() != null ? r.getGeneratedAt().toString() : null);
            LocalDate monday = mondayForWeekNumber(user, wn);
            m.put("weekStartDate", monday.toString());
            return m;
        }).collect(Collectors.toList()));

        // Include current week if it has sessions but no stored insight yet
        LocalDate thisMonday = LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int currentWeek = weekNumberFor(user, thisMonday);
        if (!storedWeeks.contains(currentWeek)) {
            Instant from = thisMonday.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant to   = thisMonday.plusDays(6).atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();
            int count = sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(userId, "COMPLETED", from, to);
            if (count > 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("weekNumber",    currentWeek);
                m.put("finding",       null);   // report not yet generated
                m.put("generatedAt",   null);
                m.put("weekStartDate", thisMonday.toString());
                history.add(0, m);  // prepend so current week is first
            }
        }

        return history;
    }

    public Map<String, Object> getByWeekNumber(UUID userId, int weekNumber) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return Map.of();
        return buildReportResponse(userId, user, weekNumber,
                mondayForWeekNumber(user, weekNumber));
    }

    // ── Full structured report builder ─────────────────────────────────

    private Map<String, Object> buildReportResponse(UUID userId, User user, int weekNumber, LocalDate monday) {
        LocalDate sunday = monday.plusDays(6);
        Instant from = monday.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = sunday.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant();

        List<WorkoutSession> sessions = sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                userId, "COMPLETED", from, to);

        int sessionsDone = sessions.size();
        int weeklyGoal   = user.getWeeklyGoal() != null ? user.getWeeklyGoal() : 4;
        boolean goalHit  = sessionsDone >= weeklyGoal;

        // All sets for this week's sessions
        List<UUID> sessionIds = sessions.stream().map(WorkoutSession::getId).collect(Collectors.toList());
        List<SetLog> allSets = sessionIds.isEmpty() ? List.of() : setLogRepo.findBySessionIdIn(sessionIds);

        BigDecimal totalVolume = sessions.stream()
                .map(s -> s.getTotalVolumeKg() != null ? s.getTotalVolumeKg() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalDurationMins = sessions.stream()
                .mapToInt(s -> s.getDurationMins() != null ? s.getDurationMins() : 0)
                .sum();

        // Prior 8-week sets for strength trend comparison
        Instant priorFrom = monday.minusWeeks(8).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<WorkoutSession> priorSessions = sessionRepo.findByUserIdAndStatusAndFinishedAtBetween(
                userId, "COMPLETED", priorFrom, from);
        List<UUID> priorIds = priorSessions.stream().map(WorkoutSession::getId).collect(Collectors.toList());
        List<SetLog> priorSets = priorIds.isEmpty() ? List.of() : setLogRepo.findBySessionIdIn(priorIds);

        // Stored AI summary paragraph (may not exist yet if report hasn't been generated)
        Optional<AiInsight> stored = insightRepo
                .findTopByUserIdAndInsightTypeAndExerciseNameOrderByGeneratedAtDesc(
                        userId, TYPE, String.valueOf(weekNumber));

        // Recovery score
        int recoveryScore = Math.min(100, (7 - sessionsDone) * 13 + (goalHit ? 9 : 0));

        // Structured sections
        List<Map<String, Object>> muscleCoverage  = buildMuscleCoverage(allSets);
        List<Map<String, Object>> strengthTrends  = buildStrengthTrends(allSets, priorSets);
        List<Map<String, Object>> personalRecords = buildPersonalRecords(allSets);
        List<Map<String, Object>> findings        = buildFindings(sessionsDone, weeklyGoal, goalHit,
                muscleCoverage, strengthTrends, personalRecords.size());
        List<Map<String, Object>> recoveryChecklist = buildRecoveryChecklist(sessionsDone, weeklyGoal,
                user.getHealthConditions());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weekNumber",        weekNumber);
        result.put("weekStartDate",     monday.toString());
        result.put("weekEndDate",       sunday.toString());
        result.put("sessionsCompleted", sessionsDone);
        result.put("weeklyGoal",        weeklyGoal);
        result.put("goalHit",           goalHit);
        result.put("totalVolumeKg",     totalVolume);
        result.put("totalDurationMins", totalDurationMins);
        result.put("recoveryScore",     recoveryScore);
        result.put("muscleCoverage",    muscleCoverage);
        result.put("strengthTrends",    strengthTrends);
        result.put("personalRecords",   personalRecords);
        result.put("findings",          findings);
        result.put("recoveryChecklist", recoveryChecklist);
        // Never return null for aiWeeklySummary when sessions exist — show mock if no stored insight yet
        String aiWeeklySummary = stored.map(AiInsight::getFinding).orElse(null);
        if (aiWeeklySummary == null && sessionsDone > 0) {
            List<String> weekPrs = allSets.stream()
                    .filter(sl -> Boolean.TRUE.equals(sl.getIsPr()))
                    .map(sl -> sl.getExerciseName() != null ? sl.getExerciseName() : sl.getExerciseId())
                    .distinct()
                    .collect(Collectors.toList());
            aiWeeklySummary = "Week " + weekNumber + " complete. " + sessionsDone + "/" + weeklyGoal +
                    " sessions done" + (weekPrs.isEmpty() ? "" : ", " + weekPrs.size() + " PR(s)") +
                    ". Report is being generated — check back in a moment.";
        }
        result.put("aiWeeklySummary",   aiWeeklySummary);
        result.put("generatedAt",       stored.map(i -> i.getGeneratedAt() != null
                ? i.getGeneratedAt().toString() : null).orElse(null));
        return result;
    }

    // ── muscleCoverage ─────────────────────────────────────────────────

    /**
     * For each standard muscle group, count how many distinct sessions trained it.
     * GREEN = 2+ sessions, AMBER = 1, RED = 0.
     */
    private List<Map<String, Object>> buildMuscleCoverage(List<SetLog> allSets) {
        // sessionId → set of muscle groups worked
        Map<UUID, Set<String>> sessionMuscles = new HashMap<>();
        for (SetLog sl : allSets) {
            String muscle = EXERCISE_MUSCLE.get(sl.getExerciseId());
            if (muscle != null) {
                sessionMuscles.computeIfAbsent(sl.getSessionId(), k -> new HashSet<>()).add(muscle);
            }
        }

        // count distinct sessions per muscle group
        Map<String, Integer> muscleSessionCount = new HashMap<>();
        for (Set<String> muscles : sessionMuscles.values()) {
            for (String muscle : muscles) {
                muscleSessionCount.merge(muscle, 1, Integer::sum);
            }
        }

        return STANDARD_MUSCLES.stream().map(muscle -> {
            int count = muscleSessionCount.getOrDefault(muscle, 0);
            String status = count >= 2 ? "GREEN" : count == 1 ? "AMBER" : "RED";
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("muscleGroup",  muscle);
            m.put("sessionCount", count);
            m.put("status",       status);
            return m;
        }).collect(Collectors.toList());
    }

    // ── strengthTrends ─────────────────────────────────────────────────

    /**
     * For each exercise trained this week, compare the heaviest set this week
     * vs the heaviest set in the prior 8 weeks.
     * ABOVE = improved, CLOSE = matched or first ever, TOO_LIGHT = regression.
     */
    private List<Map<String, Object>> buildStrengthTrends(List<SetLog> allSets, List<SetLog> priorSets) {
        // Best weight this week per exercise (keyed by display name)
        Map<String, BigDecimal> thisWeekBest = new LinkedHashMap<>();
        for (SetLog sl : allSets) {
            if (sl.getWeightKg() == null) continue;
            String name = sl.getExerciseName() != null ? sl.getExerciseName() : sl.getExerciseId();
            thisWeekBest.merge(name, sl.getWeightKg(), BigDecimal::max);
        }

        // Best weight in prior 8 weeks per exercise
        Map<String, BigDecimal> priorBest = new HashMap<>();
        for (SetLog sl : priorSets) {
            if (sl.getWeightKg() == null) continue;
            String name = sl.getExerciseName() != null ? sl.getExerciseName() : sl.getExerciseId();
            priorBest.merge(name, sl.getWeightKg(), BigDecimal::max);
        }

        return thisWeekBest.entrySet().stream().map(e -> {
            String exName = e.getKey();
            BigDecimal usedKg = e.getValue();
            BigDecimal prior  = priorBest.get(exName);

            BigDecimal targetKg;
            String assessment;
            if (prior == null) {
                targetKg   = usedKg;   // first time training this exercise
                assessment = "CLOSE";
            } else if (usedKg.compareTo(prior) > 0) {
                targetKg   = prior;
                assessment = "ABOVE";
            } else if (usedKg.compareTo(prior) == 0) {
                targetKg   = prior;
                assessment = "CLOSE";
            } else {
                targetKg   = prior;
                assessment = "TOO_LIGHT";
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("exerciseName", exName);
            m.put("usedKg",       usedKg);
            m.put("targetKg",     targetKg);
            m.put("assessment",   assessment);
            return m;
        }).collect(Collectors.toList());
    }

    // ── personalRecords ────────────────────────────────────────────────

    /**
     * Sets flagged is_pr=true this week. Returns the heaviest PR set per exercise.
     */
    private List<Map<String, Object>> buildPersonalRecords(List<SetLog> allSets) {
        Map<String, SetLog> prsByExercise = new LinkedHashMap<>();
        for (SetLog sl : allSets) {
            if (!Boolean.TRUE.equals(sl.getIsPr()) || sl.getWeightKg() == null) continue;
            String name = sl.getExerciseName() != null ? sl.getExerciseName() : sl.getExerciseId();
            prsByExercise.merge(name, sl, (a, b) ->
                    a.getWeightKg().compareTo(b.getWeightKg()) >= 0 ? a : b);
        }

        return prsByExercise.entrySet().stream().map(e -> {
            SetLog sl = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("exerciseName", e.getKey());
            m.put("type",         "PR");
            m.put("value",        sl.getWeightKg() + "kg x " + sl.getReps() + " reps");
            return m;
        }).collect(Collectors.toList());
    }

    // ── findings ───────────────────────────────────────────────────────

    private List<Map<String, Object>> buildFindings(int sessionsDone, int weeklyGoal, boolean goalHit,
            List<Map<String, Object>> muscleCoverage,
            List<Map<String, Object>> strengthTrends,
            int prCount) {

        List<Map<String, Object>> findings = new ArrayList<>();

        // Goal status
        if (goalHit) {
            findings.add(finding("GOOD",
                    "Weekly goal achieved",
                    sessionsDone + "/" + weeklyGoal + " sessions completed. Consistency is compounding."));
        } else {
            int missed = weeklyGoal - sessionsDone;
            findings.add(finding(missed > 1 ? "CRITICAL" : "WARNING",
                    missed + " session" + (missed > 1 ? "s" : "") + " missed",
                    "Completed " + sessionsDone + "/" + weeklyGoal +
                    ". Book workouts in advance to hit the target next week."));
        }

        // PRs
        if (prCount > 0) {
            findings.add(finding("GOOD",
                    prCount + " personal record" + (prCount > 1 ? "s" : "") + " set",
                    "New PB this week — strength is trending in the right direction."));
        }

        // Untrained muscle groups (RED)
        List<String> redMuscles = muscleCoverage.stream()
                .filter(m -> "RED".equals(m.get("status")))
                .map(m -> (String) m.get("muscleGroup"))
                .collect(Collectors.toList());
        if (!redMuscles.isEmpty()) {
            String muscleList = String.join(", ", redMuscles);
            findings.add(finding("CRITICAL",
                    redMuscles.size() == 1
                            ? redMuscles.get(0) + " not trained this week"
                            : redMuscles.size() + " muscle groups skipped",
                    muscleList + " had zero sessions. Add a " + muscleList.toLowerCase() +
                    " session next week to maintain balance."));
        }

        // Strength regression
        List<String> tooLight = strengthTrends.stream()
                .filter(t -> "TOO_LIGHT".equals(t.get("assessment")))
                .map(t -> (String) t.get("exerciseName"))
                .collect(Collectors.toList());
        if (!tooLight.isEmpty()) {
            findings.add(finding("WARNING",
                    "Weights below previous best",
                    String.join(", ", tooLight) +
                    " logged lighter than your recent best. Focus on progressive overload next session."));
        }

        // Strength improvement (only if no PRs already shown to avoid duplication)
        if (prCount == 0) {
            List<String> above = strengthTrends.stream()
                    .filter(t -> "ABOVE".equals(t.get("assessment")))
                    .map(t -> (String) t.get("exerciseName"))
                    .collect(Collectors.toList());
            if (!above.isEmpty()) {
                findings.add(finding("GOOD",
                        "Strength improving",
                        String.join(", ", above) + " exceeded your previous training weight."));
            }
        }

        return findings;
    }

    private Map<String, Object> finding(String type, String title, String detail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type",   type);
        m.put("title",  title);
        m.put("detail", detail);
        return m;
    }

    // ── recoveryChecklist ──────────────────────────────────────────────

    private List<Map<String, Object>> buildRecoveryChecklist(int sessionsDone, int weeklyGoal,
            String[] healthConditions) {

        List<Map<String, Object>> list = new ArrayList<>();

        int restDays = 7 - sessionsDone;
        String restStatus = restDays >= 2 ? "OK" : restDays == 1 ? "CHECK" : "CRITICAL";
        list.add(checkItem("Rest days taken (" + restDays + " of 7)", restStatus));

        list.add(checkItem("Sleep 7-8 hrs per night", "REMINDER"));
        list.add(checkItem("Drink 2L+ water daily",   "REMINDER"));
        list.add(checkItem("Post-workout stretching",  "REMINDER"));

        // Health-condition-specific entries
        if (healthConditions != null) {
            for (String condition : healthConditions) {
                if (condition == null) continue;
                String lower = condition.toLowerCase();
                if (lower.contains("knee")) {
                    list.add(checkItem("Avoid heavy knee loading (knee condition)", "NOT_FOLLOWED"));
                } else if (lower.contains("back") || lower.contains("spine")) {
                    list.add(checkItem("Maintain neutral spine on all lifts (back condition)", "NOT_FOLLOWED"));
                } else if (lower.contains("shoulder")) {
                    list.add(checkItem("Limit overhead pressing load (shoulder condition)", "NOT_FOLLOWED"));
                }
            }
        }

        return list;
    }

    private Map<String, Object> checkItem(String label, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label",  label);
        m.put("status", status);
        return m;
    }

    // ── Helpers ────────────────────────────────────────────────────────

    public int weekNumberFor(User user, LocalDate targetMonday) {
        LocalDate createdMonday = user.getCreatedAt()
                .atZone(ZoneOffset.UTC).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return (int) java.time.temporal.ChronoUnit.WEEKS.between(createdMonday, targetMonday) + 1;
    }

    private LocalDate mondayForWeekNumber(User user, int weekNumber) {
        LocalDate createdMonday = user.getCreatedAt()
                .atZone(ZoneOffset.UTC).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return createdMonday.plusWeeks(weekNumber - 1);
    }

    private int parseWeekNumber(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private String buildNotificationBody(int sessionsDone, String topPr,
                                          int recoveryScore, int findingsCount) {
        StringBuilder sb = new StringBuilder();
        sb.append(sessionsDone).append(" sessions done. ");
        if (topPr != null) sb.append(topPr).append(" PR. ");
        sb.append("Recovery score ").append(recoveryScore).append("% — AI has ")
          .append(findingsCount).append(" fixes.");
        return sb.toString();
    }

    private String buildMockFinding(int weekNumber, int done, int goal, boolean goalHit,
                                     BigDecimal volume, List<String> prs, String topExercise, String name) {
        String nameStr = name != null ? name : "Athlete";
        String prsStr  = prs.isEmpty() ? "no PRs" : prs.size() + " PR(s): " + String.join(", ", prs);
        if (goalHit) {
            return nameStr + " crushed Week " + weekNumber + " — " + done + "/" + goal + " sessions completed" +
                    ", " + volume.setScale(1, RoundingMode.HALF_UP) + "kg total volume" +
                    ", " + prsStr + ". Top exercise: " + topExercise +
                    ". Consistency is compounding — keep the streak alive next week.";
        } else {
            return nameStr + " completed " + done + "/" + goal + " sessions in Week " + weekNumber +
                    " — " + volume.setScale(1, RoundingMode.HALF_UP) + "kg total volume, " + prsStr + "." +
                    " Missing " + (goal - done) + " session(s). Aim to book workouts in advance to hit the target.";
        }
    }

    private String callOpenAiWeeklySummary(User user, int weekNumber, int done, int goal,
                                            boolean goalHit, BigDecimal volume,
                                            List<String> prs, String topExercise) {
        String restDays    = String.valueOf(7 - done);
        int recoveryScore  = Math.min(100, (7 - done) * 13 + (goalHit ? 9 : 0));
        String muscleGaps  = goalHit ? "None detected" : "Insufficient data — " + (goal - done) + " sessions missed";
        String prsStr      = prs.isEmpty() ? "none" :
                prs.stream().map(PromptSanitiser::sanitise).collect(Collectors.joining(", "));

        String userPrompt = AiPrompts.WEEKLY_SUMMARY_USER
                .replace("{name}",         PromptSanitiser.sanitise(user.getDisplayName() != null ? user.getDisplayName() : "Athlete"))
                .replace("{fitnessLevel}", user.getFitnessLevel() != null ? user.getFitnessLevel() : "unknown")
                .replace("{goal}",         user.getGoal()         != null ? user.getGoal()         : "unknown")
                .replace("{weekNumber}",   String.valueOf(weekNumber))
                .replace("{sessionsDone}", String.valueOf(done))
                .replace("{weeklyGoal}",   String.valueOf(goal))
                .replace("{totalVolumeKg}", volume.setScale(1, RoundingMode.HALF_UP).toString())
                .replace("{prs}",          prsStr)
                .replace("{muscleGaps}",   muscleGaps)
                .replace("{recoveryScore}", String.valueOf(recoveryScore));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "gpt-4o-mini");
        body.put("max_tokens", 160);
        body.put("messages", List.of(
                Map.of("role", "system", "content", AiPrompts.WEEKLY_SUMMARY_SYSTEM),
                Map.of("role", "user",   "content", userPrompt)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiKey);

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = restTemplate.postForObject(
                "https://api.openai.com/v1/chat/completions", new HttpEntity<>(body, headers), Map.class);
        if (resp == null) return buildMockFinding(weekNumber, done, goal, goalHit, volume, prs, topExercise, user.getDisplayName());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
        if (choices == null || choices.isEmpty()) return buildMockFinding(weekNumber, done, goal, goalHit, volume, prs, topExercise, user.getDisplayName());

        @SuppressWarnings("unchecked")
        Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
        return msg != null ? (String) msg.get("content") : buildMockFinding(weekNumber, done, goal, goalHit, volume, prs, topExercise, user.getDisplayName());
    }
}
