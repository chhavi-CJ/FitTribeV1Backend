package com.fittribe.api.matching;

import com.fittribe.api.entity.Archetype;
import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.entity.Group;
import com.fittribe.api.entity.MatchingProfile;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.UserMatchingStatus;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.matching.dto.MatchingDtos.*;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.GroupRepository;
import com.fittribe.api.repository.MatchingProfileRepository;
import com.fittribe.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User-facing operations on Conscious Matching state for one user.
 *
 * <p>State transitions implemented:
 * <pre>
 *                    submit-quiz       opt-out          rejoin
 *   current state   ─────────────────────────────────────────────
 *   NONE            → QUEUED           → OPTED_OUT      409
 *   QUEUED          → updates profile  → OPTED_OUT      409
 *   MATCHED         → 409              → OPTED_OUT      409
 *   OPTED_OUT       → 409              → no-op          → QUEUED
 * </pre>
 *
 * <p>Opt-out from MATCHED keeps group membership intact — flipping
 * status only signals "don't include me in future batches", not "leave
 * my current group" (locked design decision).
 *
 * <p><b>Adapted from Step 5 spec (in-scope, engine untouched):</b> the
 * spec assumed {@code ArchetypeClassifier.classify} returned an
 * {@link Archetype} and a public {@code scoreOf(MatchingQuestion,...)}.
 * The locked Step-3 engine actually returns a {@code Result} record
 * carrying the archetype plus per-question scores, and there is no
 * {@code ApiException.conflict} factory. This class uses the real
 * {@code Result} API and constructs 409s via the public
 * {@link ApiException} constructor — same behaviour the spec intended.
 */
@Service
public class MatchingApiService {

    private static final Logger log = LoggerFactory.getLogger(MatchingApiService.class);

    private final UserRepository userRepo;
    private final MatchingProfileRepository profileRepo;
    private final GroupMemberRepository groupMemberRepo;
    private final GroupRepository groupRepo;

