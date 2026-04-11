package com.fittribe.api.weeklyreport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.PendingRecalibration;
import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.FindingsGenerator;
import com.fittribe.api.findings.WeekData;
import com.fittribe.api.findings.WeekDataBuilder;
import com.fittribe.api.findings.WeeklyReportMuscle;
import com.fittribe.api.repository.PendingRecalibrationRepository;
import com.fittribe.api.repository.WeeklyReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stage 4 of Workstream A — orchestrates a single weekly report
 * computation for one user and one week.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>{@link WeekDataBuilder} — read all session/catalog/PR state and
 *       freeze it into an immutable {@link WeekData} snapshot.</li>
 *   <li>{@link FindingsGenerator} — run every rule against the snapshot
 *       and pick the top 4 findings (A2.2 semantics).</li>
 *   <li>{@link RecalibrationDetector} — derive plan-weight adjustments
 *       implied by the fired findings (A3.2).</li>
 *   <li>{@link VerdictGenerator} — one-sentence AI summary. May return
 *       null on any failure; {@code weekly_reports.verdict} stays null
 *       in that case.</li>
 *   <li>Serialize five JSONB payloads: {@code personal_records},
 *       {@code baselines}, {@code muscle_coverage}, {@code findings},
 *       {@code recalibrations}.</li>
 *   <li>Upsert one row into {@code weekly_reports}.</li>
 *   <li>Insert one row per {@link Recalibration} into
 *       {@code pending_recalibrations}.</li>
 * </ol>
 *
 * <h3>Transaction model</h3>
 * The read + compute phase runs outside any transaction — the
 * {@link VerdictGenerator} call alone can take up to 10s on the OpenAI
 * round-trip, and holding a DB transaction open for that long would
 * tie up a connection uselessly. Only the write phase is wrapped in a
 * {@link TransactionTemplate}-backed transaction so the upsert and the
 * recalibration inserts commit atomically. This matches the pattern in
 * {@code JobWorker}.
 *
 * <h3>Muscle coverage thresholds (v1.1)</h3>
 * Each of the 8 {@link WeeklyReportMuscle} tiles is assigned a status
 * based on the number of distinct sessions that trained it this week:
 * <ul>
 *   <li>0 sessions → {@code RED}</li>
 *   <li>1 session → {@code AMBER}</li>
 *   <li>2 or more sessions → {@code GREEN}</li>
 * </ul>
 * Mirrored in {@link #statusForCoverage(int)}.
 *
 * <h3>BigDecimal normalization</h3>
 * All {@link BigDecimal} values that get serialized into JSONB are
 * normalized via {@link #normalizeKg(BigDecimal)} — strips trailing
 * zeros and forces a non-negative scale so Jackson serializes
 * {@code "100.0"} as {@code 100} instead of
 * {@code 1E+2}. Same rationale as the {@code formatKg} helpers in
 * {@code RecalibrationDetector} and {@code VerdictGenerator}.
 */
@Component
public class WeeklyReportComputer {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportComputer.class);

    /** Current JSONB schema version. Bumped only on a breaking shape change. */
    static final int SCHEMA_VERSION = 1;

    /** Coverage thresholds (v1.1 §A1.2). See class-level javadoc. */
    static final int COVERAGE_GREEN_MIN = 2;
    static final int COVERAGE_AMBER_MIN = 1;

    private final WeekDataBuilder weekDataBuilder;
    private final FindingsGenerator findingsGenerator;
    private final RecalibrationDetector recalibrationDetector;
    private final VerdictGenerator verdictGenerator;
    private final WeeklyReportRepository weeklyReportRepo;
    private final PendingRecalibrationRepository pendingRecalibrationRepo;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    public WeeklyReportComputer(WeekDataBuilder weekDataBuilder,
                                FindingsGenerator findingsGenerator,
                                RecalibrationDetector recalibrationDetector,
                                VerdictGenerator verdictGenerator,
                                WeeklyReportRepository weeklyReportRepo,
                                PendingRecalibrationRepository pendingRecalibrationRepo,
                                ObjectMapper objectMapper,
                                PlatformTransactionManager txManager) {
        this.weekDataBuilder = weekDataBuilder;
        this.findingsGenerator = findingsGenerator;
        this.recalibrationDetector = recalibrationDetector;
        this.verdictGenerator = verdictGenerator;
        this.weeklyReportRepo = weeklyReportRepo;
        this.pendingRecalibrationRepo = pendingRecalibrationRepo;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    /**
     * Compute and persist one weekly report. Idempotent on the
     * {@code weekly_reports} side via the unique constraint +
     * {@code ON CONFLICT DO UPDATE}; append-only on the
     * {@code pending_recalibrations} side (see deviation #1 in the
     * Stage 4 report).
     */
    public void compute(UUID userId, LocalDate weekStart) {
        log.info("WeeklyReportComputer: starting user={} weekStart={}", userId, weekStart);

        // ── Read + compute (no DB tx) ─────────────────────────────────
        WeekData week = weekDataBuilder.build(userId, weekStart);
        List<Finding> findings = findingsGenerator.generate(week);
        List<Recalibration> recalibrations = recalibrationDetector.detect(findings, week);
        String verdict = verdictGenerator.generate(week, findings);
        if (verdict == null) {
            log.warn("WeeklyReportComputer: verdict was null for user={} weekStart={} — report will ship without it",
                    userId, weekStart);
        }

        // ── Serialize JSONB payloads ──────────────────────────────────
        String personalRecordsJson = toJson(buildPersonalRecordsPayload(week));
        String baselinesJson       = toJson(buildBaselinesPayload(week));
        String muscleCoverageJson  = toJson(buildMuscleCoveragePayload(week));
        String findingsJson        = toJson(buildFindingsPayload(findings));
        String recalibrationsJson  = toJson(buildRecalibrationsPayload(recalibrations));

        // ── Write phase (one short DB tx) ─────────────────────────────
        txTemplate.executeWithoutResult(status -> {
            weeklyReportRepo.upsert(
                    userId,
                    week.weekStart(),
                    week.weekEnd(),
                    week.weekNumber(),
                    week.userFirstName(),
                    week.sessionsLogged(),
                    week.sessionsGoal(),
                    normalizeKg(week.totalKgVolume()),
                    week.prCount(),
                    verdict,
                    personalRecordsJson,
                    baselinesJson,
                    muscleCoverageJson,
                    findingsJson,
                    recalibrationsJson,
                    week.isWeekOne(),
                    SCHEMA_VERSION);

            // TODO(dedup): This is append-only. On a re-compute of the
            // same (user, weekStart) — admin retry, stuck-job recovery,
            // or a rare cron double-trigger — we'll create duplicate
            // unapplied rows for any exercise whose recalibration was
            // already pending from the first run. Options for a future
            // fix: (a) DELETE FROM pending_recalibrations WHERE user_id=?
            // AND exercise_id=? AND applied_at IS NULL before INSERT, or
            // (b) add a partial unique index on (user_id, exercise_id)
            // WHERE applied_at IS NULL. Left as simple append for MVP —
            // the plan generator consumes whatever unapplied rows exist
            // at plan-build time, so dedup is cosmetic until scale hurts.
            for (Recalibration r : recalibrations) {
                PendingRecalibration row = new PendingRecalibration();
                row.setUserId(userId);
                row.setExerciseId(r.exerciseId());
                row.setOldTargetKg(normalizeKg(r.oldTargetKg()));
                row.setNewTargetKg(normalizeKg(r.newTargetKg()));
                row.setReason(r.reason());
                pendingRecalibrationRepo.save(row);
            }
        });

        log.info("WeeklyReportComputer: completed user={} weekStart={} findings={} recalibrations={} verdict={}",
                userId, weekStart, findings.size(), recalibrations.size(),
                verdict == null ? "null" : "present");
    }

    // ── JSONB payload builders ───────────────────────────────────────────

    /**
     * Shape: {@code [{exerciseId, newMaxKg, previousMaxKg, reps}]}.
     * Mirrors the {@link WeekData.PrEntry} record fields; kept as an
     * explicit {@code LinkedHashMap} build rather than serializing the
     * record directly so BigDecimal normalization happens inline.
     */
    private List<Map<String, Object>> buildPersonalRecordsPayload(WeekData week) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WeekData.PrEntry pr : week.personalRecords()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("exerciseId", pr.exerciseId());
            entry.put("newMaxKg", normalizeKg(pr.newMaxKg()));
            entry.put("previousMaxKg", normalizeKg(pr.previousMaxKg()));
            entry.put("reps", pr.reps());
            out.add(entry);
        }
        return out;
    }

    /**
     * Week-one baselines snapshot — one entry per exercise the user
     * trained, showing the heaviest set (weight + reps). Used by the
     * Weekly Summary screen as the "starting point" reference the rest
     * of the journey is measured against. Empty outside week one.
     *
     * <p>Built from {@code WeekData.thisWeekTopSets} (which is already
     * the top set per exercise, derived from {@code setsByExercise} in
     * the builder). The directive phrased this as "from
     * week.setsByExercise top set per anchor exercise" — same effect,
     * just reading the pre-computed value.
     */
    private List<Map<String, Object>> buildBaselinesPayload(WeekData week) {
        if (!week.isWeekOne()) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, WeekData.TopSet> e : week.thisWeekTopSets().entrySet()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("exerciseId", e.getKey());
            entry.put("weightKg", normalizeKg(e.getValue().weightKg()));
            entry.put("reps", e.getValue().reps());
            out.add(entry);
        }
        return out;
    }

    /**
     * Shape: {@code [{muscle, sessions, status}]} — always all 8 tiles,
     * in enum declaration order, so the frontend can render a fixed
     * grid without re-ordering.
     */
    private List<Map<String, Object>> buildMuscleCoveragePayload(WeekData week) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WeeklyReportMuscle tile : WeeklyReportMuscle.values()) {
            int sessions = week.sessionsByMuscle().getOrDefault(tile, 0);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("muscle", tile.name());
            entry.put("sessions", sessions);
            entry.put("status", statusForCoverage(sessions));
            out.add(entry);
        }
        return out;
    }

    /**
     * Shape: {@code [{ruleId, severity, weight, title, detail}]}.
     * Mirrors the {@link Finding} record.
     */
    private List<Map<String, Object>> buildFindingsPayload(List<Finding> findings) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Finding f : findings) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ruleId", f.ruleId());
            entry.put("severity", f.severity());
            entry.put("weight", f.weight());
            entry.put("title", f.title());
            entry.put("detail", f.detail());
            out.add(entry);
        }
        return out;
    }

    /**
     * Shape: {@code [{exerciseId, oldTargetKg, newTargetKg, reason}]}.
     * Mirrors the {@link Recalibration} record with BigDecimal
     * normalization applied.
     */
    private List<Map<String, Object>> buildRecalibrationsPayload(List<Recalibration> recalibrations) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Recalibration r : recalibrations) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("exerciseId", r.exerciseId());
            entry.put("oldTargetKg", normalizeKg(r.oldTargetKg()));
            entry.put("newTargetKg", normalizeKg(r.newTargetKg()));
            entry.put("reason", r.reason());
            out.add(entry);
        }
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Map coverage-session-count to a RED/AMBER/GREEN status per v1.1:
     * 0 sessions → RED, 1 session → AMBER, 2+ sessions → GREEN.
     */
    static String statusForCoverage(int sessions) {
        if (sessions >= COVERAGE_GREEN_MIN) return "GREEN";
        if (sessions >= COVERAGE_AMBER_MIN) return "AMBER";
        return "RED";
    }

    /**
     * Normalize a kg value for JSON serialization — strips trailing
     * zeros and forces a non-negative scale so Jackson doesn't emit
     * scientific notation ({@code 1E+2}) for integer-valued decimals.
     * Returns null for null input.
     */
    static BigDecimal normalizeKg(BigDecimal kg) {
        if (kg == null) return null;
        BigDecimal stripped = kg.stripTrailingZeros();
        if (stripped.scale() < 0) stripped = stripped.setScale(0);
        return stripped;
    }

    /**
     * Serialize a payload via the shared ObjectMapper. Wraps any
     * Jackson failure in an {@link IllegalStateException} so the
     * dispatcher treats it as a recoverable job failure (and the
     * exponential backoff / retry budget kicks in).
     */
    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "WeeklyReportComputer: failed to serialize JSONB payload", e);
        }
    }
}
