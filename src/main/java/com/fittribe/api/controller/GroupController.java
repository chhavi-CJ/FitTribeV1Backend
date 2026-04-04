package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.request.CreateGroupRequest;
import com.fittribe.api.dto.request.JoinGroupRequest;
import com.fittribe.api.entity.*;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final GroupRepository         groupRepo;
    private final GroupMemberRepository   memberRepo;
    private final FeedItemRepository      feedRepo;
    private final UserRepository          userRepo;
    private final NotificationRepository  notifRepo;
    private final WorkoutSessionRepository sessionRepo;
    private final SetLogRepository        setLogRepo;

    public GroupController(GroupRepository groupRepo,
                           GroupMemberRepository memberRepo,
                           FeedItemRepository feedRepo,
                           UserRepository userRepo,
                           NotificationRepository notifRepo,
                           WorkoutSessionRepository sessionRepo,
                           SetLogRepository setLogRepo) {
        this.groupRepo   = groupRepo;
        this.memberRepo  = memberRepo;
        this.feedRepo    = feedRepo;
        this.userRepo    = userRepo;
        this.notifRepo   = notifRepo;
        this.sessionRepo = sessionRepo;
        this.setLogRepo  = setLogRepo;
    }

    // ── POST /groups ──────────────────────────────────────────────────
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<?>> createGroup(
            @RequestBody CreateGroupRequest request,
            Authentication auth) {

        UUID userId = userId(auth);

        Group group = new Group();
        group.setName(request.name());
        group.setIcon(request.icon());
        group.setColor(request.color());
        group.setInviteCode(generateUniqueInviteCode());
        group.setCreatedBy(userId);
        Group saved = groupRepo.save(group);

        GroupMember member = new GroupMember();
        member.setGroupId(saved.getId());
        member.setUserId(userId);
        member.setRole("ADMIN");
        memberRepo.save(member);

        FeedItem feed = new FeedItem();
        feed.setGroupId(saved.getId());
        feed.setUserId(userId);
        feed.setType("GROUP_CREATED");
        feed.setBody("Group created");
        feedRepo.save(feed);

        return ResponseEntity.ok(ApiResponse.success(toGroupResponse(saved, 1)));
    }

    // ── POST /groups/join ─────────────────────────────────────────────
    @PostMapping("/join")
    @Transactional
    public ResponseEntity<ApiResponse<?>> joinGroup(
            @RequestBody JoinGroupRequest request,
            Authentication auth) {

        UUID userId = userId(auth);

        Group group = groupRepo.findByInviteCode(request.inviteCode())
                .orElseThrow(() -> ApiException.notFound("Group"));

        if (memberRepo.existsByGroupIdAndUserId(group.getId(), userId)) {
            throw ApiException.alreadyMember();
        }

        GroupMember member = new GroupMember();
        member.setGroupId(group.getId());
        member.setUserId(userId);
        member.setRole("MEMBER");
        try {
            memberRepo.save(member);
        } catch (DataIntegrityViolationException e) {
            // Lost concurrent join race — the other request already inserted this membership
            throw ApiException.alreadyMember();
        }

        String displayName = userRepo.findById(userId)
                .map(User::getDisplayName)
                .orElse("Someone");

        FeedItem feed = new FeedItem();
        feed.setGroupId(group.getId());
        feed.setUserId(userId);
        feed.setType("MEMBER_JOINED");
        feed.setBody((displayName != null ? displayName : "Someone") + " joined the group");
        feedRepo.save(feed);

        long memberCount = memberRepo.findByGroupId(group.getId()).size();
        return ResponseEntity.ok(ApiResponse.success(toGroupResponse(group, memberCount)));
    }

    // ── GET /groups/mine ──────────────────────────────────────────────
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<?>> myGroups(Authentication auth) {
        UUID userId = userId(auth);

        // 1 query: memberships for this user
        List<GroupMember> myMemberships = memberRepo.findByUserId(userId);
        if (myMemberships.isEmpty()) return ResponseEntity.ok(ApiResponse.success(List.of()));

        Set<UUID> groupIds = myMemberships.stream()
                .map(GroupMember::getGroupId).collect(Collectors.toSet());

        // 1 query: all groups in one shot
        Map<UUID, Group> groupsById = groupRepo.findByIdIn(groupIds).stream()
                .collect(Collectors.toMap(Group::getId, g -> g));

        // 1 query: all memberships across all groups to get member counts
        Map<UUID, Long> memberCountByGroup = memberRepo.findByGroupIdIn(groupIds).stream()
                .collect(Collectors.groupingBy(GroupMember::getGroupId, Collectors.counting()));

        List<Map<String, Object>> result = myMemberships.stream()
                .map(gm -> groupsById.get(gm.getGroupId()))
                .filter(Objects::nonNull)
                .map(g -> toGroupResponse(g, memberCountByGroup.getOrDefault(g.getId(), 0L)))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── GET /groups/{id}/members ──────────────────────────────────────
    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<?>> getMembers(
            @PathVariable UUID id, Authentication auth) {

        requireMembership(id, userId(auth));

        List<Map<String, Object>> result = memberRepo.findByGroupId(id).stream()
                .map(gm -> {
                    User user = userRepo.findById(gm.getUserId()).orElse(null);
                    // Skip members who have opted out of the leaderboard
                    if (user != null && !Boolean.TRUE.equals(user.getShowInLeaderboard())) return null;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("userId",      gm.getUserId());
                    m.put("displayName", user != null ? user.getDisplayName() : null);
                    m.put("role",        gm.getRole());
                    m.put("streak",      user != null ? user.getStreak() : 0);
                    m.put("joinedAt",    gm.getJoinedAt());
                    m.put("hasCrown",    gm.getCrownExpiresAt() != null
                            && gm.getCrownExpiresAt().isAfter(Instant.now()));
                    return m;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── GET /groups/{id}/feed ─────────────────────────────────────────
    @GetMapping("/{id}/feed")
    public ResponseEntity<ApiResponse<?>> getFeed(
            @PathVariable UUID id, Authentication auth) {

        requireMembership(id, userId(auth));

        // 1 query: top 30 feed items
        List<FeedItem> feedItems = feedRepo.findTop30ByGroupIdOrderByCreatedAtDesc(id);

        // 1 query: batch load all distinct authors referenced by the feed
        Set<UUID> authorIds = feedItems.stream()
                .map(FeedItem::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> nameByUserId = authorIds.isEmpty()
                ? Map.of()
                : userRepo.findByIdIn(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u.getDisplayName() != null
                                ? u.getDisplayName() : "Someone"));

        List<Map<String, Object>> result = feedItems.stream()
                .map(fi -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",          fi.getId());
                    m.put("type",        fi.getType());
                    m.put("body",        fi.getBody());
                    m.put("userId",      fi.getUserId());
                    m.put("displayName", fi.getUserId() != null
                            ? nameByUserId.get(fi.getUserId()) : null);
                    m.put("createdAt",   fi.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── GET /groups/pulse ─────────────────────────────────────────────
    @GetMapping("/pulse")
    public ResponseEntity<ApiResponse<?>> pulse(Authentication auth) {
        UUID userId = userId(auth);
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        // 1 query: user's group memberships
        List<GroupMember> myMemberships = memberRepo.findByUserId(userId);
        if (myMemberships.isEmpty()) return ResponseEntity.ok(ApiResponse.success(List.of()));

        Set<UUID> groupIds = myMemberships.stream()
                .map(GroupMember::getGroupId).collect(Collectors.toSet());

        // 1 query: all groups
        Map<UUID, Group> groupsById = groupRepo.findByIdIn(groupIds).stream()
                .collect(Collectors.toMap(Group::getId, g -> g));

        // 1 query: all memberships across all groups (to get member counts + member IDs)
        List<GroupMember> allMemberships = memberRepo.findByGroupIdIn(groupIds);
        Map<UUID, List<UUID>> memberIdsByGroup = allMemberships.stream()
                .collect(Collectors.groupingBy(GroupMember::getGroupId,
                        Collectors.mapping(GroupMember::getUserId, Collectors.toList())));

        // Collect every member ID across all groups for a single session query
        Set<UUID> allMemberIds = allMemberships.stream()
                .map(GroupMember::getUserId).collect(Collectors.toSet());

        // 1 query: all of today's sessions across all members
        List<WorkoutSession> todayAllSessions = allMemberIds.isEmpty()
                ? List.of()
                : sessionRepo.findByUserIdInAndStatusAndStartedAtAfter(
                        allMemberIds, "COMPLETED", startOfToday);

        // group_id is not on WorkoutSession — partition sessions by which group's members logged them
        Map<UUID, Set<UUID>> loggedUsersByGroup = new HashMap<>();
        Map<UUID, Set<UUID>> sessionIdsByGroup  = new HashMap<>();
        for (WorkoutSession s : todayAllSessions) {
            for (UUID gId : groupIds) {
                List<UUID> gMembers = memberIdsByGroup.getOrDefault(gId, List.of());
                if (gMembers.contains(s.getUserId())) {
                    loggedUsersByGroup.computeIfAbsent(gId, k -> new HashSet<>()).add(s.getUserId());
                    sessionIdsByGroup.computeIfAbsent(gId, k -> new HashSet<>()).add(s.getId());
                }
            }
        }

        // 1 query: check PR existence across all today's sessions at once
        Set<UUID> allTodaySessionIds = todayAllSessions.stream()
                .map(WorkoutSession::getId).collect(Collectors.toSet());
        Set<UUID> sessionIdsWithPr = allTodaySessionIds.isEmpty()
                ? Set.of()
                : setLogRepo.findSessionIdsWithPrIn(allTodaySessionIds);

        List<Map<String, Object>> result = groupIds.stream()
                .map(groupsById::get)
                .filter(Objects::nonNull)
                .map(group -> {
                    List<UUID> gMemberIds = memberIdsByGroup.getOrDefault(group.getId(), List.of());
                    int loggedToday = loggedUsersByGroup.getOrDefault(group.getId(), Set.of()).size();
                    Set<UUID> gSessionIds = sessionIdsByGroup.getOrDefault(group.getId(), Set.of());
                    boolean hasPr = gSessionIds.stream().anyMatch(sessionIdsWithPr::contains);

                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("groupId",      group.getId());
                    m.put("groupName",    group.getName());
                    m.put("groupIcon",    group.getIcon());
                    m.put("loggedToday",  loggedToday);
                    m.put("totalMembers", gMemberIds.size());
                    m.put("streak",       group.getStreak());
                    m.put("hasPr",        hasPr);
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── POST /groups/{id}/poke/{memberId} ─────────────────────────────
    @PostMapping("/{id}/poke/{memberId}")
    @Transactional
    public ResponseEntity<ApiResponse<?>> poke(
            @PathVariable UUID id,
            @PathVariable UUID memberId,
            Authentication auth) {

        UUID pokerId = userId(auth);
        requireMembership(id, pokerId);
        requireMembership(id, memberId);

        String pokerName = userRepo.findById(pokerId)
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : "Someone")
                .orElse("Someone");
        String targetName = userRepo.findById(memberId)
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : "Someone")
                .orElse("Someone");

        Notification notif = new Notification();
        notif.setUserId(memberId);
        notif.setType("POKE");
        notif.setTitle(pokerName + " poked you \uD83D\uDC4B");
        notif.setBody(pokerName + " has already logged today. You haven't.");
        notifRepo.save(notif);

        FeedItem feed = new FeedItem();
        feed.setGroupId(id);
        feed.setUserId(pokerId);
        feed.setType("POKE");
        feed.setBody(pokerName + " poked " + targetName);
        feedRepo.save(feed);

        return ResponseEntity.ok(ApiResponse.success(Map.of("poked", true)));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private UUID userId(Authentication auth) {
        return (UUID) auth.getPrincipal();
    }

    private void requireMembership(UUID groupId, UUID userId) {
        if (!memberRepo.existsByGroupIdAndUserId(groupId, userId)) {
            throw ApiException.forbidden();
        }
    }

    private Map<String, Object> toGroupResponse(Group g, long memberCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          g.getId());
        m.put("name",        g.getName());
        m.put("icon",        g.getIcon());
        m.put("color",       g.getColor());
        m.put("inviteCode",  g.getInviteCode());
        m.put("streak",      g.getStreak());
        m.put("weeklyGoal",  g.getWeeklyGoal());
        m.put("memberCount", memberCount);
        return m;
    }

    private String generateUniqueInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rand = new Random();
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(rand.nextInt(chars.length())));
            }
            code = sb.toString();
        } while (groupRepo.findByInviteCode(code).isPresent());
        return code;
    }
}
