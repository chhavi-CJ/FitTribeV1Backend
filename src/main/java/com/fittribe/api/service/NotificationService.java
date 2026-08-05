package com.fittribe.api.service;

import com.fittribe.api.entity.DeviceToken;
import com.fittribe.api.entity.GroupMember;
import com.fittribe.api.entity.Notification;
import com.fittribe.api.entity.NotificationPreference;
import com.fittribe.api.repository.DeviceTokenRepository;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.NotificationPreferenceRepository;
import com.fittribe.api.repository.NotificationRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DeviceTokenRepository         deviceTokenRepo;
    private final GroupMemberRepository         groupMemberRepo;
    private final NotificationRepository        notificationRepo;
    private final NotificationPreferenceRepository prefRepo;

    public NotificationService(DeviceTokenRepository deviceTokenRepo,
                               GroupMemberRepository groupMemberRepo,
                               NotificationRepository notificationRepo,
                               NotificationPreferenceRepository prefRepo) {
        this.deviceTokenRepo  = deviceTokenRepo;
        this.groupMemberRepo  = groupMemberRepo;
        this.notificationRepo = notificationRepo;
        this.prefRepo         = prefRepo;
    }

    /**
     * Fan-out push to every member of a group, optionally excluding one user.
     * Used for events like MEMBER_JOINED, MEMBER_LEFT, NEW_WORKOUT_FEED_ITEM.
     * Failures for individual users are logged but do not stop the fan-out.
     */
    public void sendPushToGroupExceptUser(UUID groupId, UUID excludeUserId,
                                          String title, String body,
                                          Map<String, String> data) {
        List<GroupMember> members = groupMemberRepo.findByGroupId(groupId);
        for (GroupMember m : members) {
            if (excludeUserId != null && excludeUserId.equals(m.getUserId())) continue;
            try {
                sendPush(m.getUserId(), title, body, data);
            } catch (Exception e) {
                log.warn("Push fan-out failed for user={} in group={}", m.getUserId(), groupId, e);
            }
        }
    }

    /**
     * Send a push notification to the most recently used device for the given user.
     *
     * Never throws — push failures are logged at WARN and swallowed so callers
     * are never blocked by FCM outages or stale tokens.
     *
     * Stale tokens (UNREGISTERED / INVALID_ARGUMENT) are deleted automatically.
     *
     * Device selection: Prefers the device with the most recent lastSeenAt.
     * If lastSeenAt is null (old tokens), falls back to createdAt.
     * This ensures only one push per user, targeting their most active device.
     */
    public void sendPush(UUID userId, String title, String body, Map<String, String> data) {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                log.debug("sendPush skipped — Firebase not initialised (placeholder mode)");
                return;
            }

            List<DeviceToken> tokens = deviceTokenRepo.findByUserId(userId);
            if (tokens.isEmpty()) {
                log.warn("sendPush: no device tokens found for userId={}", userId);
                return;
            }

            DeviceToken mostRecentToken = tokens.stream()
                    .max((a, b) -> {
                        var aTime = a.getLastSeenAt() != null ? a.getLastSeenAt() : a.getCreatedAt();
                        var bTime = b.getLastSeenAt() != null ? b.getLastSeenAt() : b.getCreatedAt();
                        return aTime.compareTo(bTime);
                    })
                    .orElse(tokens.get(0));

            log.debug("sendPush: user={} has {} device(s), sending to most recent: {}",
                    userId, tokens.size(), mostRecentToken.getId());

            log.info("sendPush: attempting push to userId={} title={}", userId, title);
            FirebaseMessaging fcm = FirebaseMessaging.getInstance();
            sendToToken(fcm, mostRecentToken, userId, title, body, data);
        } catch (Exception e) {
            log.warn("sendPush: unexpected error for user={}: {}", userId, e.getMessage());
        }
    }

    /**
     * Check whether a push notification should be sent to this user for this type.
     * Evaluates preferences and quiet hours (in IST).
     *
     * Notification types map to preference categories:
     *   STREAK_RISK, STREAK_MILESTONE, STREAK_BROKEN, STREAK_FREEZE_USED → streakEnabled
     *   GROUP_GOAL_HIT, GROUP_MEMBER_LOGGED, GROUP_MEMBER_JOINED, GROUP_TRAINED_WITHOUT_YOU → groupActivityEnabled
     *   WEEKLY_REPORT_READY, WEEKLY_GOAL_HIT → weeklyReportEnabled
     *   POKE → socialEnabled
     *   COMEBACK_NUDGE → comebackEnabled
     */
    private boolean shouldSendPush(UUID userId, String type) {
        NotificationPreference prefs = prefRepo.findByUserId(userId)
                .orElse(new NotificationPreference(userId));

        // Check if category is enabled
        boolean categoryEnabled = switch (type) {
            case "STREAK_RISK", "STREAK_MILESTONE", "STREAK_BROKEN", "STREAK_FREEZE_USED"
                    -> prefs.getStreakEnabled();
            case "GROUP_GOAL_HIT", "GROUP_MEMBER_LOGGED", "GROUP_MEMBER_JOINED", "GROUP_TRAINED_WITHOUT_YOU"
                    -> prefs.getGroupActivityEnabled();
            case "WEEKLY_REPORT_READY", "WEEKLY_GOAL_HIT"
                    -> prefs.getWeeklyReportEnabled();
            case "POKE" -> prefs.getSocialEnabled();
            case "COMEBACK_NUDGE" -> prefs.getComebackEnabled();
            default -> true;  // Unknown types always send
        };

        if (!categoryEnabled) return false;

        // Check quiet hours
        if (!prefs.getQuietHoursEnabled()) return true;

        LocalTime now = LocalTime.now(IST);
        LocalTime start = prefs.getQuietStart();
        LocalTime end = prefs.getQuietEnd();

        // Quiet hours spanning midnight (e.g., 22:00 → 07:00)
        if (start.isAfter(end)) {
            return !(now.isAfter(start) || now.isBefore(end));
        } else {
            return !(now.isAfter(start) && now.isBefore(end));
        }
    }

    /**
     * Single entry point for creating any in-app notification, with optional push.
     *
     * Saves a row to the notifications table so it appears in the user's
     * notification feed, then (when sendPush=true) fires an FCM push to every
     * registered device for that user, subject to user preferences and quiet hours.
     *
     * @param actorId  nullable — the user who caused the event (null for system alerts)
     * @param groupId  nullable — relevant group, if any
     * @param data     key-value pairs forwarded in the FCM data payload (deep-link info etc.)
     * @param sendPush whether to also send an FCM push on top of the in-app record
     */
    public void notifyUser(UUID recipientId, String type, String title, String body,
                           UUID actorId, UUID groupId,
                           Map<String, String> data, boolean sendPush) {
        try {
            Notification notif = new Notification();
            notif.setRecipientId(recipientId);
            notif.setActorId(actorId);
            notif.setGroupId(groupId);
            notif.setType(type);
            notif.setMetadata(Map.of("title", title, "body", body));
            notificationRepo.save(notif);
        } catch (Exception e) {
            log.warn("notifyUser: failed to save in-app notification type={} for user={}: {}",
                    type, recipientId, e.getMessage());
        }

        if (sendPush && shouldSendPush(recipientId, type)) {
            sendPush(recipientId, title, body, data != null ? data : Map.of());
        }
    }

    /**
     * Batch variant of {@link #sendPush}: sends an FCM push to every registered
     * device for each user in the list. Only sends push — does not create
     * in-app notification records.
     */
    public void sendToUsers(List<UUID> userIds, String title, String body,
                            Map<String, String> data) {
        if (userIds == null || userIds.isEmpty()) return;
        for (UUID userId : userIds) {
            try {
                sendPush(userId, title, body, data);
            } catch (Exception e) {
                log.warn("sendToUsers: push failed for user={}: {}", userId, e.getMessage());
            }
        }
    }

    private void sendToToken(FirebaseMessaging fcm, DeviceToken dt, UUID userId,
                              String title, String body, Map<String, String> data) {
        String tokenPrefix = dt.getToken().substring(0, Math.min(8, dt.getToken().length()));
        try {
            Message.Builder builder = Message.builder()
                    .setToken(dt.getToken())
                    .setNotification(com.google.firebase.messaging.Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }

            String messageId = fcm.send(builder.build());
            log.info("sendPush: push delivered to userId={} token={}... messageId={}", userId, tokenPrefix, messageId);

        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("sendPush: stale token for user={} ({}), removing token={}...", userId, code, tokenPrefix);
                try {
                    deviceTokenRepo.deleteByUserIdAndToken(userId, dt.getToken());
                } catch (Exception del) {
                    log.warn("sendPush: could not delete stale token for user={}: {}", userId, del.getMessage());
                }
            } else {
                log.error("sendToToken: FCM push failed for userId={} token={}... code={}: {}", userId, tokenPrefix, code, e.getMessage(), e);
            }
        }
    }
}
