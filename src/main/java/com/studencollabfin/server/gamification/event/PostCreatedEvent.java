package com.studencollabfin.server.gamification.event;

public record PostCreatedEvent(String userId, String postId, String category, boolean hasResources) {
}
