package com.fittribe.api.controller;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.dto.request.TrackEventRequest;
import com.fittribe.api.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Analytics event tracking endpoint for authenticated users.
 *
 * <p>Receives analytics events from the frontend and logs them asynchronously
 * for later analysis. All event tracking is fire-and-forget: failures are logged
 * internally but never returned to the client.
 *
 * <h3>Auth</h3>
 * All endpoints require authentication. User identity is extracted from the
 * JWT via {@code (UUID) auth.getPrincipal()}.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Track an analytics event from the frontend.
     *
     * <p>Accepts an event name and optional properties, and saves the event
     * asynchronously to the analytics_events table. The authenticated user ID
     * is automatically captured.
     *
     * <p>Response is always {@code 200 OK} with a success envelope, regardless
     * of whether the event was actually saved (failures are logged internally).
     *
     * @param auth              the authenticated user context
     * @param trackEventRequest the event name and optional properties
     * @return {@code 200} with success response
     */
    @PostMapping("/track")
    public ResponseEntity<ApiResponse<Map<String, String>>> track(
            Authentication auth,
            @RequestBody TrackEventRequest trackEventRequest) {

        UUID userId = (UUID) auth.getPrincipal();

        // Validate event name
        if (trackEventRequest.eventName() == null || trackEventRequest.eventName().isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "status", "invalid_request",
                    "message", "eventName is required")));
        }

        // Dispatch async tracking (fire-and-forget)
        analyticsService.track(userId, trackEventRequest.eventName(),
                trackEventRequest.properties());

        log.debug("Analytics event enqueued: userId={} eventName={}",
                userId, trackEventRequest.eventName());

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "status", "accepted",
                "message", "Event queued for processing")));
    }
}
