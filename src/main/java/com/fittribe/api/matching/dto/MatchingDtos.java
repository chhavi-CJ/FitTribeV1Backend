package com.fittribe.api.matching.dto;

import com.fittribe.api.entity.Archetype;
import com.fittribe.api.entity.UserMatchingStatus;
import com.fittribe.api.matching.MatchingAnswer;

import java.util.List;
import java.util.UUID;

/**
 * DTOs for the user-facing Conscious Matching API.
 *
 * Records are immutable and serialised to JSON by Jackson using the
 * record's accessor names — predictable shape for the frontend.
 */
public final class MatchingDtos {

    private MatchingDtos() {}

    /**
     * Request body for POST /api/v1/matching/submit-quiz.
     * Jackson rejects unknown enum values with 400 automatically.
     */
    public record SubmitQuizRequest(
            MatchingAnswer q1,
            MatchingAnswer q2,
            MatchingAnswer q3,
            MatchingAnswer q4
    ) {}

    /** Response body for POST /api/v1/matching/submit-quiz. */
    public record SubmitQuizResponse(
            Archetype archetype,
            UserMatchingStatus status
    ) {}

    /**
     * Response body for GET /api/v1/matching/me. All fields after
     * status are nullable depending on state. Frontend keys off
     * {@code status} and populates from optional fields.
     *
     * - NONE        → status only
     * - QUEUED      → status + archetype
     * - OPTED_OUT   → status + archetype (preserved from prior submit)
     * - MATCHED     → status + archetype + matchedGroup
     */
    public record MeResponse(
            UserMatchingStatus status,
            Archetype archetype,        // nullable
            MatchedGroup matchedGroup   // nullable
    ) {}

    /** Embedded in MeResponse when status == MATCHED. */
    public record MatchedGroup(
            UUID id,
            String name,
            List<GroupMemberDto> members
    ) {}

    /** A single group member, as seen from the my-group view. */
    public record GroupMemberDto(
            UUID userId,
            String displayName,
            Archetype archetype
    ) {}

    /** Response body for POST /api/v1/matching/opt-out and /rejoin. */
    public record StatusResponse(
            UserMatchingStatus status
    ) {}
}
