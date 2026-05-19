package com.fittribe.api.matching;

import com.fittribe.api.entity.Archetype;
import com.fittribe.api.entity.Group;
import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.entity.MatchingProfile;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.UserMatchingStatus;
import com.fittribe.api.matching.GroupBuilder.FormedGroup;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.GroupRepository;
import com.fittribe.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MatchPersister}. Repositories are mocked; the
 * persister is built via its package-private test constructor so a
 * deterministic / mock {@link GroupNameGenerator} can be injected.
 */
@ExtendWith(MockitoExtension.class)
class MatchPersisterTest {

    @Mock private GroupRepository groupRepo;
    @Mock private GroupMemberRepository groupMemberRepo;
    @Mock private UserRepository userRepo;

    private static final UUID SAVED_GROUP_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

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

    private static MatchCandidate candidate(long idSeed, Archetype archetype) {
        MatchingProfile p = new MatchingProfile();
        p.setUserId(new UUID(0, idSeed));
        p.setArchetype(archetype);
        return new MatchCandidate(p, "MALE");
    }

    private static User user(long idSeed) {
        User u = new User();
        u.setId(new UUID(0, idSeed));
        u.setGender("MALE");
        u.setUserMatchingStatus(UserMatchingStatus.QUEUED);
        return u;
    }

    private static FormedGroup formed(Archetype dominant, Archetype... members) {
        List<MatchCandidate> list = new ArrayList<>();
        long seed = 1;
        for (Archetype a : members) list.add(candidate(seed++, a));
        return new FormedGroup(list, 20, dominant);
    }

    /** Stub groupRepo.save to echo the arg back with a fixed id. */
    private void stubGroupSaveWithId() {
        when(groupRepo.save(any(Group.class))).thenAnswer(inv -> {
            Group g = inv.getArgument(0);
            setField(g, "id", SAVED_GROUP_ID);
            return g;
        });
    }

    // ── Tests ────────────────────────────────────────────────────────

    @Test
    void persists_group_with_members_and_flips_status() {
        MatchPersister persister = new MatchPersister(
                groupRepo, groupMemberRepo, userRepo, new GroupNameGenerator());
        FormedGroup group = formed(Archetype.ANCHOR,
                Archetype.ANCHOR, Archetype.RETURNER, Archetype.GRINDER, Archetype.STRIVER);

        stubGroupSaveWithId();
        List<User> members = List.of(user(1), user(2), user(3), user(4));
        when(userRepo.findAllById(any())).thenReturn(members);

        UUID returned = persister.persist(group);

        assertThat(returned).isEqualTo(SAVED_GROUP_ID);

        ArgumentCaptor<Group> groupCap = ArgumentCaptor.forClass(Group.class);
        verify(groupRepo, times(1)).save(groupCap.capture());
        assertThat(groupCap.getValue().getCreatedVia()).isEqualTo("MATCHED");
        assertThat(groupCap.getValue().getName()).isNotBlank();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GroupMember>> memCap = ArgumentCaptor.forClass(List.class);
        verify(groupMemberRepo, times(1)).saveAll(memCap.capture());
        assertThat(memCap.getValue()).hasSize(4);
        assertThat(memCap.getValue()).allSatisfy(m -> {
            assertThat(m.getGroupId()).isEqualTo(SAVED_GROUP_ID);
            assertThat(m.getRole()).isEqualTo("MEMBER");
        });

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<User>> userCap = ArgumentCaptor.forClass(List.class);
        verify(userRepo, times(1)).saveAll(userCap.capture());
        assertThat(userCap.getValue()).hasSize(4);
        assertThat(userCap.getValue()).allSatisfy(u ->
                assertThat(u.getUserMatchingStatus()).isEqualTo(UserMatchingStatus.MATCHED));
    }

    @Test
    void picks_unique_name_via_group_repo_check() {
        MatchPersister persister = new MatchPersister(
                groupRepo, groupMemberRepo, userRepo, new GroupNameGenerator());
        FormedGroup group = formed(Archetype.ANCHOR,
                Archetype.ANCHOR, Archetype.RETURNER, Archetype.GRINDER);

        stubGroupSaveWithId();
        // First candidate name taken, second free.
        when(groupRepo.existsByName(anyString())).thenReturn(true, false);
        when(userRepo.findAllById(any())).thenReturn(List.of());

        persister.persist(group);

        ArgumentCaptor<String> nameCap = ArgumentCaptor.forClass(String.class);
        verify(groupRepo, times(2)).existsByName(nameCap.capture());
        ArgumentCaptor<Group> groupCap = ArgumentCaptor.forClass(Group.class);
        verify(groupRepo).save(groupCap.capture());

        // The persisted name must be the SECOND candidate queried — the
        // one existsByName reported free. This pins the wiring to
        // GroupRepository.existsByName.
        String persistedName = groupCap.getValue().getName();
        assertThat(persistedName).isEqualTo(nameCap.getAllValues().get(1));
        assertThat(persistedName).isNotEqualTo(nameCap.getAllValues().get(0));
    }

    @Test
    void rejects_null_formed_group() {
        MatchPersister persister = new MatchPersister(
                groupRepo, groupMemberRepo, userRepo, new GroupNameGenerator());
        assertThatThrownBy(() -> persister.persist(null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(groupRepo, never()).save(any());
    }

    @Test
    void rejects_under_size_group() {
        MatchPersister persister = new MatchPersister(
                groupRepo, groupMemberRepo, userRepo, new GroupNameGenerator());
        FormedGroup tooSmall = formed(Archetype.ANCHOR, Archetype.ANCHOR, Archetype.RETURNER);
        assertThatThrownBy(() -> persister.persist(tooSmall))
                .isInstanceOf(IllegalArgumentException.class);
        verify(groupRepo, never()).save(any());
    }

    @Test
    void uses_dominant_archetype_for_name_pool() {
        // GroupNameGenerator is final (locked Step 4a) so it can't be
        // Mockito-mocked. Instead use the real generator with a free
        // uniqueness check: the picked name then comes straight from the
        // dominant archetype's pool. Asserting the name is a GRINDER-pool
        // entry proves dominantArchetype drove pool selection.
        MatchPersister persister = new MatchPersister(
                groupRepo, groupMemberRepo, userRepo, new GroupNameGenerator());

        FormedGroup group = formed(Archetype.GRINDER,
                Archetype.GRINDER, Archetype.GRINDER, Archetype.STRIVER, Archetype.SEEKER);
        stubGroupSaveWithId();
        when(userRepo.findAllById(any())).thenReturn(List.of());
        // groupRepo.existsByName unstubbed -> false -> never falls back
        // to the mixed pool, so the name is from the GRINDER pool.

        persister.persist(group);

        ArgumentCaptor<Group> groupCap = ArgumentCaptor.forClass(Group.class);
        verify(groupRepo).save(groupCap.capture());
        assertThat(groupCap.getValue().getName())
                .isIn("The Grinders", "No Days Off", "The Discipline Club");
    }
}