    @Autowired
    public MatchingApiService(UserRepository userRepo,
                              MatchingProfileRepository profileRepo,
                              GroupMemberRepository groupMemberRepo,
                              GroupRepository groupRepo) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.groupMemberRepo = groupMemberRepo;
        this.groupRepo = groupRepo;
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "MATCHING_CONFLICT", message);
    }

    /**
     * POST /api/v1/matching/submit-quiz
     *
     * - NONE → upserts matching_profile, flips status to QUEUED
     * - QUEUED → updates matching_profile, status stays QUEUED (don't
     *   reset queue-position by changing the row's created_at, since
     *   we don't expose position anyway it's mostly principle —
     *   re-submitting shouldn't feel like a fresh start)
     * - MATCHED → 409 "already matched — leave your group first"
     * - OPTED_OUT → 409 "you opted out — call /rejoin first"
     */
    @Transactional
    public SubmitQuizResponse submitQuiz(UUID userId, SubmitQuizRequest req) {
        User user = mustGetUser(userId);

        switch (user.getUserMatchingStatus()) {
            case MATCHED -> throw conflict(
                    "Already matched. Leave your current group before re-taking the quiz.");
            case OPTED_OUT -> throw conflict(
                    "You've opted out. Call /api/v1/matching/rejoin first.");
            case NONE, QUEUED -> { /* fall through */ }
        }

        // Classify answers (locked pure-logic). The engine returns a
        // Result record carrying both the archetype and per-question
        // score contributions.
        ArchetypeClassifier.Result result = ArchetypeClassifier.classify(
                req.q1(), req.q2(), req.q3(), req.q4());
        Archetype archetype = result.archetype();

        // Upsert profile.
        Optional<MatchingProfile> existing = profileRepo.findByUserId(userId);
        MatchingProfile profile = existing.orElseGet(MatchingProfile::new);
        if (existing.isEmpty()) {
            profile.setUserId(userId);
        }
        profile.setArchetype(archetype);
        profile.setAnswerQ1(req.q1().name());
        profile.setAnswerQ2(req.q2().name());
        profile.setAnswerQ3(req.q3().name());
        profile.setAnswerQ4(req.q4().name());
        profile.setScoreQ1(result.scoreQ1());
        profile.setScoreQ2(result.scoreQ2());
        profile.setScoreQ3(result.scoreQ3());
        profile.setScoreQ4(result.scoreQ4());
        // PartnerGenderPref isn't part of the quiz in this iteration —
        // defaults to ANY from the entity. Future quiz screen may ask.
        profileRepo.save(profile);

        // Flip status if first submission (NONE → QUEUED).
        if (user.getUserMatchingStatus() == UserMatchingStatus.NONE) {
            user.setUserMatchingStatus(UserMatchingStatus.QUEUED);
            userRepo.save(user);
            log.info("User {} submitted quiz — archetype={}, status NONE → QUEUED",
                    userId, archetype);
        } else {
            log.info("User {} re-submitted quiz — archetype updated to {}, status stays QUEUED",
                    userId, archetype);
        }

        return new SubmitQuizResponse(archetype, user.getUserMatchingStatus());
    }

    /**
     * GET /api/v1/matching/me
     *
     * Returns the user's current matching state. Single endpoint covers
     * all four status values via nullable fields.
     */
    @Transactional(readOnly = true)
    public MeResponse getMyStatus(UUID userId) {
        User user = mustGetUser(userId);
        UserMatchingStatus status = user.getUserMatchingStatus();

        if (status == UserMatchingStatus.NONE) {
            return new MeResponse(status, null, null);
        }

        // Profile may not exist if status got flipped via SQL — defensive.
        Optional<MatchingProfile> profile = profileRepo.findByUserId(userId);
        Archetype archetype = profile.map(MatchingProfile::getArchetype).orElse(null);

        if (status == UserMatchingStatus.MATCHED) {
            MatchedGroup matchedGroup = loadMatchedGroup(userId);
            return new MeResponse(status, archetype, matchedGroup);
        }

        // QUEUED or OPTED_OUT: status + archetype.
        return new MeResponse(status, archetype, null);
    }

    /**
     * POST /api/v1/matching/opt-out
     *
     * Idempotent — calling on an already-OPTED_OUT user is a no-op.
     * For MATCHED users, group membership is preserved (only status
     * flips; "don't re-match me", not "leave my group").
     */
    @Transactional
    public StatusResponse optOut(UUID userId) {
        User user = mustGetUser(userId);
        if (user.getUserMatchingStatus() == UserMatchingStatus.OPTED_OUT) {
            return new StatusResponse(UserMatchingStatus.OPTED_OUT);
        }
        UserMatchingStatus prior = user.getUserMatchingStatus();
        user.setUserMatchingStatus(UserMatchingStatus.OPTED_OUT);
        userRepo.save(user);
        log.info("User {} opted out of matching (was {})", userId, prior);
        return new StatusResponse(UserMatchingStatus.OPTED_OUT);
    }

    /**
     * POST /api/v1/matching/rejoin
     *
     * OPTED_OUT → QUEUED. Requires an existing matching_profile (user
     * must have taken the quiz at least once). Returns 409 if not
     * OPTED_OUT or no profile exists.
     */
    @Transactional
    public StatusResponse rejoin(UUID userId) {
        User user = mustGetUser(userId);
        if (user.getUserMatchingStatus() != UserMatchingStatus.OPTED_OUT) {
            throw conflict(
                    "Can only rejoin from OPTED_OUT state. Current state: "
                    + user.getUserMatchingStatus());
        }
        if (profileRepo.findByUserId(userId).isEmpty()) {
            // Edge case — somehow opted out without a profile. Shouldn't
            // happen, but be honest about it.
            throw conflict(
                    "No matching profile found. Submit the quiz to join matching.");
        }
        user.setUserMatchingStatus(UserMatchingStatus.QUEUED);
        userRepo.save(user);
        log.info("User {} rejoined matching (OPTED_OUT → QUEUED)", userId);
        return new StatusResponse(UserMatchingStatus.QUEUED);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private User mustGetUser(UUID userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User"));
    }

    /**
     * Load the user's matched group with full member details for
     * rendering the "your crew" reveal screen. Returns null if
     * somehow the user is MATCHED but has no group_members row.
     */
    private MatchedGroup loadMatchedGroup(UUID userId) {
        // Find the (most recent if multiple, defensive) MATCHED group.
        // V71 added created_via='MATCHED' to groups; we use that to
        // pick the one this user landed in.
        List<GroupMember> memberships = groupMemberRepo.findByUserId(userId);
        if (memberships.isEmpty()) return null;

        // Prefer the group with created_via='MATCHED'. If multiple
        // (shouldn't happen), pick the most recent.
        Group matchedGroup = null;
        for (GroupMember m : memberships) {
            Optional<Group> g = groupRepo.findById(m.getGroupId());
            if (g.isPresent() && "MATCHED".equals(g.get().getCreatedVia())) {
                if (matchedGroup == null || g.get().getCreatedAt()
                        .isAfter(matchedGroup.getCreatedAt())) {
                    matchedGroup = g.get();
                }
            }
        }
        if (matchedGroup == null) return null;

        // Load all members of that group with their profile + user data.
        List<GroupMember> allMembers = groupMemberRepo.findByGroupId(matchedGroup.getId());
        List<UUID> memberUserIds = allMembers.stream()
                .map(GroupMember::getUserId).toList();

        Map<UUID, User> usersById = userRepo.findAllById(memberUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, MatchingProfile> profilesById = profileRepo
                .findByUserIdIn(memberUserIds).stream()
                .collect(Collectors.toMap(MatchingProfile::getUserId, p -> p));

        List<GroupMemberDto> dtos = new ArrayList<>(allMembers.size());
        for (GroupMember gm : allMembers) {
            User u = usersById.get(gm.getUserId());
            MatchingProfile p = profilesById.get(gm.getUserId());
            if (u == null) continue;  // defensive
            dtos.add(new GroupMemberDto(
                    u.getId(),
                    u.getDisplayName() != null ? u.getDisplayName() : "Member",
                    p != null ? p.getArchetype() : null));
        }

        return new MatchedGroup(matchedGroup.getId(), matchedGroup.getName(), dtos);
    }
}
