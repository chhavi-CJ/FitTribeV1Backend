package com.fittribe.api.matching;

import com.fittribe.api.entity.Group;
import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.entity.User;
import com.fittribe.api.entity.UserMatchingStatus;
import com.fittribe.api.matching.GroupBuilder.FormedGroup;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.GroupRepository;
import com.fittribe.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Writes a {@link FormedGroup} to the database as a {@code groups} row +
 * {@code group_members} rows + flips each member's
 * {@code users.user_matching_status} from QUEUED to MATCHED.
 *
 * <p>Each call to {@link #persist(FormedGroup)} runs in its own
 * {@code @Transactional} boundary. If the call throws, the row is rolled
 * back atomically but other groups already persisted in the same batch
 * are unaffected (locked design decision: per-group transactions, partial
 * success on errors).
 *
 * <p>This is the only place in the matching package that touches the DB.
 */
@Service
public class MatchPersister {

    private static final Logger log = LoggerFactory.getLogger(MatchPersister.class);

    private final GroupRepository groupRepo;
    private final GroupMemberRepository groupMemberRepo;
    private final UserRepository userRepo;
    private final GroupNameGenerator nameGenerator;

    @Autowired
    public MatchPersister(GroupRepository groupRepo,
                          GroupMemberRepository groupMemberRepo,
                          UserRepository userRepo) {
        this.groupRepo = groupRepo;
        this.groupMemberRepo = groupMemberRepo;
        this.userRepo = userRepo;
        // Use the default-constructor name generator (seeded Random per
        // instance). Test constructor below allows injecting one for
        // deterministic name picks in tests.
        this.nameGenerator = new GroupNameGenerator();
    }

    /** Test constructor — allows injecting a seeded name generator. */
    MatchPersister(GroupRepository groupRepo,
                   GroupMemberRepository groupMemberRepo,
                   UserRepository userRepo,
                   GroupNameGenerator nameGenerator) {
        this.groupRepo = groupRepo;
        this.groupMemberRepo = groupMemberRepo;
        this.userRepo = userRepo;
        this.nameGenerator = nameGenerator;
    }

    /**
     * Persist a formed group atomically.
     *
     * @return the persisted Group's UUID on success.
     * @throws RuntimeException if anything fails — the caller
     *         ({@link MatchingService}) catches this and adds an error
     *         line to {@link BatchSummary#errors()}.
     */
    @Transactional
    public UUID persist(FormedGroup formed) {
        if (formed == null) throw new IllegalArgumentException("formed group is null");
        if (formed.members() == null || formed.members().size() < 3) {
            throw new IllegalArgumentException(
                    "formed group must have >= 3 members, got " +
                    (formed.members() == null ? 0 : formed.members().size()));
        }

        // 1. Pick a name. Uniqueness checked against the DB.
        String name = nameGenerator.pickName(
                formed.dominantArchetype(),
                formed.members().size(),
                groupRepo::existsByName);

        // 2. Create the group row.
        Group group = new Group();
        group.setName(name);
        // Member 0 of the formed group is the seed — record them as
        // the creator. (groups.created_by is nullable, so this is
        // informational, not load-bearing.)
        group.setCreatedBy(formed.members().get(0).profile().getUserId());
        group.setCreatedVia("MATCHED");  // see Step 1 migration — V71 added this column
        // streak default 0, weekly_goal default 4 — set by @Column defaults
        Group saved = groupRepo.save(group);

        // 3. Create one group_members row per member.
        List<GroupMember> memberships = formed.members().stream().map(c -> {
            GroupMember m = new GroupMember();
            m.setGroupId(saved.getId());
            m.setUserId(c.profile().getUserId());
            m.setRole("MEMBER");
            return m;
        }).toList();
        groupMemberRepo.saveAll(memberships);

        // 4. Flip each member's matching status from QUEUED to MATCHED.
        List<UUID> memberIds = formed.members().stream()
                .map(c -> c.profile().getUserId()).toList();
        List<User> users = userRepo.findAllById(memberIds);
        for (User u : users) {
            u.setUserMatchingStatus(UserMatchingStatus.MATCHED);
        }
        userRepo.saveAll(users);

        log.info("Persisted matched group {} ('{}') with {} members, quality {}",
                saved.getId(), name, formed.members().size(), formed.qualityScore());

        return saved.getId();
    }
}
