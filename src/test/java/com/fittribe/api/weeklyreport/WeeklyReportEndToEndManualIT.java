package com.fittribe.api.weeklyreport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.PendingRecalibration;
import com.fittribe.api.entity.WeeklyReport;
import com.fittribe.api.jobs.JobEnqueuer;
import com.fittribe.api.jobs.JobType;
import com.fittribe.api.jobs.PendingJob;
import com.fittribe.api.jobs.PendingJobRepository;
import com.fittribe.api.repository.PendingRecalibrationRepository;
import com.fittribe.api.repository.WeeklyReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end manual integration test that exercises the complete
 * {@code JobEnqueuer → pending_jobs → JobWorker → WeeklyReportComputer}
 * pipeline against the live Railway Postgres database.
 *
 * <p>Unlike {@link WeeklyReportComputerManualIT}, which calls
 * {@code compute()} directly (and uses {@code @Transactional} rollback for
 * cleanup), this test exercises the job-queue path: it inserts a real
 * {@code COMPUTE_WEEKLY_REPORT} row, then polls every second for up to 30
 * seconds waiting for {@code JobWorker} to pick it up on its scheduled tick,
 * and asserts the {@code weekly_reports} row is written with the expected
 * shape.
 *
 * <p>NOT {@code @Transactional}. The worker runs its claim and terminal-state
 * transitions in its own {@code TransactionTemplate} calls on a separate
 * scheduler thread — those transactions are invisible to any outer test
 * transaction, so rollback-on-test-end would leave the job row in
 * {@code pending} but the weekly_reports row committed. Cleanup is therefore
 * done explicitly in a {@code try/finally} block so the live DB stays clean
 * even on assertion failure.
 *
 * <h3>Prerequisites</h3>
 * User Harsh ({@code d60d34cf-cbe2-454c-b89e-6c7340e9b88b}) must have 5
 * completed sessions in the DB for the week starting 2026-04-06 —
 * the test asserts {@code sessions_logged = 5} and {@code sessions_goal = 4}.
 *
 * <h3>How to run</h3>
 * <pre>
 * export $(cat .env | xargs)
 * mvn test \
 *   -Dtest=WeeklyReportEndToEndManualIT \
 *   -Dfittribe.manualTest=true \
 *   -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "fittribe.manualTest", matches = "true")
class WeeklyReportEndToEndManualIT {

    /** Harsh's user ID — has 5 sessions in the 2026-04-06 week. */
    private static final UUID HARSH_ID =
            UUID.fromString("d60d34cf-cbe2-454c-b89e-6c7340e9b88b");

    /** Week under test. */
    private static final LocalDate WEEK_START = LocalDate.of(2026, 4, 6);

    /** Max seconds to wait for the worker to pick up and process the job. */
    private static final int POLL_TIMEOUT_SECS = 30;

    @Autowired
    private JobEnqueuer jobEnqueuer;

    @Autowired
    private PendingJobRepository pendingJobRepo;

    @Autowired
    private WeeklyReportRepository weeklyReportRepo;

