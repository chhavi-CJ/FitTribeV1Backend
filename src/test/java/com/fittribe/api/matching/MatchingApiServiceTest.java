package com.fittribe.api.matching;

import com.fittribe.api.entity.Archetype;
import com.fittribe.api.entity.Group;
import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.entity.MatchingProfile;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.UserMatchingStatus;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.matching.dto.MatchingDtos.MeResponse;
import com.fittribe.api.matching.dto.MatchingDtos.StatusResponse;
import com.fittribe.api.matching.dto.MatchingDtos.SubmitQuizRequest;
import com.fittribe.api.matching.dto.MatchingDtos.SubmitQuizResponse;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.GroupRepository;
import com.fittribe.api.repository.MatchingProfileRepository;
import com.fittribe.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MatchingApiService}. Repositories mocked; the
 * real (locked) {@link ArchetypeClassifier} runs unmocked, so quiz
 * answer sets below were chosen against the Step-3 scoring table to
 * yield known archetypes (ANCHOR / STRIVER).
 */
@ExtendWith(MockitoExtension.class)
class MatchingApiServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private MatchingProfileRepository profileRepo;
    @Mock private GroupMemberRepository groupMemberRepo;
    @Mock private GroupRepository groupRepo;

    private MatchingApiService service() {
        return new MatchingApiService(userRepo, profileRepo, groupMemberRepo, groupRepo);
    }

    private static final UUID UID = new UUID(0, 1);

    // Answer sets verified against the Step-3 ArchetypeClassifier table.
    private static final SubmitQuizRequest ANCHOR_ANSWERS = new SubmitQuizRequest(
            MatchingAnswer.LIFE_GOT_BUSY, MatchingAnswer.CELEBRATE_WINS,
            MatchingAnswer.SHOW_UP_ON_BAD_DAYS, MatchingAnswer.SHARED_PROGRESS_TRACKING);
    private static final SubmitQuizRequest STRIVER_ANSWERS = new SubmitQuizRequest(
            MatchingAnswer.LOST_MOTIVATION, MatchingAnswer.PUSH_WHEN_SLACKING,
            MatchingAnswer.NEED_NUDGING, MatchingAnswer.FRIENDLY_COMPETITION);

    // ── Helpers ──────────────────────────────────────────────────────

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static User user(UUID id, UserMatchingStatus status, String displayName) {
        User u = new User();
        u.setId(id);
        u.setUserMatchingStatus(status);
        u.setDisplayName(displayName);
        return u;
    }

    private static MatchingProfile profile(UUID userId, Archetype archetype) {
        MatchingProfile p = new MatchingProfile();
        p.setUserId(userId);
        p.setArchetype(archetype);
        return p;
    }

    private static Group matchedGroup(UUID id, String name) {
        Group g = new Group();
        setField(g, "id", id);
        g.setName(name);
        g.setCreatedVia("MATCHED");
        setField(g, "createdAt", Instant.now());
        return g;
    }

    private static GroupMember membership(UUID groupId, UUID userId) {
        GroupMember m = new GroupMember();
        m.setGroupId(groupId);
        m.setUserId(userId);
        return m;
    }

    private static ApiException expectApiException(org.junit.jupiter.api.function.Executable e) {
        try {
            e.execute();
        } catch (Throwable t) {
            assertThat(t).isInstanceOf(ApiException.class);
            return (ApiException) t;
        }
        throw new AssertionError("expected ApiException");
    }

    // ── submitQuiz ───────────────────────────────────────────────────

    @Test
    void submitQuiz_NONE_creates_profile_and_flips_to_QUEUED() {
        User u = user(UID, UserMatchingStatus.NONE, "Asha");
        when(userRepo.findById(UID)).thenReturn(Optional.of(u));
        when(profileRepo.findByUserId(UID)).thenReturn(Optional.empty());

        SubmitQuizResponse resp = service().submitQuiz(UID, ANCHOR_ANSWERS);

        ArgumentCaptor<MatchingProfile> pc = ArgumentCaptor.forClass(MatchingProfile.class);
        verify(profileRepo).save(pc.capture());
        assertThat(pc.getValue().getUserId()).isEqualTo(UID);
        assertThat(pc.getValue().getArchetype()).isEqualTo(Archetype.ANCHOR);
        assertThat(pc.getValue().getAnswerQ1()).isEqualTo("LIFE_GOT_BUSY");
        verify(userRepo).save(u);
        assertThat(u.getUserMatchingStatus()).isEqualTo(UserMatchingStatus.QUEUED);
        assertThat(resp.archetype()).isEqualTo(Archetype.ANCHOR);
        assertThat(resp.status()).isEqualTo(UserMatchingStatus.QUEUED);
    }

    @Test
    void submitQuiz_QUEUED_updates_profile_keeps_status() {
        User u = user(UID, UserMatchingStatus.QUEUED, "Asha");
        MatchingProfile existing = profile(UID, Archetype.RETURNER);
        when(userRepo.findById(UID)).thenReturn(Optional.of(u));
        when(profileRepo.findByUserId(UID)).thenReturn(Optional.of(existing));

        SubmitQuizResponse resp = service().submitQuiz(UID, STRIVER_ANSWERS);

        ArgumentCaptor<MatchingProfile> pc = ArgumentCaptor.forClass(MatchingProfile.class);
        verify(profileRepo).save(pc.capture());
        assertThat(pc.getValue()).isSameAs(existing);
        assertThat(pc.getValue().getArchetype()).isEqualTo(Archetype.STRIVER);
        verify(userRepo, never()).save(any());
        assertThat(u.getUserMatchingStatus()).isEqualTo(UserMatchingStatus.QUEUED);
        assertThat(resp.status()).isEqualTo(UserMatchingStatus.QUEUED);
    }

    @Test
    void submitQuiz_MATCHED_throws_conflict() {
        User u = user(UID, UserMatchingStatus.MATCHED, "Asha");
        when(userRepo.findById(UID)).thenReturn(Optional.of(u));

        ApiException ex = expectApiException(() -> service().submitQuiz(UID, ANCHOR_ANSWERS));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(profileRepo, never()).save(any());
    }

    @Test
    void submitQuiz_OPTED_OUT_throws_conflict() {
        User u = user(UID, UserMatchingStatus.OPTED_OUT, "Asha");
        when(userRepo.findById(UID)).thenReturn(Optional.of(u));

        ApiException ex = expectApiException(() -> service().submitQuiz(UID, ANCHOR_ANSWERS));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(profileRepo, never()).save(any());
    }

    @Test
    void submitQuiz_user_not_found_throws_404() {
        when(userRepo.findById(UID)).thenReturn(Optional.empty());

        ApiException ex = expectApiException(() -> service().submitQuiz(UID, ANCHOR_ANSWERS));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── getMyStatus ──────────────────────────────────────────────────

    @Test
    void getMyStatus_NONE_returns_status_only() {
        when(userRepo.findById(UID))
                .thenReturn(Optional.of(user(UID, UserMatchingStatus.NONE, "Asha")));

        MeResponse r = service().getMyStatus(UID);

        assertThat(r.status()).isEqualTo(UserMatchingStatus.NONE);
        assertThat(r.archetype()).isNull();
        assertThat(r.matchedGroup()).isNull();
        verify(profileRepo, never()).findByUserId(any());
    }

    @Test
    void getMyStatus_QUEUED_returns_status_and_archetype() {
        when(userRepo.findById(UID))
                .thenReturn(Optional.of(user(UID, UserMatchingStatus.QUEUED, "Asha")));
        when(profileRepo.findByUserId(UID))
                .thenReturn(Optional.of(profile(UID, Archetype.GRINDER)));

        MeResponse r = service().getMyStatus(UID);

        assertThat(r.status()).isEqualTo(UserMatchingStatus.QUEUED);
        assertThat(r.archetype()).isEqualTo(Archetype.GRINDER);
        assertThat(r.matchedGroup()).isNull();
    }

    @Test
    void getMyStatus_MATCHED_returns_full_crew_details() {
        UUID groupId = new UUID(0, 100);
        UUID u1 = UID, u2 = new UUID(0, 2), u3 = new UUID(0, 3), u4 = new UUID(0, 4);

        when(userRepo.findById(UID))
                .thenReturn(Optional.of(user(UID, UserMatchingStatus.MATCHED, "Asha")));
        when(profileRepo.findByUserId(UID))
                .thenReturn(Optional.of(profile(UID, Archetype.ANCHOR)));
        when(groupMemberRepo.findByUserId(UID))
                .thenReturn(List.of(membership(groupId, UID)));
        when(groupRepo.findById(groupId))
                .thenReturn(Optional.of(matchedGroup(groupId, "The Anchors")));
        when(groupMemberRepo.findByGroupId(groupId)).thenReturn(List.of(
                membership(groupId, u1), membership(groupId, u2),
                membership(groupId, u3), membership(groupId, u4)));
        when(userRepo.findAllById(anyList())).thenReturn(List.of(
                user(u1, UserMatchingStatus.MATCHED, "Asha"),
                user(u2, UserMatchingStatus.MATCHED, "Beni"),
                user(u3, UserMatchingStatus.MATCHED, "Charu"),
                user(u4, UserMatchingStatus.MATCHED, "Dev")));
        when(profileRepo.findByUserIdIn(anyList())).thenReturn(List.of(
                profile(u1, Archetype.ANCHOR), profile(u2, Archetype.RETURNER),
                profile(u3, Archetype.GRINDER), profile(u4, Archetype.STRIVER)));

        MeResponse r = service().getMyStatus(UID);

        assertThat(r.status()).isEqualTo(UserMatchingStatus.MATCHED);
        assertThat(r.archetype()).isEqualTo(Archetype.ANCHOR);
        assertThat(r.matchedGroup()).isNotNull();
        assertThat(r.matchedGroup().name()).isEqualTo("The Anchors");
        assertThat(r.matchedGroup().members()).hasSize(4);
        assertThat(r.matchedGroup().members()).allSatisfy(m -> {
            assertThat(m.displayName()).isNotBlank();
            assertThat(m.archetype()).isNotNull();
        });
    }

    @Test
    void getMyStatus_OPTED_OUT_returns_status_and_archetype() {
        when(userRepo.findById(UID))
                .thenReturn(Optional.of(user(UID, UserMatchingStatus.OPTED_OUT, "Asha")));
        when(profileRepo.findByUserId(UID))
                .thenReturn(Optional.of(profile(UID, Archetype.SEEKER)));

        MeResponse r = service().getMyStatus(UID);

        assertThat(r.status()).isEqualTo(UserMatchingStatus.OPTED_OUT);
        assertThat(r.archetype()).isEqualTo(Archetype.SEEKER);
        assertThat(r.matchedGroup()).isNull();
        verify(groupMemberRepo, never()).findByUserId(any());
    }

    // ── opt-out ──────────────────────────────────────────────────────

    @Test
    void optOut_QUEUED_user_flips_to_OPTED_OUT() {
        User u = user(UID, UserMatchingStatus.QUEUED, "Asha");
        when(userRepo.findById(UID)).thenReturn(Optional.of(u));

        StatusResponse r = service().optOut(UID);

        assertThat(r.status()).isEqualTo(UserMatchingStatus.OPTED_OUT);
        assertThat(u.getUserMatchingStatus()).isEqualTo(UserMatchingStatus.OPTED_OUT);
        verify(userRepo).save(u);
    }

    @Test
    void optOut_MATCHED_user_flips_status_keeps_group_membership() {
        User u = user(UID, UserMatchingStatus.MATCHED, "Asha");
        when(userRepo.findById(UID)).thenReturn(Optional.of(u));

        StatusResponse r = service().optOut(UID);

        assertThat(r.status()).isEqualTo(UserMatchingStatus.OPTED_OUT);
        assertThat(u.getUserMatchingStatus()).isEqualTo(UserMatchingStatus.OPTED_OUT);
        verify(userRepo).save(u);
        // Membership preserved — opt-out never touches group_members.
        verify(groupMemberRepo, never()).delete(any());
        verify(groupMemberRepo, never()).deleteAll(any());
    }

    @Test
    void optOut_already_OPTED_OUT_is_idempotent() {
        User u = user(UID, UserMatchingStatus.OPTED_OUT, "Asha");
        when(userRepo.findById(UID)).thenReturn(Optional.of(u));

        StatusResponse r = service().optOut(UID);

        assertThat(r.status()).isEqualTo(UserMatchingStatus.OPTED_OUT);
        verify(userRepo, never()).save(any());
    }

    // ── rejoin ───────────────────────────────────────────────────────

    @Test
    void rejoin_OPTED_OUT_user_with_profile_flips_to_QUEUED() {
        User u = user(UID, UserMatchingStatus.OPTED_OUT, "Asha");
        when(userRepo.findById(UID)).thenReturn(Optional.of(u));
        when(profileRepo.findByUserId(UID))
                .thenReturn(Optional.of(profile(UID, Archetype.ANCHOR)));

        StatusResponse r = service().rejoin(UID);

        assertThat(r.status()).isEqualTo(UserMatchingStatus.QUEUED);
        assertThat(u.getUserMatchingStatus()).isEqualTo(UserMatchingStatus.QUEUED);
        verify(userRepo).save(u);
    }

    @Test
    void rejoin_OPTED_OUT_user_without_profile_throws_conflict() {
        User u = user(UID, UserMatchingStatus.OPTED_OUT, "Asha");
        when(userRepo.findById(UID)).thenReturn(Optional.of(u));
        when(profileRepo.findByUserId(UID)).thenReturn(Optional.empty());

        ApiException ex = expectApiException(() -> service().rejoin(UID));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(userRepo, never()).save(any());
    }

    @Test
    void rejoin_QUEUED_user_throws_conflict() {
        when(userRepo.findById(UID))
                .thenReturn(Optional.of(user(UID, UserMatchingStatus.QUEUED, "Asha")));

        ApiException ex = expectApiException(() -> service().rejoin(UID));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(userRepo, never()).save(any());
    }

    @Test
    void rejoin_NONE_user_throws_conflict() {
        when(userRepo.findById(UID))
                .thenReturn(Optional.of(user(UID, UserMatchingStatus.NONE, "Asha")));

        ApiException ex = expectApiException(() -> service().rejoin(UID));
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(userRepo, never()).save(any());
    }
}
