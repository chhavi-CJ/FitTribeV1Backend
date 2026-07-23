package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.entity.Notification;
import com.fittribe.api.entity.User;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.NotificationRepository;
import com.fittribe.api.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationRepository notifRepo;
    private final UserRepository         userRepo;

    public NotificationController(NotificationRepository notifRepo, UserRepository userRepo) {
        this.notifRepo = notifRepo;
        this.userRepo  = userRepo;
    }

    // ── GET /notifications[?unread=true] ──────────────────────────────
    @GetMapping
    public ResponseEntity<ApiResponse<?>> list(
            @RequestParam(required = false) Boolean unread,
            Authentication auth) {

        UUID userId = userId(auth);
        PageRequest page = PageRequest.of(0, 30);

        List<Notification> notifs = Boolean.TRUE.equals(unread)
                ? notifRepo.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(userId, page)
                : notifRepo.findByRecipientIdOrderByCreatedAtDesc(userId, page);

        // Batch-load active actors in one query
        Set<UUID> actorIds = notifs.stream()
                .map(Notification::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, User> actorsById = actorIds.isEmpty()
                ? Map.of()
                : userRepo.findActiveByIdIn(actorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> items = notifs.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",         n.getId());
            m.put("type",       n.getType());
            m.put("actor",      buildActor(n.getActorId(), actorsById));
            m.put("feedItemId", n.getFeedItemId());
            m.put("groupId",    n.getGroupId());
            m.put("metadata",   n.getMetadata());
            m.put("readAt",     n.getReadAt());
            m.put("createdAt",  n.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(items));
    }

    // ── GET /notifications/unread-count ───────────────────────────────
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<?>> unreadCount(Authentication auth) {
        long count = notifRepo.countByRecipientIdAndReadAtIsNull(userId(auth));
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    // ── POST /notifications/read-all ──────────────────────────────────
    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<ApiResponse<?>> readAll(Authentication auth) {
        notifRepo.markAllReadByRecipientId(userId(auth));
        return ResponseEntity.ok(ApiResponse.success(Map.of("marked", true)));
    }

    // ── POST /notifications/{id}/read ─────────────────────────────────
    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<ApiResponse<?>> readOne(
            @PathVariable UUID id, Authentication auth) {

        Notification notif = notifRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notification"));
        // Return 404 (not 403) so callers cannot probe for other users' notification IDs
        if (!notif.getRecipientId().equals(userId(auth)))
            throw ApiException.notFound("Notification");

        notif.setReadAt(OffsetDateTime.now());
        notifRepo.save(notif);
        return ResponseEntity.ok(ApiResponse.success(Map.of("marked", true)));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private Map<String, Object> buildActor(UUID actorId, Map<UUID, User> actorsById) {
        Map<String, Object> actor = new LinkedHashMap<>();
        if (actorId == null || !actorsById.containsKey(actorId)) {
            actor.put("id",          actorId);
            actor.put("displayName", "Deleted user");
            actor.put("avatarUrl",   null);
        } else {
            User u = actorsById.get(actorId);
            actor.put("id",          u.getId());
            actor.put("displayName", u.getDisplayName() != null ? u.getDisplayName() : "Someone");
            actor.put("avatarUrl",   null);
        }
        return actor;
    }

    private UUID userId(Authentication auth) {
        return (UUID) auth.getPrincipal();
    }
}
