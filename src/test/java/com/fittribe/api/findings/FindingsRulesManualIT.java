package com.fittribe.api.findings;

import com.fittribe.api.findings.rules.FullConsistencyRule;
import com.fittribe.api.findings.rules.LowAdherenceRule;
import com.fittribe.api.findings.rules.MissingMuscleRule;
import com.fittribe.api.findings.rules.MultiPrWeekRule;
import com.fittribe.api.findings.rules.MultipleTooLightRule;
import com.fittribe.api.findings.rules.PrRegressionRule;
import com.fittribe.api.findings.rules.PushPullImbalanceRule;
import com.fittribe.api.findings.rules.SingleTooLightRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manual harness — builds a {@link WeekData} for a real user/week against the
 * Railway DB and runs every {@link FindingsRule} bean against it, printing
 * which ones fire. Gated the same way as {@code WeekDataBuilderManualIT} so
 * it never runs in normal {@code mvn test}.
 *
 * <h3>How to run</h3>
 * <pre>
 * export $(cat .env | xargs)
 * mvn test \
 *   -Dtest=FindingsRulesManualIT \
 *   -Dfittribe.manualTest=true \
 *   -Dfittribe.testUserId=d60d34cf-cbe2-454c-b89e-6c7340e9b88b \
 *   -Dfittribe.testWeekStart=2026-04-06 \
 *   -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * Not a pass/fail test — only asserts that the builder produced a non-null
 * WeekData so the harness turns green as long as it did not throw. Use the
 * printed output to eyeball which rules fire for the target user.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "fittribe.manualTest", matches = "true")
class FindingsRulesManualIT {

    @Autowired private WeekDataBuilder builder;
    @Autowired private FindingsGenerator findingsGenerator;
    @Autowired private PrRegressionRule prRegressionRule;
    @Autowired private PushPullImbalanceRule pushPullImbalanceRule;
    @Autowired private MissingMuscleRule missingMuscleRule;
    @Autowired private MultipleTooLightRule multipleTooLightRule;
    @Autowired private SingleTooLightRule singleTooLightRule;
    @Autowired private LowAdherenceRule lowAdherenceRule;
    @Autowired private MultiPrWeekRule multiPrWeekRule;
    @Autowired private FullConsistencyRule fullConsistencyRule;

    @Test
    void runAllRulesAgainstRealUser() {
        String userIdStr = System.getProperty("fittribe.testUserId");
        String weekStartStr = System.getProperty("fittribe.testWeekStart");
        if (userIdStr == null || weekStartStr == null) {
            throw new IllegalStateException(
                    "Set -Dfittribe.testUserId=<uuid> and -Dfittribe.testWeekStart=<yyyy-MM-dd>");
        }

        UUID userId = UUID.fromString(userIdStr);
        LocalDate weekStart = LocalDate.parse(weekStartStr);

        WeekData week = builder.build(userId, weekStart);
        if (week == null) throw new AssertionError("WeekDataBuilder returned null");

        List<FindingsRule> rules = List.of(
                prRegressionRule,
                pushPullImbalanceRule,
                missingMuscleRule,
                multipleTooLightRule,
                singleTooLightRule,
                lowAdherenceRule,
                multiPrWeekRule,
                fullConsistencyRule);

        System.out.println("=============== FINDINGS RULES DUMP ===============");
        System.out.println("User:     " + userId);
        System.out.println("Week:     " + weekStart + " → " + week.weekEnd());
        System.out.println("Week #:   " + week.weekNumber()
                + (week.isWeekOne() ? " (week one)" : ""));
        System.out.println("Sessions: " + week.sessionsLogged() + " / " + week.sessionsGoal()
                + (week.weeklyGoalHit() ? " [goal hit]" : ""));
        System.out.println("PRs:      " + week.prCount());
        System.out.println("Push/Pull/Legs sessions: "
                + week.pushSessionCount() + " / "
                + week.pullSessionCount() + " / "
                + week.legsSessionCount());
        System.out.println("Muscle coverage: " + week.sessionsByMuscle());
        System.out.println("--------------------------------------------------");

        List<Finding> allFindings = new ArrayList<>();
        for (FindingsRule rule : rules) {
            List<Finding> fired = rule.evaluate(week);
            if (fired.isEmpty()) {
                System.out.println("  [ ] " + rule.getRuleId() + " — did not fire");
            } else {
                for (Finding f : fired) {
                    System.out.println("  [X] " + f.ruleId() + " (" + f.severity()
                            + ", w=" + f.weight() + ")");
                    System.out.println("       title : " + f.title());
                    System.out.println("       detail: " + f.detail());
                }
                allFindings.addAll(fired);
            }
        }
        System.out.println("--------------------------------------------------");
        System.out.println("TOTAL FINDINGS: " + allFindings.size());
        System.out.println("==================================================");

        List<Finding> top = findingsGenerator.generate(week);
        System.out.println("=============== FINDINGS GENERATOR — FINAL TOP " + top.size() + " ===============");
        int i = 1;
        for (Finding f : top) {
            System.out.println("  " + i++ + ". " + f.ruleId()
                    + " (" + f.severity() + ", w=" + f.weight() + ")");
            System.out.println("      title : " + f.title());
            System.out.println("      detail: " + f.detail());
        }
        System.out.println("==================================================");
    }
}
