package com.studencollabfin.server.gamification.event;

public record UserReportedEvent(String reporterId, String targetUserId) {
}
