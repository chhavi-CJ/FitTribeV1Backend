package com.fittribe.api.weeklyreport;

import com.fittribe.api.findings.Finding;
import com.fittribe.api.findings.WeekData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates a one-sentence AI verdict for a weekly training report
 * (Wynners A3.3).
 *
 * <p>This is the only AI call in Workstream A. It is wrapped so that any
 * failure — network error, timeout, blank API key, unexpected response
 * shape — results in {@code null} being returned instead of propagating
 * the exception. The rest of the weekly report must be able to ship
 * without a verdict; a missing AI sentence is not a blocker.
 *
 * <h3>Constraints (from Wynners A3.3)</h3>
 * <ul>
 *   <li>Model: {@code gpt-4o}</li>
 *   <li>Max output tokens: 50 (one sentence)</li>
 *   <li>Timeout: 10 seconds (both connect and read)</li>
 *   <li>On any failure: return {@code null}, log the error, do not throw</li>
 * </ul>
 *
 * <h3>Why a dedicated RestTemplate</h3>
 * The existing {@link com.fittribe.api.service.AiService} uses a plain
 * {@code new RestTemplate()} with no timeouts. Reusing that would violate
 * the 10-second requirement. Extracting a shared {@code OpenAiClient}
 * bean is the right long-term move but out of scope for A3.3, so this
 * class owns its own {@link RestTemplate} with a
 * {@link SimpleClientHttpRequestFactory} configured for 10s connect +
 * 10s read.
 */
@Component
public class VerdictGenerator {

    private static final Logger log = LoggerFactory.getLogger(VerdictGenerator.class);

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o";
    private static final int MAX_TOKENS = 50;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String SYSTEM_PROMPT =
            "You are a no-nonsense fitness coach writing one sentence " +
            "for a weekly training report.";

    private static final String USER_PROMPT_TEMPLATE = """
            User trained %s/%s sessions this week, total %s kg.

            Top finding: %s
            Other findings: %s

            Write ONE sentence (max 20 words) that honestly summarizes
            the week. Direct, not preachy. Mention the highlight and the
            weakness in the same sentence if both exist.

            Examples:
            - "Strong push day PRs, but legs skipped again — pull is also falling behind."
            - "Full consistency and a chest PR — best week yet."
            - "Three sessions missed this week. Consistency is the single biggest predictor of progress."

            Output only the sentence. No quotes, no preamble.""";

    private final String apiKey;
    private final RestTemplate restTemplate;

    @Autowired
    public VerdictGenerator(@Value("${openai.api-key:}") String apiKey) {
        this(apiKey, defaultRestTemplate());
    }

    /** Package-private test seam — lets tests inject a mockable RestTemplate. */
    VerdictGenerator(String apiKey, RestTemplate restTemplate) {
        this.apiKey = apiKey;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());
        return new RestTemplate(factory);
    }

    /**
     * Produce a one-sentence verdict for the given week and findings.
     * Returns {@code null} on any failure — the caller must tolerate a
     * missing verdict (weekly report still ships, {@code verdict} column
     * just stays null).
     */
    public String generate(WeekData week, List<Finding> findings) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("VerdictGenerator: openai.api-key is blank — skipping AI call, returning null");
            return null;
        }
        try {
            String userPrompt = buildUserPrompt(week, findings);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", MODEL);
            body.put("max_tokens", MAX_TOKENS);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user",   "content", userPrompt)));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    OPENAI_URL, new HttpEntity<>(body, headers), Map.class);

            String content = extractContent(response);
            if (content == null) {
                log.warn("VerdictGenerator: OpenAI returned no usable content for user {} week {}",
                        week.userId(), week.weekStart());
            }
            return content;
        } catch (Exception e) {
            log.error("VerdictGenerator: AI call failed for user {} week {} — returning null",
                    week.userId(), week.weekStart(), e);
            return null;
        }
    }

    // ── Response parsing ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static String extractContent(Map<String, Object> response) {
        if (response == null) return null;
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) return null;
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) return null;
        Object content = message.get("content");
        if (!(content instanceof String s)) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ── Prompt building ───────────────────────────────────────────────────

    private String buildUserPrompt(WeekData week, List<Finding> findings) {
        List<Finding> safe = findings == null ? List.of() : findings;
        String topTitle = safe.isEmpty() ? "none" : safe.get(0).title();
        String otherTitles = safe.size() <= 1
                ? "none"
                : safe.stream().skip(1).map(Finding::title).collect(Collectors.joining("; "));

        return USER_PROMPT_TEMPLATE.formatted(
                week.sessionsLogged(),
                week.sessionsGoal(),
                formatKg(week.totalKgVolume()),
                topTitle,
                otherTitles);
    }

    private static String formatKg(BigDecimal kg) {
        if (kg == null) return "0";
        BigDecimal stripped = kg.stripTrailingZeros();
        if (stripped.scale() < 0) stripped = stripped.setScale(0);
        return stripped.toPlainString();
    }
}
