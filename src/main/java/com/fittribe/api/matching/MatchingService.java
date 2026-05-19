package com.fittribe.api.matching;

import com.fittribe.api.entity.MatchingProfile;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.UserMatchingStatus;
import com.fittribe.api.matching.GroupBuilder.BuildResult;
import com.fittribe.api.matching.GroupBuilder.FormedGroup;
import com.fittribe.api.repository.MatchingProfileRepository;
import com.fittribe.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Conscious Matching orchestrator. Loads the queued pool, calls the
 * pure-logic {@link GroupBuilder}, hands each formed group to
 * {@link MatchPersister}, aggregates a {@link BatchSummary}.
 *
 * <p>Stateless. Safe to call concurrently from multiple triggers (cron
 * + admin endpoint) — but in practice the per-batch query pattern
 * means concurrent runs would both load the same pool and try to match
 * the same users, which the persister's failures would catch gracefully
 * (one batch wins, the other gets DB conflicts and skips). At launch
 * scale a single trigger is plenty.
 *
 * <p>NOT @Transactional itself — transactions are per-group, inside the
 * persister. A failure in one group must not roll back the others.
 */
@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final UserRepository userRepo;
    private final MatchingProfileRepository profileRepo;
    private final MatchPersister persister;

    @Autowired
    public MatchingService(UserRepository userRepo,
                           MatchingProfileRepository profileRepo,
                           MatchPersister persister) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
        this.persister = persister;
    }

    /**
     * Run one batch. Loads all QUEUED users, attempts to form groups,
     * persists each (atomically per-group). Users who don't get
     * matched remain QUEUED for next batch.
     */
    public BatchSummary runBatch() {
        // 1. Load queued users.
        List<User> queued = userRepo.findByUserMatchingStatus(UserMatchingStatus.QUEUED);
        log.info("Conscious Matching batch starting — queued pool size: {}", queued.size());

        if (queued.size() < 3) {
            log.info("Pool size {} < 3; nothing to do.", queued.size());
            return BatchSummary.empty();
        }

        // 2. Batch-load matching profiles for the queued users.
        List<UUID> userIds = queued.stream().map(User::getId).toList();
        List<MatchingProfile> profiles = profileRepo.findByUserIdIn(userIds);
        Map<UUID, MatchingProfile> profileByUser = profiles.stream()
                .collect(Collectors.toMap(MatchingProfile::getUserId, p -> p));

        // 3. Build MatchCandidates, skipping users with no matching_profile
        //    (defensive — shouldn't happen if QUEUED was set via the
        //    submit-quiz path, but a manual SQL UPDATE could create this
        //    inconsistency, so we tolerate it).
        List<MatchCandidate> pool = new ArrayList<>(queued.size());
        for (User u : queued) {
            MatchingProfile p = profileByUser.get(u.getId());
            if (p == null) {
                log.warn("User {} is QUEUED but has no matching_profile — skipping", u.getId());
                continue;
            }
            pool.add(new MatchCandidate(p, u.getGender()));
        }

        if (pool.size() < 3) {
            log.info("Effective pool size {} < 3 after profile join; nothing to do.", pool.size());
            return BatchSummary.empty();
        }

        // 4. Call the engine.
        BuildResult result = GroupBuilder.buildGroups(pool);
        log.info("Engine produced {} groups, {} candidates in remainder",
                result.formedGroups().size(), result.remainder().size());

        // 5. Persist each group, per-transaction. Skip on error.
        int usersMatchedCount = 0;
        List<String> errors = new ArrayList<>();
        for (FormedGroup group : result.formedGroups()) {
            try {
                UUID groupId = persister.persist(group);
                usersMatchedCount += group.members().size();
                log.info("Persisted group {} with {} members", groupId, group.members().size());
            } catch (RuntimeException e) {
                String summary = "Failed to persist group of " + group.members().size()
                        + " (dominant=" + group.dominantArchetype() + ", quality="
                        + group.qualityScore() + "): " + e.getMessage();
                log.error(summary, e);
                errors.add(summary);
                // Per locked design: persistence failure → those users
                // stay QUEUED (status was never flipped). They'll
                // naturally be retried next batch. No further action.
            }
        }

        int usersRemaining = pool.size() - usersMatchedCount;

        BatchSummary summary = new BatchSummary(
                result.formedGroups().size() - countFailed(errors),
                usersMatchedCount,
                usersRemaining,
                errors);
        log.info("Conscious Matching batch complete: {} groups formed, " +
                 "{} users matched, {} remaining, {} errors",
                summary.groupsFormed(), summary.usersMatched(),
                summary.usersRemaining(), summary.errors().size());
        return summary;
    }

    private static int countFailed(List<String> errors) { return errors.size(); }
}
