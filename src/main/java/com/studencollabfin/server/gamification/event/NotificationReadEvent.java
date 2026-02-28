package com.studencollabfin.server.gamification.event;

public record NotificationReadEvent(
        String userId,
        String notificationId,
        String eventId,
        String notificationType,
        long timeToReadInMinutes) {
}
