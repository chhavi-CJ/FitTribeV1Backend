package com.fittribe.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittribe.api.entity.AnalyticsEvent;
import com.fittribe.api.repository.AnalyticsEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final AnalyticsEventRepository analyticsEventRepo;
    private final ObjectMapper objectMapper;

    public AnalyticsService(AnalyticsEventRepository analyticsEventRepo,
                           ObjectMapper objectMapper) {
        this.analyticsEventRepo = analyticsEventRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Asynchronously track an analytics event for a user.
     *
     * Fire-and-forget: failures are logged but do not block the caller.
     * The event is saved to the analytics_events table for later analysis.
     *
     * @param userId     the user who triggered the event
     * @param eventName  the event type (e.g., "WORKOUT_STARTED", "PROFILE_VIEWED")
     * @param properties optional key-value data to store with the event
     */
    @Async
    public void track(UUID userId, String eventName, Map<String, Object> properties) {
        try {
            AnalyticsEvent event = new AnalyticsEvent(userId, eventName);

            if (properties != null && !properties.isEmpty()) {
                event.setEventProperties(objectMapper.valueToTree(properties));
            } else {
                event.setEventProperties(objectMapper.createObjectNode());
            }

            analyticsEventRepo.save(event);
            log.debug("Analytics event tracked: userId={} eventName={}", userId, eventName);

        } catch (Exception e) {
            log.warn("Failed to track analytics event: userId={} eventName={}: {}",
                    userId, eventName, e.getMessage());
        }
    }

    /**
     * Track an analytics event with session context.
     *
     * @param userId      the user who triggered the event
     * @param eventName   the event type
     * @param sessionId   optional session ID (e.g., workout session ID)
     * @param properties  optional key-value data
     */
    @Async
    public void trackWithSession(UUID userId, String eventName, String sessionId,
                                 Map<String, Object> properties) {
        try {
            AnalyticsEvent event = new AnalyticsEvent(userId, eventName);
            event.setSessionId(sessionId);

            if (properties != null && !properties.isEmpty()) {
                event.setEventProperties(objectMapper.valueToTree(properties));
            } else {
                event.setEventProperties(objectMapper.createObjectNode());
            }

            analyticsEventRepo.save(event);
            log.debug("Analytics event tracked: userId={} eventName={} sessionId={}",
                    userId, eventName, sessionId);

        } catch (Exception e) {
            log.warn("Failed to track analytics event: userId={} eventName={} sessionId={}: {}",
                    userId, eventName, sessionId, e.getMessage());
        }
    }

    /**
     * Track an analytics event with platform and version context.
     *
     * @param userId      the user who triggered the event
     * @param eventName   the event type
     * @param properties  optional key-value data
     * @param platform    the platform (e.g., "iOS", "Android", "Web")
     * @param appVersion  the app version string
     */
    @Async
    public void trackWithContext(UUID userId, String eventName, Map<String, Object> properties,
                                 String platform, String appVersion) {
        try {
            AnalyticsEvent event = new AnalyticsEvent(userId, eventName);
            event.setPlatform(platform);
            event.setAppVersion(appVersion);

            if (properties != null && !properties.isEmpty()) {
                event.setEventProperties(objectMapper.valueToTree(properties));
            } else {
                event.setEventProperties(objectMapper.createObjectNode());
            }

            analyticsEventRepo.save(event);
            log.debug("Analytics event tracked: userId={} eventName={} platform={} version={}",
                    userId, eventName, platform, appVersion);

        } catch (Exception e) {
            log.warn("Failed to track analytics event: userId={} eventName={}: {}",
                    userId, eventName, e.getMessage());
        }
    }
}
