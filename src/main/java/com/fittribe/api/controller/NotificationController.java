package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.entity.Notification;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationRepository notifRepo;

    public NotificationController(NotificationRepository notifRepo) {
        this.notifRepo = notifRepo;
    }

    // ── GET /notifications ────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<ApiResponse<?>> list(
            @RequestParam(required = false) Boolean unread,
            Authentication auth) {
        UUID userId = userId(auth);

        List<Notification> notifs = Boolean.TRUE.equals(unread)
                ? notifRepo.findTop30ByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                : notifRepo.findTop30ByUserIdOrderByCreatedAtDesc(userId);
        long unreadCount = notifRepo.countByUserIdAndIsReadFalse(userId);

        List<Map<String, Object>> items = notifs.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",        n.getId());
            m.put("type",      n.getType());
            m.put("title",     n.getTitle());
            m.put("body",      n.getBody());
            m.put("isRead",    n.getIsRead());
            m.put("createdAt", n.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("unreadCount",    unreadCount);
        data.put("notifications",  items);

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // ── PATCH /notifications/read-all ─────────────────────────────────
    @PatchMapping("/read-all")
    @Transactional
    public ResponseEntity<ApiResponse<?>> readAll(Authentication auth) {
        notifRepo.markAllReadByUserId(userId(auth));
        return ResponseEntity.ok(ApiResponse.success(Map.of("marked", true)));
    }

    // ── PATCH /notifications/{id}/read ────────────────────────────────
    @PatchMapping("/{id}/read")
    @Transactional
    public ResponseEntity<ApiResponse<?>> readOne(
            @PathVariable UUID id, Authentication auth) {

        Notification notif = notifRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notification"));
        if (!notif.getUserId().equals(userId(auth))) throw ApiException.forbidden();

        notif.setIsRead(true);
        notifRepo.save(notif);
        return ResponseEntity.ok(ApiResponse.success(Map.of("marked", true)));
    }

    private UUID userId(Authentication auth) {
        return (UUID) auth.getPrincipal();
    }
}
