package com.fittribe.api.findings.rules;

import com.fittribe.api.findings.WeekData;
import com.fittribe.api.findings.WeeklyReportMuscle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test-only builder for {@link WeekData}. Provides sensible defaults (week 2,
 * goal 4, no sessions, all muscles at zero) and lets individual rule tests
 * override only the fields they care about. Keeps test setup to one or two
 * lines rather than a 21-field record constructor call.
 */
final class WeekDataFixture {

    UUID userId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000");
    LocalDate weekStart = LocalDate.of(2026, 4, 6);
    LocalDate weekEnd = LocalDate.of(2026, 4, 13);
    int weekNumber = 2;
    boolean isWeekOne = false;
    String userFirstName = "Asha";
    int sessionsLogged = 0;
    int sessionsGoal = 4;
    boolean weeklyGoalHit = false;
    BigDecimal totalKgVolume = BigDecimal.ZERO;
    int prCount = 0;
    List<WeekData.SessionSummary> sessions = new ArrayList<>();
    Map<String, List<WeekData.LoggedSet>> setsByExercise = new LinkedHashMap<>();
    Map<WeeklyReportMuscle, Integer> sessionsByMuscle = new EnumMap<>(WeeklyReportMuscle.class);
    int pushSessionCount = 0;
    int pullSessionCount = 0;
    int legsSessionCount = 0;
    Map<String, WeekData.TopSet> thisWeekTopSets = new LinkedHashMap<>();
    Map<String, WeekData.TopSet> previousWeekTopSets = new LinkedHashMap<>();
    List<WeekData.PrEntry> personalRecords = new ArrayList<>();
    Map<String, WeekData.TargetVsLogged> targetExercises = new LinkedHashMap<>();
    Map<String, WeekData.ExerciseMeta> exerciseCatalog = new LinkedHashMap<>();

    private WeekDataFixture() {
        for (WeeklyReportMuscle m : WeeklyReportMuscle.values()) sessionsByMuscle.put(m, 0);
    }

    static WeekDataFixture baseline() { return new WeekDataFixture(); }

    WeekDataFixture weekNumber(int n) {
        this.weekNumber = n;
        this.isWeekOne = (n == 1);
        return this;
    }

    WeekDataFixture weekOne() { return weekNumber(1); }

    WeekDataFixture sessionsLogged(int n) {
        this.sessionsLogged = n;
        this.weeklyGoalHit = (n >= sessionsGoal);
        return this;
    }

    WeekDataFixture sessionsGoal(int n) {
        this.sessionsGoal = n;
        this.weeklyGoalHit = (sessionsLogged >= n);
        return this;
    }

    WeekDataFixture pushCount(int n) { this.pushSessionCount = n; return this; }
    WeekDataFixture pullCount(int n) { this.pullSessionCount = n; return this; }
    WeekDataFixture legsCount(int n) { this.legsSessionCount = n; return this; }
    WeekDataFixture prCount(int n)   { this.prCount = n; return this; }

    WeekDataFixture totalKgVolume(double kg) {
        this.totalKgVolume = BigDecimal.valueOf(kg);
        return this;
    }

    WeekDataFixture muscleSessions(WeeklyReportMuscle m, int count) {
        this.sessionsByMuscle.put(m, count);
        return this;
    }

    /** Set every tile to 1 — a clean baseline for rules that do not care about muscles. */
    WeekDataFixture allMusclesTrained() {
        for (WeeklyReportMuscle m : WeeklyReportMuscle.values()) sessionsByMuscle.put(m, 1);
        return this;
    }

    WeekDataFixture thisWeekTop(String exerciseId, double kg, int reps) {
        thisWeekTopSets.put(exerciseId, new WeekData.TopSet(BigDecimal.valueOf(kg), reps));
        return this;
    }

    WeekDataFixture previousWeekTop(String exerciseId, double kg, int reps) {
        previousWeekTopSets.put(exerciseId, new WeekData.TopSet(BigDecimal.valueOf(kg), reps));
        return this;
    }

    WeekDataFixture target(String exerciseId, double targetKg, Double loggedMaxKg) {
        BigDecimal logged = loggedMaxKg == null ? null : BigDecimal.valueOf(loggedMaxKg);
        targetExercises.put(exerciseId, new WeekData.TargetVsLogged(
                exerciseId, BigDecimal.valueOf(targetKg), logged));
        return this;
    }

    WeekDataFixture exercise(String id, String name) {
        exerciseCatalog.put(id, new WeekData.ExerciseMeta(
                id, name, "CHEST", new String[0], false,
                WeekData.ExerciseMeta.PushPull.PUSH));
        return this;
    }

    WeekData build() {
        return new WeekData(
                userId, weekStart, weekEnd, weekNumber, isWeekOne, userFirstName,
                sessionsLogged, sessionsGoal, weeklyGoalHit, totalKgVolume, prCount,
                Collections.unmodifiableList(sessions),
                Collections.unmodifiableMap(setsByExercise),
                Collections.unmodifiableMap(sessionsByMuscle),
                pushSessionCount, pullSessionCount, legsSessionCount,
                Collections.unmodifiableMap(thisWeekTopSets),
                Collections.unmodifiableMap(previousWeekTopSets),
                Collections.unmodifiableList(personalRecords),
                Collections.unmodifiableMap(targetExercises),
                Collections.unmodifiableMap(exerciseCatalog));
    }
}
