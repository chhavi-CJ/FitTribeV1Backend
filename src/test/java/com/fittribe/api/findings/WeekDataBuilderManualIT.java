package com.fittribe.api.findings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Manual integration test — builds a {@link WeekData} against the live
 * Railway database for a specific user and week, then logs the result as
 * JSON. Gated by {@code -Dfittribe.manualTest=true} so it never runs in
 * normal {@code mvn test}.
 *
 * <h3>How to run</h3>
 * <pre>
 * export $(cat .env | xargs)
 * mvn test \
 *   -Dtest=WeekDataBuilderManualIT \
 *   -Dfittribe.manualTest=true \
 *   -Dfittribe.testUserId=d60d34cf-cbe2-454c-b89e-6c7340e9b88b \
 *   -Dfittribe.testWeekStart=2026-04-06 \
 *   -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * System properties:
 * <ul>
 *   <li>{@code fittribe.testUserId} (required) — UUID of the target user</li>
 *   <li>{@code fittribe.testWeekStart} (required) — ISO date of the UTC Monday
 *       that starts the week window</li>
 * </ul>
 *
 * This is not a pass/fail test — it's a harness for inspecting the
 * builder's output against real data. It only asserts that the returned
 * {@link WeekData} is non-null, so the test turns green as long as the
 * builder didn't throw.
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "fittribe.manualTest", matches = "true")
class WeekDataBuilderManualIT {

    @Autowired
    private WeekDataBuilder builder;

    @Test
    void buildWeekDataForRealUser() throws Exception {
        String userIdStr = System.getProperty("fittribe.testUserId");
        String weekStartStr = System.getProperty("fittribe.testWeekStart");
        if (userIdStr == null || weekStartStr == null) {
            throw new IllegalStateException(
                    "Set -Dfittribe.testUserId=<uuid> and -Dfittribe.testWeekStart=<yyyy-MM-dd>");
        }

        UUID userId = UUID.fromString(userIdStr);
        LocalDate weekStart = LocalDate.parse(weekStartStr);

        WeekData week = builder.build(userId, weekStart);

        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT);

        String json = mapper.writeValueAsString(week);
        System.out.println("=============== WEEK DATA DUMP ===============");
        System.out.println(json);
        System.out.println("================================================");

        if (week == null) {
            throw new AssertionError("WeekDataBuilder returned null");
        }
    }
}
