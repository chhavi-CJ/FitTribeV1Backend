package com.fittribe.api.controller;

import com.fittribe.api.matching.BatchSummary;
import com.fittribe.api.matching.MatchingService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link MatchingAdminController}.
 *
 * <p>Standalone MockMvc — the controller is wired by hand with the
 * configured admin key passed to its constructor, so each test can pin
 * a different key without {@code @TestPropertySource}. Standalone setup
 * also bypasses the Spring Security filter chain (covered separately by
 * the SecurityConfig allow-list); these tests exercise the controller's
 * own deny-by-default key check.
 *
 * <p>{@link MatchingService} is a concrete class; following the
 * established codebase pattern for concrete collaborators on this JDK
 * ({@code AdminJobTriggerControllerTest}), it's stubbed with a
 * hand-rolled fake rather than a Mockito mock.
 */
class MatchingAdminControllerTest {

    private static final String CONFIGURED_KEY = "expected-key";

    /** Hand-rolled fake — records call count, returns a canned summary. */
    static class FakeMatchingService extends MatchingService {
        int calls = 0;
        BatchSummary toReturn = BatchSummary.empty();

        FakeMatchingService() {
            super(null, null, null);
        }

        @Override
        public BatchSummary runBatch() {
            calls++;
            return toReturn;
        }
    }

    private MockMvc mvc(MatchingAdminController controller) {
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void rejects_request_with_no_admin_key_header() throws Exception {
        FakeMatchingService svc = new FakeMatchingService();
        MockMvc mvc = mvc(new MatchingAdminController(svc, CONFIGURED_KEY));

        mvc.perform(post("/api/admin/matching/run-batch"))
                .andExpect(status().isForbidden());

        assertEquals(0, svc.calls);
    }

    @Test
    void rejects_request_with_wrong_admin_key() throws Exception {
        FakeMatchingService svc = new FakeMatchingService();
        MockMvc mvc = mvc(new MatchingAdminController(svc, CONFIGURED_KEY));

        mvc.perform(post("/api/admin/matching/run-batch")
                        .header("X-Admin-Key", "wrong-key"))
                .andExpect(status().isForbidden());

        assertEquals(0, svc.calls);
    }

    @Test
    void rejects_request_when_admin_key_unconfigured() throws Exception {
        FakeMatchingService svc = new FakeMatchingService();
        // Empty configured key -> deny by default even if a header is sent.
        MockMvc mvc = mvc(new MatchingAdminController(svc, ""));

        mvc.perform(post("/api/admin/matching/run-batch")
                        .header("X-Admin-Key", "anything"))
                .andExpect(status().isForbidden());

        assertEquals(0, svc.calls);
    }

    @Test
    void accepts_request_with_correct_admin_key() throws Exception {
        FakeMatchingService svc = new FakeMatchingService();
        svc.toReturn = new BatchSummary(2, 8, 0, List.of());
        MockMvc mvc = mvc(new MatchingAdminController(svc, CONFIGURED_KEY));

        mvc.perform(post("/api/admin/matching/run-batch")
                        .header("X-Admin-Key", CONFIGURED_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupsFormed").value(2))
                .andExpect(jsonPath("$.usersMatched").value(8))
                .andExpect(jsonPath("$.usersRemaining").value(0))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isEmpty());

        assertEquals(1, svc.calls);
    }

    @Test
    void returns_batch_summary_with_errors_when_present() throws Exception {
        FakeMatchingService svc = new FakeMatchingService();
        String err = "Failed to persist group of 4 (dominant=ANCHOR, quality=8): db down";
        svc.toReturn = new BatchSummary(1, 4, 3, List.of(err));
        MockMvc mvc = mvc(new MatchingAdminController(svc, CONFIGURED_KEY));

        mvc.perform(post("/api/admin/matching/run-batch")
                        .header("X-Admin-Key", CONFIGURED_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupsFormed").value(1))
                .andExpect(jsonPath("$.usersMatched").value(4))
                .andExpect(jsonPath("$.usersRemaining").value(3))
                .andExpect(jsonPath("$.errors[0]").value(containsString("db down")))
                .andExpect(jsonPath("$.errors[0]").value(containsString("dominant=ANCHOR")));

        assertEquals(1, svc.calls);
    }
}
