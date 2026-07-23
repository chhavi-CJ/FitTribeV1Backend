package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.entity.NotificationPreference;
import com.fittribe.api.repository.NotificationPreferenceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceRepository repo;

    public NotificationPreferenceController(NotificationPreferenceRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();

        NotificationPreference prefs = repo.findByUserId(userId)
                .orElse(new NotificationPreference(userId));

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "streakReminders", prefs.getStreakEnabled(),
                "groupActivity", prefs.getGroupActivityEnabled(),
                "weeklyReport", prefs.getWeeklyReportEnabled(),
                "pokes", prefs.getSocialEnabled(),
                "comebackNudges", prefs.getComebackEnabled(),
                "quietHoursEnabled", prefs.getQuietHoursEnabled(),
                "quietFrom", prefs.getQuietStart().toString(),
                "quietTo", prefs.getQuietEnd().toString()
        )));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(
            Authentication auth,
            @RequestBody Map<String, Object> body) {
        UUID userId = (UUID) auth.getPrincipal();

        NotificationPreference prefs = repo.findByUserId(userId)
                .orElse(new NotificationPreference(userId));

        if (body.containsKey("streakReminders")) {
            prefs.setStreakEnabled((Boolean) body.get("streakReminders"));
        }
        if (body.containsKey("groupActivity")) {
            prefs.setGroupActivityEnabled((Boolean) body.get("groupActivity"));
        }
        if (body.containsKey("weeklyReport")) {
            prefs.setWeeklyReportEnabled((Boolean) body.get("weeklyReport"));
        }
        if (body.containsKey("pokes")) {
            prefs.setSocialEnabled((Boolean) body.get("pokes"));
        }
        if (body.containsKey("comebackNudges")) {
            prefs.setComebackEnabled((Boolean) body.get("comebackNudges"));
        }
        if (body.containsKey("quietHoursEnabled")) {
            prefs.setQuietHoursEnabled((Boolean) body.get("quietHoursEnabled"));
        }
        if (body.containsKey("quietFrom")) {
            prefs.setQuietStart(LocalTime.parse((String) body.get("quietFrom")));
        }
        if (body.containsKey("quietTo")) {
            prefs.setQuietEnd(LocalTime.parse((String) body.get("quietTo")));
        }

        repo.save(prefs);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "streakReminders", prefs.getStreakEnabled(),
                "groupActivity", prefs.getGroupActivityEnabled(),
                "weeklyReport", prefs.getWeeklyReportEnabled(),
                "pokes", prefs.getSocialEnabled(),
                "comebackNudges", prefs.getComebackEnabled(),
                "quietHoursEnabled", prefs.getQuietHoursEnabled(),
                "quietFrom", prefs.getQuietStart().toString(),
                "quietTo", prefs.getQuietEnd().toString()
        )));
    }
}
