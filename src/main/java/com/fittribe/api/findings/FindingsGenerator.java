package com.fittribe.api.findings;

import com.fittribe.api.config.FindingsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs every registered {@link FindingsRule} against a {@link WeekData}
 * snapshot and picks the top 4 findings for the weekly report (Wynners A2.2).
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>Evaluate every rule and flatten the results into a single list.</li>
 *   <li>Drop anything whose rule is {@link FindingsRule#isWeekOneEligible()
 *       not week-one eligible} when {@link WeekData#isWeekOne()} is true.</li>
 *   <li>Sort by severity (RED &gt; AMBER &gt; GREEN) then weight desc.</li>
 *   <li>Trim to 4.</li>
 *   <li>Always-one-green backfill: if the trimmed list has fewer than 4
 *       findings AND contains zero GREEN, append the first eligible
 *       {@link FindingsConfig.FallbackGreen} so the report never shows an
 *       all-red wall of doom.</li>
 * </ol>
 *
 * <h3>Backfill semantics (clarification of the v1.0 spec)</h3>
 * The v1.0 doc described the green backfill as "drop the 4th finding and
 * inject a fallback green". In practice that conflicts with the
 * non-negotiable test "5 RED → 4 RED with no injection" (we keep the four
 * highest-priority reds even if none are green). The rule we actually
 * implement is: <em>pad up to 4 with a fallback green only when there is
 * room</em>. If the top 4 is already full, we leave it alone; if it is
 * short AND has no green, we append a fallback. This satisfies every
 * behavioural test in A2.2 and is the less surprising outcome for users.
 *
 * <h3>Fallback requirement expressions</h3>
 * The YAML uses three tiny expressions:
 * <ul>
 *   <li>{@code "always"} — always matches.</li>
 *   <li>{@code "sessions >= 1"} — user logged at least one session.</li>
 *   <li>{@code "kg > 0"} — user moved any weight this week.</li>
 * </ul>
 * Anything else is logged and treated as non-matching (fail-safe). This is
 * a deliberately dumb DSL — if the set grows we'll replace it with a real
 * expression library rather than accreting string matches.
 */
@Component
public class FindingsGenerator {

    private static final Logger log = LoggerFactory.getLogger(FindingsGenerator.class);

    static final int MAX_FINDINGS = 4;

    private final List<FindingsRule> rules;
    private final FindingsConfig config;

    public FindingsGenerator(List<FindingsRule> rules, FindingsConfig config) {
        this.rules = rules;
        this.config = config;
    }

    /** Produce the final (up to 4) findings list for a week. */
    public List<Finding> generate(WeekData week) {
        List<Finding> fired = new ArrayList<>();
        for (FindingsRule rule : rules) {
            if (week.isWeekOne() && !rule.isWeekOneEligible()) continue;
            fired.addAll(rule.evaluate(week));
        }

        fired.sort(FINDING_ORDER);

        List<Finding> top = fired.size() > MAX_FINDINGS
                ? new ArrayList<>(fired.subList(0, MAX_FINDINGS))
                : new ArrayList<>(fired);

        if (top.size() < MAX_FINDINGS && !containsGreen(top)) {
            Finding fallback = pickFallbackGreen(week);
            if (fallback != null) top.add(fallback);
        }

        return top;
    }

    // ── Sorting ───────────────────────────────────────────────────────────

    /** RED &gt; AMBER &gt; GREEN, then higher weight first. */
    static final Comparator<Finding> FINDING_ORDER =
            Comparator.comparingInt((Finding f) -> -severityRank(f.severity()))
                    .thenComparingInt(f -> -f.weight());

    private static int severityRank(String severity) {
        if (severity == null) return 0;
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "RED" -> 3;
            case "AMBER" -> 2;
            case "GREEN" -> 1;
            default -> 0;
        };
    }

    private static boolean containsGreen(List<Finding> findings) {
        for (Finding f : findings) {
            if ("GREEN".equalsIgnoreCase(f.severity())) return true;
        }
        return false;
    }

    // ── Fallback green ────────────────────────────────────────────────────

    private Finding pickFallbackGreen(WeekData week) {
        List<FindingsConfig.FallbackGreen> candidates = config.getFallbackGreens();
        if (candidates == null || candidates.isEmpty()) {
            log.warn("No fallback_greens configured — report may have no GREEN finding");
            return null;
        }
        for (FindingsConfig.FallbackGreen candidate : candidates) {
            if (matchesRequirement(candidate.getRequires(), week)) {
                return buildFallback(candidate, week);
            }
        }
        log.warn("No fallback_green matched week (sessions={}, kg={}) — none of {} candidates applied",
                week.sessionsLogged(), week.totalKgVolume(), candidates.size());
        return null;
    }

    private boolean matchesRequirement(String requires, WeekData week) {
        if (requires == null) return false;
        String r = requires.trim().toLowerCase(Locale.ROOT);
        return switch (r) {
            case "always" -> true;
            case "sessions >= 1" -> week.sessionsLogged() >= 1;
            case "kg > 0" -> week.totalKgVolume() != null
                    && week.totalKgVolume().signum() > 0;
            default -> {
                log.warn("Unknown fallback_green requires expression: '{}' — treating as no-match", requires);
                yield false;
            }
        };
    }

    private Finding buildFallback(FindingsConfig.FallbackGreen template, WeekData week) {
        Map<String, String> vars = new HashMap<>();
        vars.put("sessions", String.valueOf(week.sessionsLogged()));
        vars.put("kg", formatKg(week.totalKgVolume()));
        String title = interpolate(template.getTitle(), vars);
        String detail = interpolate(template.getDetail(), vars);
        return new Finding("fallback_green", "GREEN", 0, title, detail);
    }

    private static String interpolate(String template, Map<String, String> vars) {
        if (template == null) return null;
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    private static String formatKg(BigDecimal kg) {
        if (kg == null) return "0";
        BigDecimal stripped = kg.stripTrailingZeros();
        if (stripped.scale() < 0) stripped = stripped.setScale(0);
        return stripped.toPlainString();
    }
}
