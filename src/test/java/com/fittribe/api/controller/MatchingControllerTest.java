package com.fittribe.api.controller;

import com.fittribe.api.entity.Archetype;
import com.fittribe.api.entity.UserMatchingStatus;
import com.fittribe.api.matching.MatchingApiService;
import com.fittribe.api.matching.dto.MatchingDtos.MeResponse;
import com.fittribe.api.matching.dto.MatchingDtos.StatusResponse;
import com.fittribe.api.matching.dto.MatchingDtos.SubmitQuizRequest;
import com.fittribe.api.matching.dto.MatchingDtos.SubmitQuizResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone-MockMvc tests for {@link MatchingController} — mirrors the
 * {@code MatchingAdminControllerTest} pattern. {@link MatchingApiService}
 * is concrete; following the codebase pattern for concrete collaborators
 * on this JDK it's stubbed with a hand-rolled fake (call-recording).
 *
 * <p>The {@code Authentication} principal is supplied via the request's
 * {@code .principal(...)} (resolved by Spring MVC's principal argument
 * resolver), holding the user id — same value the JWT filter would set.
 */
class MatchingControllerTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Hand-rolled fake — records calls + args, returns canned values. */
    static class FakeMatchingApiService extends MatchingApiService {
        int submitCalls = 0, meCalls = 0, optOutCalls = 0, rejoinCalls = 0;
        UUID lastUserId;
        SubmitQuizRequest lastSubmit;
        SubmitQuizResponse submitReturn = new SubmitQuizResponse(Archetype.ANCHOR, UserMatchingStatus.QUEUED);
        MeResponse meReturn = new MeResponse(UserMatchingStatus.QUEUED, Archetype.ANCHOR, null);

        FakeMatchingApiService() {
            super(null, null, null, null);
        }

        @Override
        public SubmitQuizResponse submitQuiz(UUID userId, SubmitQuizRequest req) {
            submitCalls++;
            lastUserId = userId;
            lastSubmit = req;
            return submitReturn;
        }

        @Override
        public MeResponse getMyStatus(UUID userId) {
            meCalls++;
            lastUserId = userId;
            return meReturn;
        }

        @Override
        public StatusResponse optOut(UUID userId) {
            optOutCalls++;
            lastUserId = userId;
            return new StatusResponse(UserMatchingStatus.OPTED_OUT);
        }

        @Override
        public StatusResponse rejoin(UUID userId) {
            rejoinCalls++;
            lastUserId = userId;
            return new StatusResponse(UserMatchingStatus.QUEUED);
        }
    }

    private final FakeMatchingApiService svc = new FakeMatchingApiService();
    private final MockMvc mvc =
            MockMvcBuilders.standaloneSetup(new MatchingController(svc)).build();

    private Authentication auth() {
        Authentication a = mock(Authentication.class);
        when(a.getPrincipal()).thenReturn(USER_ID);
        return a;
    }

    private static final String VALID_BODY = """
            {"q1":"LIFE_GOT_BUSY","q2":"CELEBRATE_WINS",
             "q3":"SHOW_UP_ON_BAD_DAYS","q4":"SHARED_PROGRESS_TRACKING"}""";

    // ── Tests ────────────────────────────────────────────────────────

    @Test
    void submit_quiz_endpoint_delegates_to_service() throws Exception {
        mvc.perform(post("/api/v1/matching/submit-quiz")
                        .principal(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archetype").value("ANCHOR"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.error").doesNotExist());

        assertEquals(1, svc.submitCalls);
        assertEquals(USER_ID, svc.lastUserId);
        assertEquals("LIFE_GOT_BUSY", svc.lastSubmit.q1().name());
        assertEquals("CELEBRATE_WINS", svc.lastSubmit.q2().name());
        assertEquals("SHOW_UP_ON_BAD_DAYS", svc.lastSubmit.q3().name());
        assertEquals("SHARED_PROGRESS_TRACKING", svc.lastSubmit.q4().name());
    }

    @Test
    void submit_quiz_rejects_bad_enum_in_body() throws Exception {
        String badBody = """
                {"q1":"INVALID_ANSWER","q2":"CELEBRATE_WINS",
                 "q3":"SHOW_UP_ON_BAD_DAYS","q4":"SHARED_PROGRESS_TRACKING"}""";

        mvc.perform(post("/api/v1/matching/submit-quiz")
                        .principal(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest());

        assertEquals(0, svc.submitCalls);
    }

    @Test
    void submit_quiz_rejects_missing_field() throws Exception {
        // DEVIATION FROM SPEC (flagged): SubmitQuizRequest is an
        // un-validated record (spec locked it; no @Valid/@NotNull). A
        // missing field therefore binds as null — the controller does NOT
        // 400 it; presence is enforced downstream by ArchetypeClassifier
        // in the service layer. This test pins the REAL contract: the
        // request binds with q3=null and the controller still delegates.
        String missingQ3 = """
                {"q1":"LIFE_GOT_BUSY","q2":"CELEBRATE_WINS",
                 "q4":"SHARED_PROGRESS_TRACKING"}""";

        mvc.perform(post("/api/v1/matching/submit-quiz")
                        .principal(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingQ3))
                .andExpect(status().isOk());

        assertEquals(1, svc.submitCalls);
        assertNotNull(svc.lastSubmit);
        assertNull(svc.lastSubmit.q3(), "missing q3 binds as null (no controller validation)");
    }

    @Test
    void me_endpoint_returns_service_response() throws Exception {
        svc.meReturn = new MeResponse(UserMatchingStatus.QUEUED, Archetype.GRINDER, null);

        mvc.perform(get("/api/v1/matching/me").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.archetype").value("GRINDER"))
                .andExpect(jsonPath("$.data.matchedGroup").doesNotExist());

        assertEquals(1, svc.meCalls);
        assertEquals(USER_ID, svc.lastUserId);
    }

    @Test
    void opt_out_endpoint_delegates_to_service() throws Exception {
        mvc.perform(post("/api/v1/matching/opt-out").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPTED_OUT"));

        assertEquals(1, svc.optOutCalls);
        assertEquals(USER_ID, svc.lastUserId);
    }

    @Test
    void rejoin_endpoint_delegates_to_service() throws Exception {
        mvc.perform(post("/api/v1/matching/rejoin").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));

        assertEquals(1, svc.rejoinCalls);
        assertEquals(USER_ID, svc.lastUserId);
    }
}
