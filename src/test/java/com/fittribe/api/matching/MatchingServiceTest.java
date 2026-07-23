package com.fittribe.api.matching;

import com.fittribe.api.entity.Archetype;
import com.fittribe.api.entity.MatchingProfile;
import com.fittribe.api.entity.PartnerGenderPref;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.UserMatchingStatus;
import com.fittribe.api.matching.GroupBuilder.FormedGroup;
import com.fittribe.api.repository.MatchingProfileRepository;
import com.fittribe.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MatchingService}. The repositories and
 * {@link MatchPersister} are mocked; the real pure-logic
 * {@link GroupBuilder} runs unmocked, so the pools below are built so
 * the engine deterministically produces the asserted group counts.
 */
@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private MatchingProfileRepository profileRepo;
    @Mock private MatchPersister persister;

    private MatchingService service() {
        return new MatchingService(userRepo, profileRepo, persister);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static User user(long idSeed) {
        User u = new User();
        u.setId(new UUID(0, idSeed));
        u.setGender("MALE");
        u.setUserMatchingStatus(UserMatchingStatus.QUEUED);
        return u;
    }

    private static MatchingProfile profile(long idSeed, Archetype archetype) {
        MatchingProfile p = new MatchingProfile();
        p.setUserId(new UUID(0, idSeed));
        p.setArchetype(archetype);
        p.setPartnerGenderPref(PartnerGenderPref.ANY);
        p.setScoreQ1(0);
        p.setScoreQ2(0);
        p.setScoreQ3(0);
        p.setScoreQ4(0);
        return p;
    }

    /** Build users + profiles for the given archetypes, ids 1..n. */
    private record Pool(List<User> users, List<MatchingProfile> profiles) {}

    private static Pool pool(Archetype... archetypes) {
        List<User> users = new ArrayList<>();
        List<MatchingProfile> profiles = new ArrayList<>();
        long seed = 1;
        for (Archetype a : archetypes) {
            users.add(user(seed));
            profiles.add(profile(seed, a));
            seed++;
        }
        return new Pool(users, profiles);
    }

    // ── Tests ────────────────────────────────────────────────────────

    @Test
    void empty_pool_returns_empty_summary() {
        when(userRepo.findByUserMatchingStatus(UserMatchingStatus.QUEUED))
                .thenReturn(List.of());

        BatchSummary s = service().runBatch();

        assertThat(s).isEqualTo(BatchSummary.empty());
        verify(persister, never()).persist(any());
    }

    @Test
    void pool_below_3_returns_empty_summary() {
        when(userRepo.findByUserMatchingStatus(UserMatchingStatus.QUEUED))
                .thenReturn(List.of(user(1), user(2)));

        BatchSummary s = service().runBatch();

        assertThat(s).isEqualTo(BatchSummary.empty());
        verify(persister, never()).persist(any());
    }

    @Test
    void pool_without_profiles_skips_orphan_users() {
        // 4 QUEUED users; only 3 have a matching_profile. The orphan
        // (id 4) is skipped, so the engine sees exactly 3 candidates
        // (ANCHOR, RETURNER, SEEKER) -> one group of 3.
        Pool p = pool(Archetype.ANCHOR, Archetype.RETURNER, Archetype.SEEKER);
        List<User> users = new ArrayList<>(p.users());
        users.add(user(4)); // orphan, no profile
        when(userRepo.findByUserMatchingStatus(UserMatchingStatus.QUEUED))
                .thenReturn(users);
        when(profileRepo.findByUserIdIn(any())).thenReturn(p.profiles());
        when(persister.persist(any())).thenReturn(UUID.randomUUID());

        BatchSummary s = service().runBatch();

        ArgumentCaptor<FormedGroup> cap = ArgumentCaptor.forClass(FormedGroup.class);
        verify(persister, times(1)).persist(cap.capture());
        // Engine input was 3 (orphan excluded) -> a size-3 group, never 4.
        assertThat(cap.getValue().members()).hasSize(3);
        assertThat(s.usersMatched()).isEqualTo(3);
        // pool excluded the orphan, so remainder is 0 (orphan never counted).
        assertThat(s.usersRemaining()).isEqualTo(0);
    }

    @Test
    void successful_run_persists_each_group_and_counts_correctly() {
        // 8 candidates -> engine forms 2 groups of 4, remainder empty.
        Pool p = pool(
                Archetype.ANCHOR, Archetype.ANCHOR, Archetype.GRINDER, Archetype.GRINDER,
                Archetype.RETURNER, Archetype.STRIVER, Archetype.SEEKER, Archetype.SOCIAL_BUTTERFLY);
        when(userRepo.findByUserMatchingStatus(UserMatchingStatus.QUEUED))
                .thenReturn(p.users());
        when(profileRepo.findByUserIdIn(any())).thenReturn(p.profiles());
        when(persister.persist(any())).thenReturn(UUID.randomUUID());

        BatchSummary s = service().runBatch();

        verify(persister, times(2)).persist(any());
        assertThat(s.groupsFormed()).isEqualTo(2);
        assertThat(s.usersMatched()).isEqualTo(8);
        assertThat(s.usersRemaining()).isEqualTo(0);
        assertThat(s.errors()).isEmpty();
    }

    @Test
    void persistence_failure_continues_to_next_group() {
        Pool p = pool(
                Archetype.ANCHOR, Archetype.ANCHOR, Archetype.GRINDER, Archetype.GRINDER,
                Archetype.RETURNER, Archetype.STRIVER, Archetype.SEEKER, Archetype.SOCIAL_BUTTERFLY);
        when(userRepo.findByUserMatchingStatus(UserMatchingStatus.QUEUED))
                .thenReturn(p.users());
        when(profileRepo.findByUserIdIn(any())).thenReturn(p.profiles());
        when(persister.persist(any()))
                .thenThrow(new RuntimeException("db down"))
                .thenReturn(UUID.randomUUID());

        BatchSummary s = service().runBatch();

        verify(persister, times(2)).persist(any()); // did not short-circuit
        assertThat(s.groupsFormed()).isEqualTo(1);
        assertThat(s.usersMatched()).isEqualTo(4);
        assertThat(s.errors()).hasSize(1);
        assertThat(s.errors().get(0)).contains("group of 4").contains("dominant=");
    }

    @Test
    void usersRemaining_includes_engine_remainder_AND_persistence_failures() {
        // 7 candidates -> engine forms 1 group of 4; the leftover 3
        // (STRIVER, SB, SB) are all fragile -> deferred -> remainder 3.
        Pool p = pool(
                Archetype.ANCHOR, Archetype.GRINDER, Archetype.RETURNER, Archetype.RETURNER,
                Archetype.STRIVER, Archetype.SOCIAL_BUTTERFLY, Archetype.SOCIAL_BUTTERFLY);
        when(userRepo.findByUserMatchingStatus(UserMatchingStatus.QUEUED))
                .thenReturn(p.users());
        when(profileRepo.findByUserIdIn(any())).thenReturn(p.profiles());
        when(persister.persist(any())).thenReturn(UUID.randomUUID());

        BatchSummary s = service().runBatch();

        verify(persister, times(1)).persist(any());
        assertThat(s.usersMatched()).isEqualTo(4);
        assertThat(s.usersRemaining()).isEqualTo(3);
    }
}
