package com.fittribe.api.service;

import com.fittribe.api.entity.DeviceToken;
import com.fittribe.api.repository.DeviceTokenRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final DeviceTokenRepository deviceTokenRepo;

    public NotificationService(DeviceTokenRepository deviceTokenRepo) {
        this.deviceTokenRepo = deviceTokenRepo;
    }

    /**
     * Send a push notification to every registered device for the given user.
     *
     * Never throws — push failures are logged at WARN and swallowed so callers
     * are never blocked by FCM outages or stale tokens.
     *
     * Stale tokens (UNREGISTERED / INVALID_ARGUMENT) are deleted automatically.
     */
    public void sendPush(UUID userId, String title, String body, Map<String, String> data) {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                log.debug("sendPush skipped — Firebase not initialised (placeholder mode)");
                return;
            }

            List<DeviceToken> tokens = deviceTokenRepo.findByUserId(userId);
            if (tokens.isEmpty()) {
                log.debug("sendPush: no registered devices for user={}", userId);
                return;
            }

            FirebaseMessaging fcm = FirebaseMessaging.getInstance();
            for (DeviceToken dt : tokens) {
                sendToToken(fcm, dt, userId, title, body, data);
            }
        } catch (Exception e) {
            log.warn("sendPush: unexpected error for user={}: {}", userId, e.getMessage());
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
            log.debug("sendPush: delivered user={} token={}... messageId={}", userId, tokenPrefix, messageId);

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
                log.warn("sendPush: FCM error for user={} token={}... code={}: {}",
                        userId, tokenPrefix, code, e.getMessage());
            }
        }
    }
}
