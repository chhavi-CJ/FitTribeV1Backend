package com.fittribe.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.group.GroupCarouselDto;
import com.fittribe.api.dto.group.GroupWeeklyCardDto;
import com.fittribe.api.dto.group.GroupWeeklyProgressDto;
import com.fittribe.api.dto.group.LeaderboardResponseDto;
import com.fittribe.api.dto.group.TopPerformerDto;
import com.fittribe.api.entity.GroupWeeklyTopPerformer;
import com.fittribe.api.repository.GroupWeeklyTopPerformerRepository;
import com.fittribe.api.service.LeaderboardService;
import com.fittribe.api.service.TopPerformerService;
import com.fittribe.api.dto.request.CreateGroupRequest;
import com.fittribe.api.dto.request.JoinGroupRequest;
import com.fittribe.api.dto.request.ReactRequest;
import com.fittribe.api.dto.request.UpdateGroupRequest;
import com.fittribe.api.entity.*;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.service.GroupProgressService;
import com.fittribe.api.service.GroupWeeklyCardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fittribe.api.repository.*;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.fittribe.api.util.Zones;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private static final Logger log = LoggerFactory.getLogger(GroupController.class);

    private final GroupRepository         groupRepo;
    private final GroupMemberRepository   memberRepo;
    private final FeedItemRepository      feedRepo;
    private final UserRepository          userRepo;
    private final NotificationRepository  notifRepo;
    private final WorkoutSessionRepository sessionRepo;
    private final SetLogRepository        setLogRepo;
    private final ReactionRepository      reactionRepo;
    private final GroupProgressService              groupProgressService;
    private final GroupWeeklyCardService            groupWeeklyCardService;
    private final GroupWeeklyTopPerformerRepository topPerformerRepo;
    private final LeaderboardService                leaderboardService;
    private final PokeLogRepository                 pokeLogRepo;
    private final ObjectMapper                      mapper;

    public GroupController(GroupRepository groupRepo,
                           GroupMemberRepository memberRepo,
                           FeedItemRepository feedRepo,
                           UserRepository userRepo,
                           NotificationRepository notifRepo,
                           WorkoutSessionRepository sessionRepo,
                           SetLogRepository setLogRepo,
                           ReactionRepository reactionRepo,
                           GroupProgressService groupProgressService,
                           GroupWeeklyCardService groupWeeklyCardService,
                           GroupWeeklyTopPerformerRepository topPerformerRepo,
                           LeaderboardService leaderboardService,
                           PokeLogRepository pokeLogRepo,
                           ObjectMapper mapper) {
        this.groupRepo             = groupRepo;
        this.memberRepo            = memberRepo;
        this.feedRepo              = feedRepo;
        this.userRepo              = userRepo;
        this.notifRepo             = notifRepo;
        this.sessionRepo           = sessionRepo;
        this.setLogRepo            = setLogRepo;
        this.reactionRepo          = reactionRepo;
        this.groupProgressService  = groupProgressService;
        this.groupWeeklyCardService = groupWeeklyCardService;
        this.topPerformerRepo      = topPerformerRepo;
        this.leaderboardService    = leaderboardService;
        this.pokeLogRepo           = pokeLogRepo;
        this.mapper                = mapper;
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

        try {
            groupProgressService.onMemberJoinedGroup(group.getId(), userId);
        } catch (Exception e) {
            log.error("Group progress join hook failed for group={} user={}", group.getId(), userId, e);
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

    // ── GET /groups/{id} ───────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getGroup(
            @PathVariable UUID id, Authentication auth) {

        UUID userId = userId(auth);
        GroupMember membership = memberRepo.findByGroupIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.forbidden());

        Group group = groupRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Group"));

        long memberCount = memberRepo.findByGroupId(id).size();

        Map<String, Object> response = toGroupResponse(group, memberCount);
        response.put("createdBy",       group.getCreatedBy());
        response.put("createdAt",       group.getCreatedAt());
        response.put("currentUserRole", membership.getRole());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── PATCH /groups/{id} ───────────────────────────────────────────
    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<?>> updateGroup(
            @PathVariable UUID id,
            @RequestBody UpdateGroupRequest request,
            Authentication auth) {

        UUID userId = userId(auth);
        GroupMember membership = memberRepo.findByGroupIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Group"));

        if (!"ADMIN".equals(membership.getRole())) {
            throw ApiException.forbidden();
        }

        Group group = groupRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Group"));

        if (request.name() != null)  group.setName(request.name());
        if (request.icon() != null)  group.setIcon(request.icon());
        if (request.color() != null) group.setColor(request.color());
        groupRepo.save(group);

        long memberCount = memberRepo.findByGroupId(id).size();
        return ResponseEntity.ok(ApiResponse.success(toGroupResponse(group, memberCount)));
    }

    // ── POST /groups/{id}/leave ──────────────────────────────────────
    @PostMapping("/{id}/leave")
    @Transactional
    public ResponseEntity<ApiResponse<?>> leave(
            @PathVariable UUID id, Authentication auth) {

        UUID userId = userId(auth);

        GroupMember leaving = memberRepo.findByGroupIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Membership"));

        List<GroupMember> allMembers = memberRepo.findByGroupId(id);

        if (allMembers.size() == 1) {
            // Sole member — delete the entire group (cascades to members, feed, reactions)
            groupRepo.deleteById(id);
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
                groupProgressService.onMemberLeftGroup(id, userId);
            } catch (Exception e) {
                log.error("Group progress leave hook failed for group={} user={}", id, userId, e);
            }
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of("success", true)));
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

        Map<UUID, String> roleByGroup = myMemberships.stream()
                .collect(Collectors.toMap(GroupMember::getGroupId, GroupMember::getRole));

        List<Map<String, Object>> result = myMemberships.stream()
                .map(gm -> groupsById.get(gm.getGroupId()))
                .filter(Objects::nonNull)
                .map(g -> {
                    Map<String, Object> resp = toGroupResponse(g, memberCountByGroup.getOrDefault(g.getId(), 0L));
                    resp.put("currentUserRole", roleByGroup.get(g.getId()));
                    return resp;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── GET /groups/{id}/members ──────────────────────────────────────
    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<?>> getMembers(
            @PathVariable UUID id, Authentication auth) {

        requireMembership(id, userId(auth));

        // Batch-load which recipients have been poked today in this group (one query)
        LocalDate today = LocalDate.now(Zones.APP_ZONE);
        Set<UUID> pokedTodayIds = pokeLogRepo.findRecipientsPokdToday(id, today);

        List<Map<String, Object>> result = memberRepo.findByGroupId(id).stream()
                .map(gm -> {
                    User user = userRepo.findById(gm.getUserId()).orElse(null);
                    // Skip if user not found (orphaned member row)
                    if (user == null) return null;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("userId",      gm.getUserId());
                    m.put("displayName", user.getDisplayName());
                    m.put("role",        gm.getRole());
                    m.put("streak",      user.getStreak());
                    m.put("joinedAt",    gm.getJoinedAt());
                    m.put("hasCrown",    gm.getCrownExpiresAt() != null
                            && gm.getCrownExpiresAt().isAfter(Instant.now()));
                    m.put("pokedToday",  pokedTodayIds.contains(gm.getUserId()));
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

        UUID currentUserId = userId(auth);
        requireMembership(id, currentUserId);

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

        // 1 query: batch load all reactions for these feed items
        Set<UUID> feedItemIds = feedItems.stream().map(FeedItem::getId).collect(Collectors.toSet());
        Map<UUID, List<Reaction>> reactionsByItem = feedItemIds.isEmpty()
                ? Map.of()
                : reactionRepo.findByFeedItemIdIn(feedItemIds).stream()
                        .collect(Collectors.groupingBy(Reaction::getFeedItemId));

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
                    try {
                        m.put("eventData", mapper.readTree(
                                fi.getEventData() != null ? fi.getEventData() : "{}"));
                    } catch (Exception ex) {
                        m.put("eventData", mapper.createObjectNode());
                    }

                    // Reactions
                    List<Reaction> itemReactions = reactionsByItem.getOrDefault(fi.getId(), List.of());
                    Map<String, Long> counts = new LinkedHashMap<>();
                    counts.put("STRONG", 0L);
                    counts.put("RESPECT", 0L);
                    counts.put("KEEP_GOING", 0L);
                    counts.put("COMMENDABLE", 0L);
                    itemReactions.forEach(r -> counts.merge(r.getKind(), 1L, Long::sum));
                    m.put("reactions",  counts);
                    m.put("myReaction", itemReactions.stream()
                            .filter(r -> r.getUserId().equals(currentUserId))
                            .map(Reaction::getKind).findFirst().orElse(null));

                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── POST /groups/feed/{feedItemId}/react ──────────────────────────
    @PostMapping("/feed/{feedItemId}/react")
    @Transactional
    public ResponseEntity<ApiResponse<?>> react(
            @PathVariable UUID feedItemId,
            @RequestBody @Valid ReactRequest request,
            Authentication auth) {

        UUID userId = userId(auth);

        FeedItem fi = feedRepo.findById(feedItemId)
                .orElseThrow(() -> ApiException.notFound("FeedItem"));
        requireMembership(fi.getGroupId(), userId);

        // Toggle logic: same type → remove, different type → update, none → insert
        Optional<Reaction> existing = reactionRepo.findByFeedItemIdAndUserId(feedItemId, userId);
        if (existing.isPresent()) {
            if (existing.get().getKind().equals(request.type())) {
                reactionRepo.delete(existing.get());
            } else {
                existing.get().setKind(request.type());
                reactionRepo.save(existing.get());
            }
        } else {
            Reaction r = new Reaction();
            r.setFeedItemId(feedItemId);
            r.setUserId(userId);
            r.setKind(request.type());
            reactionRepo.save(r);
        }

        // Build response with counts + myReaction
        List<Reaction> allReactions = reactionRepo.findByFeedItemIdIn(List.of(feedItemId));
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("STRONG", 0L);
        counts.put("RESPECT", 0L);
        counts.put("KEEP_GOING", 0L);
        counts.put("COMMENDABLE", 0L);
        allReactions.forEach(r -> counts.merge(r.getKind(), 1L, Long::sum));

        String myReaction = reactionRepo.findByFeedItemIdAndUserId(feedItemId, userId)
                .map(Reaction::getKind).orElse(null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reactions",  counts);
        response.put("myReaction", myReaction);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ── GET /groups/pulse ─────────────────────────────────────────────
    @GetMapping("/pulse")
    public ResponseEntity<ApiResponse<?>> pulse(Authentication auth) {
        UUID userId = userId(auth);
        Instant startOfToday = Zones.fitnessDayStart(Zones.fitnessDayNow());

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
    public ResponseEntity<?> poke(
            @PathVariable UUID id,
            @PathVariable UUID memberId,
            Authentication auth) {

        UUID pokerId = userId(auth);
        requireMembership(id, pokerId);
        requireMembership(id, memberId);

        LocalDate today = LocalDate.now(Zones.APP_ZONE);

        // Rate-limit: one poke per recipient per group per IST day
        if (pokeLogRepo.existsByGroupIdAndRecipientUserIdAndPokedDate(id, memberId, today)) {
            LocalDate tomorrow = today.plusDays(1);
            Instant retryAfter = tomorrow.atStartOfDay(Zones.APP_ZONE).toInstant();
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("alreadyPokedToday", true, "retryAfter", retryAfter.toString()));
        }

        String pokerName = userRepo.findById(pokerId)
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : "Someone")
                .orElse("Someone");
        String pokerFirstName = pokerName.trim().split("\\s+")[0];

        Group group = groupRepo.findById(id).orElse(null);
        String groupName = group != null ? group.getName() : "your group";
        int weeklyGoal = group != null && group.getWeeklyGoal() != null ? group.getWeeklyGoal() : 4;

        // Sessions completed this IST week by the recipient
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        int completed = sessionRepo.countByUserIdAndStatusAndFinishedAtBetween(
                memberId, "COMPLETED",
                weekStart.atStartOfDay(Zones.APP_ZONE).toInstant(),
                Instant.now());
        int sessionsRemaining = Math.max(0, weeklyGoal - completed);

        // Write notification — no feed item (pokes are private, not in feed)
        Notification notif = new Notification();
        notif.setUserId(memberId);
        notif.setType("POKE");
        notif.setTitle(pokerFirstName + " poked you \uD83D\uDC4B");
        notif.setBody(pokerFirstName + " poked you in " + groupName
                + " \u2014 " + sessionsRemaining
                + " session" + (sessionsRemaining != 1 ? "s" : "") + " to go \uD83D\uDCAA");
        notifRepo.save(notif);

        // Record the poke so rate-limit fires on subsequent attempts
        PokeLog pl = new PokeLog();
        pl.setGroupId(id);
        pl.setRecipientUserId(memberId);
        pl.setPokerUserId(pokerId);
        pl.setPokedDate(today);
        pokeLogRepo.save(pl);

        return ResponseEntity.ok(ApiResponse.success(Map.of("poked", true)));
    }

    // ── GET /groups/{groupId}/weekly-progress ────────────────────────
    @GetMapping("/{groupId}/weekly-progress")
    public ResponseEntity<ApiResponse<?>> getWeeklyProgress(
            @PathVariable UUID groupId, Authentication auth) {

        UUID userId = userId(auth);
        requireMembership(groupId, userId);
        GroupWeeklyProgressDto dto = groupProgressService.getProgressForGroup(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    // ── GET /groups/{groupId}/cards ──────────────────────────────────
    @GetMapping("/{groupId}/cards")
    public ResponseEntity<ApiResponse<?>> getWeeklyCards(
            @PathVariable UUID groupId, Authentication auth) {

        UUID userId = userId(auth);
        requireMembership(groupId, userId);
        List<GroupWeeklyCardDto> cards = groupWeeklyCardService.getCardsForGroup(groupId)
                .stream().map(GroupWeeklyCardDto::from).toList();
        return ResponseEntity.ok(ApiResponse.success(cards));
    }

    // ── GET /groups/me/all-progress ──────────────────────────────────
    @GetMapping("/me/all-progress")
    public ResponseEntity<ApiResponse<?>> getAllGroupsProgress(Authentication auth) {
        UUID userId = userId(auth);
        List<GroupCarouselDto> result = groupProgressService.getMyAllGroupsProgress(userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── GET /groups/{groupId}/top-performer ──────────────────────────
    @GetMapping("/{groupId}/top-performer")
    public ResponseEntity<ApiResponse<?>> getTopPerformer(
            @PathVariable UUID groupId,
            @RequestParam(required = false) Integer isoYear,
            @RequestParam(required = false) Integer isoWeek,
            Authentication auth) {

        UUID userId = userId(auth);
        requireMembership(groupId, userId);

        GroupWeeklyTopPerformer tp;
        if (isoYear != null && isoWeek != null) {
            tp = topPerformerRepo.findByGroupIdAndIsoYearAndIsoWeekAndDimension(groupId, isoYear, isoWeek, "EFFORT")
                    .orElse(null);
        } else {
            tp = topPerformerRepo.findTopByGroupIdAndDimensionOrderByIsoYearDescIsoWeekDesc(groupId, "EFFORT")
                    .orElse(null);
        }

        if (tp == null) return ResponseEntity.ok(ApiResponse.success(null));

        String displayName = userRepo.findById(tp.getWinnerUserId())
                .map(User::getDisplayName)
                .orElse("Unknown");

        TopPerformerDto dto = new TopPerformerDto();
        dto.setWinnerUserId(tp.getWinnerUserId());
        dto.setWinnerDisplayName(displayName);
        dto.setWinnerAvatarInitials(avatarInitials(displayName));
        dto.setDimension(tp.getDimension());
        dto.setScoreValue(tp.getScoreValue());
        dto.setMetricLabel(tp.getMetricLabel());
        dto.setIsoYear(tp.getIsoYear());
        dto.setIsoWeek(tp.getIsoWeek());
        dto.setCurrentUser(tp.getWinnerUserId().equals(userId));
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    // ── GET /groups/{groupId}/leaderboard ───────────────────────────
    @GetMapping("/{groupId}/leaderboard")
    public ResponseEntity<ApiResponse<?>> getLeaderboard(
            @PathVariable UUID groupId,
            @RequestParam String type,
            @RequestParam(required = false) String week,
            Authentication auth) {

        UUID userId = userId(auth);
        requireMembership(groupId, userId);

        LocalDate weekStart = parseWeekOrDefault(week);
        LeaderboardResponseDto dto = leaderboardService.getLeaderboard(groupId, type, weekStart, userId);
        return ResponseEntity.ok(ApiResponse.success(dto));
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

    /**
     * Parses optional "YYYY-Www" week parameter (e.g. "2026-W18") into that week's Monday.
     * Defaults to the current IST week's Monday when the parameter is absent or blank.
     */
    private static LocalDate parseWeekOrDefault(String week) {
        if (week == null || week.isBlank()) {
            return LocalDate.now(Zones.APP_ZONE)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        String[] parts = week.split("-W");
        if (parts.length != 2) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "week must be in format YYYY-Www (e.g. 2026-W18)");
        }
        try {
            int year    = Integer.parseInt(parts[0]);
            int weekNum = Integer.parseInt(parts[1]);
            return TopPerformerService.mondayOfIsoWeek(year, weekNum);
        } catch (NumberFormatException e) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "week must be in format YYYY-Www (e.g. 2026-W18)");
        }
    }

    private static String avatarInitials(String displayName) {
        if (displayName == null || displayName.isBlank()) return "?";
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].charAt(0) + "" + parts[parts.length - 1].charAt(0)).toUpperCase();
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
