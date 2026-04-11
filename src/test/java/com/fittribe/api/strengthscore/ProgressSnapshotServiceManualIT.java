package com.fittribe.api.strengthscore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.StrengthScoreHistory;
import com.fittribe.api.entity.UserProgressSnapshot;
import com.fittribe.api.entity.WorkoutSession;
import com.fittribe.api.repository.StrengthScoreHistoryRepository;
import com.fittribe.api.repository.UserProgressSnapshotRepository;
import com.fittribe.api.repository.WorkoutSessionRepository;
import com.fittribe.api.jobs.JobWorker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manual integration test for {@link ProgressSnapshotService} against the
 * live Railway Postgres database.
 *
 * <p>Calls {@code computeForSession()} for Harsh's most recent completed
 * session, then verifies that strength_score_history and user_progress_snapshot
 * rows were created with expected structure.
 *
 * <p>NOT @Transactional — the service uses TransactionTemplate internally,
 * which would be invisible to a test-level transaction. Cleanup is explicit
 * via try/finally.
 *
 * <h3>How to run</h3>
 * <pre>
 * export $(cat .env | xargs)
 * mvn test \
 *   -Dtest=ProgressSnapshotServiceManualIT \
 *   -Dfittribe.manualTest=true \
 *   -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "fittribe.manualTest", matches = "true")
class ProgressSnapshotServiceManualIT {

    /** Harsh's user ID — must have at least one completed session. */
    private static final UUID HARSH_ID =
            UUID.fromString("d60d34cf-cbe2-454c-b89e-6c7340e9b88b");

    @Autowired
    private ProgressSnapshotService progressSnapshotService;

    @Autowired
    private WorkoutSessionRepository sessionRepo;

    @Autowired
    private StrengthScoreHistoryRepository historyRepo;

    @Autowired
    private UserProgressSnapshotRepository snapshotRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void computeForMostRecentSession() throws Exception {
        // ── Find Harsh's most recent completed session ─────────────────
        List<WorkoutSession> sessions = sessionRepo.findTop3ByUserIdAndStatusOrderByStartedAtDesc(
                HARSH_ID, "COMPLETED");
        if (sessions.isEmpty()) {
            throw new IllegalStateException("Harsh has no completed sessions");
        }
        WorkoutSession session = sessions.get(0);

        UUID sessionId = session.getId();
        LocalDate weekStart = LocalDate.ofInstant(session.getFinishedAt(),
                java.time.ZoneOffset.UTC).with(java.time.DayOfWeek.MONDAY);

        System.out.println("=============== ProgressSnapshotService Manual IT ===============");
        System.out.println("User: " + HARSH_ID);
        System.out.println("Session: " + sessionId);
        System.out.println("Week start: " + weekStart);

        // ── Query for pre-existing rows (for cleanup) ──────────────────
        List<StrengthScoreHistory> preExistingHistory =
                historyRepo.findByUserIdAndWeekStartGreaterThanEqualOrderByWeekStartDesc(
                        HARSH_ID, weekStart);
        int preExistingHistoryCount = preExistingHistory.size();

        var preExistingSnapshot = snapshotRepo.findById(HARSH_ID);

        // ── Call the service ───────────────────────────────────────────
        progressSnapshotService.computeForUserWeek(HARSH_ID, weekStart);
        System.out.println("✓ computeForUserWeek() completed");

        // ── Assert: strength_score_history rows exist ──────────────────
        List<StrengthScoreHistory> historyRows =
                historyRepo.findByUserIdAndWeekStartGreaterThanEqualOrderByWeekStartDesc(
                        HARSH_ID, weekStart);
        int newHistoryCount = historyRows.size() - preExistingHistoryCount;

        System.out.println("\nStrength Score History rows created: " + newHistoryCount);
        for (StrengthScoreHistory row : historyRows.stream()
                .filter(r -> r.getWeekStart().equals(weekStart))
                .toList()) {
            System.out.println("  - " + row.getMuscle() + ": " + row.getStrengthScore());
        }

        assertFalse(historyRows.isEmpty(), "Expected at least one strength_score_history row");

        // ── Assert: user_progress_snapshot row exists ──────────────────
        var snapshotOpt = snapshotRepo.findById(HARSH_ID);
        assertTrue(snapshotOpt.isPresent(), "Expected user_progress_snapshot row");

        UserProgressSnapshot snapshot = snapshotOpt.get();
        assertNotNull(snapshot.getData(), "Snapshot data must not be null");

        // ── Parse and validate JSONB structure ─────────────────────────
        Map<String, Object> data = objectMapper.readValue(snapshot.getData(),
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

        assertNotNull(data.get("lastUpdated"), "lastUpdated field required");
        assertNotNull(data.get("muscleScores"), "muscleScores field required");
        assertNotNull(data.get("overallScore"), "overallScore field required");
        assertNotNull(data.get("formulaVersion"), "formulaVersion field required");

        // ── Pretty-print the snapshot ──────────────────────────────────
        System.out.println("\nSnapshot JSONB:");
        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(data);
        System.out.println(prettyJson);

        System.out.println("=============== Test Complete ===============");

        // ── Cleanup: delete rows created by this test ──────────────────
        try {
            // Delete strength_score_history rows for this week
            List<StrengthScoreHistory> rowsToDelete = historyRepo
                    .findByUserIdAndWeekStartGreaterThanEqualOrderByWeekStartDesc(
                            HARSH_ID, weekStart).stream()
                    .filter(r -> r.getWeekStart().equals(weekStart))
                    .toList();
            for (StrengthScoreHistory row : rowsToDelete) {
                historyRepo.deleteById(row.getId());
            }
            System.out.println("✓ Deleted " + rowsToDelete.size() + " strength_score_history rows");

            // Delete user_progress_snapshot if it's new (not pre-existing)
            if (preExistingSnapshot.isEmpty()) {
                snapshotRepo.deleteById(HARSH_ID);
                System.out.println("✓ Deleted user_progress_snapshot row");
            } else {
                System.out.println("ℹ Kept pre-existing user_progress_snapshot row");
            }
        } catch (Exception e) {
            System.err.println("Cleanup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
