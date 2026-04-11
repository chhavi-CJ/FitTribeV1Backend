package com.fittribe.api.weeklyreport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import com.fittribe.api.findings.WeeklyReportMuscle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link VerdictGenerator}. Exercises the generator against
 * a {@link MockRestServiceServer} bound to its injected {@link RestTemplate}
 * so we never hit the real OpenAI API. The key invariant under test is
 * that <em>every</em> failure mode (blank key, HTTP error, network error,
 * malformed response, blank content) returns {@code null} without
 * propagating an exception — the weekly report must be able to ship with
 * {@code verdict=null}.
 */
class VerdictGeneratorTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private VerdictGenerator generator;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer   = MockRestServiceServer.bindTo(restTemplate).build();
        generator    = new VerdictGenerator("test-api-key", restTemplate);
    }

    // ── Happy path ────────────────────────────────────────────────────────

    @Test
    void happyPathReturnsSentence() throws Exception {
        String responseBody = mapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of(
                                "role", "assistant",
                                "content", "Full consistency and a chest PR — best week yet.")))));

        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String out = generator.generate(buildWeek(), List.of(
                finding("full_consistency", "GREEN", 55, "Full consistency"),
                finding("multi_pr_week",    "GREEN", 50, "3 PRs this week")));

        assertEquals("Full consistency and a chest PR — best week yet.", out);
        mockServer.verify();
    }

    // ── Failure modes — all must return null without throwing ────────────

    @Test
    void blankApiKeyReturnsNullWithoutCallingApi() {
        // No mockServer.expect(...) — an HTTP call here would fail the test.
        VerdictGenerator blankGen = new VerdictGenerator("", restTemplate);

        String out = blankGen.generate(buildWeek(), List.of());

        assertNull(out);
        mockServer.verify();
    }

    @Test
    void serverErrorReturnsNull() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withServerError());

        String out = generator.generate(buildWeek(), List.of());

        assertNull(out);
    }

    @Test
    void networkErrorReturnsNull() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(request -> {
                    throw new RuntimeException("simulated network error");
                });

        String out = generator.generate(buildWeek(), List.of());

        assertNull(out);
    }

    @Test
    void emptyChoicesReturnsNull() throws Exception {
        String responseBody = mapper.writeValueAsString(Map.of("choices", List.of()));
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String out = generator.generate(buildWeek(), List.of());

        assertNull(out);
    }

    @Test
    void blankContentReturnsNull() throws Exception {
        String responseBody = mapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("role", "assistant", "content", "   ")))));
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String out = generator.generate(buildWeek(), List.of());

        assertNull(out);
    }

    // ── Empty findings list is a valid input (week one, nothing fired) ───

    @Test
    void emptyFindingsStillProducesVerdict() throws Exception {
        String responseBody = mapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of(
                                "role", "assistant",
                                "content", "Three sessions missed this week. Consistency is key.")))));
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String out = generator.generate(buildWeek(), List.of());

        assertEquals("Three sessions missed this week. Consistency is key.", out);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static Finding finding(String ruleId, String severity, int weight, String title) {
        return new Finding(ruleId, severity, weight, title, "detail");
    }

    /**
     * Minimal WeekData — fields the verdict prompt reads are set to realistic
     * values; everything else defaults to empty. Doesn't rely on
     * {@code WeekDataFixture} because that lives in a different package and
     * is package-private.
     */
    private static WeekData buildWeek() {
        Map<WeeklyReportMuscle, Integer> muscles = new EnumMap<>(WeeklyReportMuscle.class);
        for (WeeklyReportMuscle m : WeeklyReportMuscle.values()) muscles.put(m, 0);
        return new WeekData(
                UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"),
                LocalDate.of(2026, 4, 6),
                LocalDate.of(2026, 4, 13),
                2, false, "Asha",
                4, 4, true, BigDecimal.valueOf(2840), 2,
                Collections.unmodifiableList(new ArrayList<>()),
                Collections.unmodifiableMap(new LinkedHashMap<>()),
                Collections.unmodifiableMap(muscles),
                0, 0, 0,
                Collections.unmodifiableMap(new LinkedHashMap<>()),
                Collections.unmodifiableMap(new LinkedHashMap<>()),
                Collections.unmodifiableList(new ArrayList<>()),
                Collections.unmodifiableMap(new LinkedHashMap<>()),
                Collections.unmodifiableMap(new LinkedHashMap<>()));
    }
}
