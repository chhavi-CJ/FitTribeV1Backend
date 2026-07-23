package com.fittribe.api.service;

import com.fittribe.api.entity.Notification;
import com.fittribe.api.repository.DeviceTokenRepository;
import com.fittribe.api.repository.GroupMemberRepository;
import com.fittribe.api.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private DeviceTokenRepository  deviceTokenRepo;
    private GroupMemberRepository  groupMemberRepo;
    private NotificationRepository notificationRepo;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        deviceTokenRepo  = mock(DeviceTokenRepository.class);
        groupMemberRepo  = mock(GroupMemberRepository.class);
        notificationRepo = mock(NotificationRepository.class);
        service = new NotificationService(deviceTokenRepo, groupMemberRepo, notificationRepo);
    }

    @Test
    void notifyUser_savesInAppNotificationWithCorrectFields() {
        UUID recipientId = UUID.randomUUID();
        UUID actorId     = UUID.randomUUID();
        UUID groupId     = UUID.randomUUID();

        service.notifyUser(recipientId, "POKE", "Title", "Body",
                actorId, groupId, Map.of("type", "POKE"), false);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals(recipientId, saved.getRecipientId());
        assertEquals(actorId,     saved.getActorId());
        assertEquals(groupId,     saved.getGroupId());
        assertEquals("POKE",      saved.getType());
        assertEquals("Title",     saved.getMetadata().get("title"));
        assertEquals("Body",      saved.getMetadata().get("body"));
    }

    @Test
    void notifyUser_whenSendPushFalse_doesNotQueryDeviceTokens() {
        service.notifyUser(UUID.randomUUID(), "POKE", "T", "B",
                null, null, Map.of(), false);

        verify(notificationRepo).save(any());
        verifyNoInteractions(deviceTokenRepo);
    }

    @Test
    void notifyUser_whenFirebaseAbsent_stillSavesInAppNotification() {
        // Firebase is not initialized in unit tests (no FirebaseApp).
        // sendPush gracefully skips FCM but the in-app record must still be saved.
        service.notifyUser(UUID.randomUUID(), "POKE", "T", "B",
                null, null, Map.of(), true);

        verify(notificationRepo).save(any());
    }

    @Test
    void sendToUsers_emptyList_doesNothing() {
        service.sendToUsers(List.of(), "T", "B", Map.of());
        verifyNoInteractions(deviceTokenRepo);
    }

    @Test
    void sendToUsers_withUsers_doesNotThrow() {
        // Firebase is absent in unit tests, so FCM delivery is skipped gracefully.
        // Verify sendToUsers does not propagate exceptions for any user.
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        assertDoesNotThrow(() -> service.sendToUsers(List.of(u1, u2), "T", "B", Map.of()));
    }
}
