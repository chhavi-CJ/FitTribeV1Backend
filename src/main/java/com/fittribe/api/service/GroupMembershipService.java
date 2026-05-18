package com.fittribe.api.service;

import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.GroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class GroupMembershipService {

    private static final Logger log = LoggerFactory.getLogger(GroupMembershipService.class);

    private final GroupRepository        groupRepo;
    private final GroupMemberRepository  memberRepo;
    private final GroupProgressService   groupProgressService;

    public GroupMembershipService(GroupRepository groupRepo,
                                  GroupMemberRepository memberRepo,
                                  GroupProgressService groupProgressService) {
        this.groupRepo            = groupRepo;
        this.memberRepo           = memberRepo;
        this.groupProgressService = groupProgressService;
    }

    /**
     * Removes a user from a group. Extracted verbatim from
     * GroupController.leave() so it can be reused from account deletion.
     *
     * No-ops if the membership does not exist (idempotent): both the
     * standalone leave endpoint and account-deletion already know the
     * user is a member, so a missing row only happens on a concurrent
     * leave. Leaving a group you are not in is a safe no-op.
     */
    @Transactional
    public void removeMemberFromGroup(UUID groupId, UUID userId) {
        GroupMember leaving = memberRepo.findByGroupIdAndUserId(groupId, userId)
                .orElse(null);
        if (leaving == null) {
            return;
        }

        List<GroupMember> allMembers = memberRepo.findByGroupId(groupId);

        if (allMembers.size() == 1) {
            // Sole member — delete the entire group (cascades to members, feed, reactions)
            groupRepo.deleteById(groupId);
        } else {
            // If leaving user is the only ADMIN, promote longest-tenured member
            boolean isOnlyAdmin = "ADMIN".equals(leaving.getRole())
                    && allMembers.stream().filter(m -> "ADMIN".equals(m.getRole())).count() == 1;

            if (isOnlyAdmin) {
                allMembers.stream()
                        .filter(m -> !m.getUserId().equals(userId))
                        .min(Comparator.comparing(GroupMember::getJoinedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .ifPresent(m -> {
                            m.setRole("ADMIN");
                            memberRepo.save(m);
                        });
            }

            memberRepo.delete(leaving);

            try {
                groupProgressService.onMemberLeftGroup(groupId, userId);
            } catch (Exception e) {
                log.error("Group progress leave hook failed for group={} user={}", groupId, userId, e);
            }
        }
    }
}