    @Autowired
    private PendingRecalibrationRepository pendingRecalibrationRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void weeklyReportJobRunsThroughWorkerAndWritesReport() throws Exception {
        // ── 1. Enqueue ─────────────────────────────────────────────────────
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", HARSH_ID.toString());
        payload.put("weekStart", WEEK_START.toString());
        Long jobId = jobEnqueuer.enqueue(JobType.COMPUTE_WEEKLY_REPORT, payload);
        System.out.println("E2E: enqueued job id=" + jobId);

        WeeklyReport row = null;
        try {
            // ── 2. Poll for completion (up to POLL_TIMEOUT_SECS seconds) ───────
            String finalStatus = null;
            for (int elapsed = 0; elapsed < POLL_TIMEOUT_SECS; elapsed++) {
                Thread.sleep(1_000);
                Optional<PendingJob> opt = pendingJobRepo.findById(jobId);
                if (opt.isEmpty()) {
                    fail("pending_jobs row id=" + jobId + " disappeared before reaching 'completed'");
                }
                String status = opt.get().getStatus();
                System.out.println("E2E: poll " + (elapsed + 1) + "s — status=" + status);
                if ("completed".equals(status)) {
                    finalStatus = status;
                    break;
                }
                if ("failed".equals(status)) {
                    fail("Job id=" + jobId + " reached 'failed' status; error="
                            + opt.get().getError());
                }
            }
            if (!"completed".equals(finalStatus)) {
                fail("Job id=" + jobId + " did not reach 'completed' within "
                        + POLL_TIMEOUT_SECS + "s (last status=" + finalStatus + ")");
            }

            // ── 3. Fetch the weekly_reports row ────────────────────────────────
            row = weeklyReportRepo
                    .findByUserIdAndWeekStart(HARSH_ID, WEEK_START)
                    .orElseThrow(() -> new AssertionError(
                            "weekly_reports row missing after job completed — "
                                    + "user=" + HARSH_ID + " weekStart=" + WEEK_START));

            // ── 4. Assertions ──────────────────────────────────────────────────
            assertEquals(5, row.getSessionsLogged(),
                    "Harsh has 5 sessions in the 2026-04-06 week");
            assertEquals(4, row.getSessionsGoal(),
                    "default sessions_goal must be 4");

            assertNotNull(row.getUserFirstName());
            assertTrue(!row.getUserFirstName().isBlank(), "userFirstName must not be blank");
            assertTrue(row.getWeekNumber() >= 1, "weekNumber must be positive");

            // verdict may be null when OPENAI_API_KEY is absent — log but don't fail
            System.out.println("E2E: verdict = "
                    + (row.getVerdict() == null ? "<null — no OpenAI key>" : row.getVerdict()));

            // findings: must be a JSON array with ≥3 entries
            JsonNode findingsNode = objectMapper.readTree(row.getFindings());
            assertTrue(findingsNode.isArray(), "findings column must be a JSON array");
            assertTrue(findingsNode.size() >= 3,
                    "findings must have ≥3 entries (got " + findingsNode.size() + ")");

            // muscle_coverage: must be a JSON array with exactly 8 tiles
            JsonNode muscleCoverageNode = objectMapper.readTree(row.getMuscleCoverage());
            assertTrue(muscleCoverageNode.isArray(), "muscle_coverage column must be a JSON array");
            assertEquals(8, muscleCoverageNode.size(),
                    "muscle_coverage must have exactly 8 tiles (got " + muscleCoverageNode.size() + ")");

            // recalibrations: must be a JSON array (may be empty)
            JsonNode recalibrationsNode = objectMapper.readTree(row.getRecalibrations());
            assertTrue(recalibrationsNode.isArray(), "recalibrations column must be a JSON array");

            // ── 5. Pending recalibrations fan-out ─────────────────────────────
            List<PendingRecalibration> unapplied =
                    pendingRecalibrationRepo.findUnappliedByUser(HARSH_ID);
            System.out.println("E2E: unapplied pending_recalibrations count = " + unapplied.size());

            // ── 6. Log full row as pretty-printed JSON ─────────────────────────
            System.out.println("=============== WEEKLY REPORT (E2E) ===============");
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(row));
            System.out.println("====================================================");

        } finally {
            // ── 7. Cleanup — runs even on assertion failure ────────────────────
            try {
                pendingJobRepo.deleteById(jobId);
                System.out.println("E2E cleanup: deleted pending_jobs id=" + jobId);
            } catch (Exception e) {
                System.err.println("E2E cleanup warning: could not delete pending_jobs id="
                        + jobId + ": " + e.getMessage());
            }
            if (row != null) {
                try {
                    weeklyReportRepo.deleteById(row.getId());
                    System.out.println("E2E cleanup: deleted weekly_reports id=" + row.getId());
                } catch (Exception e) {
                    System.err.println("E2E cleanup warning: could not delete weekly_reports id="
                            + row.getId() + ": " + e.getMessage());
                }
            }
        }
    }
}
