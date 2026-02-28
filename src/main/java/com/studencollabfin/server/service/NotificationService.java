package com.studencollabfin.server.service;

import com.studencollabfin.server.dto.EventNotificationDTO;
import com.studencollabfin.server.gamification.event.NotificationReadEvent;
import com.studencollabfin.server.model.Inbox;
import com.studencollabfin.server.repository.InboxRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class NotificationService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private InboxRepository inboxRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @SuppressWarnings("null")
    public void notifyPodMembers(String podId, String message) {
        if (podId != null) {
            messagingTemplate.convertAndSend("/topic/pod/" + podId, (Object) message);
        }
    }

    public void notifyUser(String userId, Object notification) {
        if (userId != null && notification != null) {
            messagingTemplate.convertAndSend("/queue/user/" + userId, notification);
        }
    }

    public void notifyPodUpdate(String podId, Object update) {
        if (podId != null && update != null) {
            messagingTemplate.convertAndSend("/topic/pod/" + podId + "/updates", update);
        }
    }

    public void notifyBuddyBeacon(String userId, Object beaconData) {
        if (beaconData != null) {
            messagingTemplate.convertAndSend("/topic/campus/beacons", beaconData);
        }
    }

    /**
     * ✅ NEW EVENT NOTIFICATION: Notify all users when a new event is published
     * 
     * @param eventNotification The event notification DTO with event details
     * @param allUserIds        List of all user IDs to notify
     */
    public void broadcastEventNotification(EventNotificationDTO eventNotification, java.util.List<String> allUserIds) {
        if (eventNotification == null || allUserIds == null || allUserIds.isEmpty()) {
            return;
        }

        // Send WebSocket notification to all users
        for (String userId : allUserIds) {
            messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", eventNotification);

            // Also save to Inbox for persistent storage
            Inbox inboxItem = new Inbox();
            inboxItem.setUserId(userId);
            inboxItem.setType(Inbox.NotificationType.POD_EVENT); // Reusing POD_EVENT type for all events
            inboxItem.setTitle("New Event: " + eventNotification.getEventTitle());
            inboxItem.setMessage(eventNotification.getMessage());
            inboxItem.setSeverity(Inbox.NotificationSeverity.LOW);
            inboxItem.setPostId(eventNotification.getEventId());
            inboxItem.setSenderId("system");
            inboxItem.setCreatedAt(LocalDateTime.now());
            inboxItem.setTimestamp(LocalDateTime.now());
            inboxItem.setRead(false);

            inboxRepository.save(inboxItem);
        }
    }

    /**
     * ✅ DOMAIN-SPECIFIC EVENT NOTIFICATION: Notify only users in a specific domain
     * 
     * @param eventNotification The event notification DTO
     * @param domainUserIds     List of user IDs in the specific domain
     */
    public void broadcastEventNotificationToDomaIn(EventNotificationDTO eventNotification,
            java.util.List<String> domainUserIds) {
        broadcastEventNotification(eventNotification, domainUserIds);
    }

    @SuppressWarnings("null")
    public Inbox markNotificationAsRead(String notificationId) {
        Inbox inbox = inboxRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Inbox item not found: " + notificationId));

        if (inbox.isRead()) {
            return inbox;
        }

        LocalDateTime readAt = LocalDateTime.now();
        LocalDateTime createdAt = inbox.getCreatedAt() != null
                ? inbox.getCreatedAt()
                : (inbox.getTimestamp() != null ? inbox.getTimestamp() : readAt);
        long timeToReadInMinutes = Math.max(0, ChronoUnit.MINUTES.between(createdAt, readAt));

        inbox.setRead(true);
        Inbox updated = inboxRepository.save(inbox);

        String notificationType = "UNKNOWN";
        if (updated.getType() != null) {
            notificationType = updated.getType() == Inbox.NotificationType.POD_EVENT
                    ? "EVENT"
                    : updated.getType().name();
        }

        eventPublisher.publishEvent(new NotificationReadEvent(
                updated.getUserId(),
                updated.getId(),
                updated.getPostId(),
                notificationType,
                timeToReadInMinutes));

        return updated;
    }
}
