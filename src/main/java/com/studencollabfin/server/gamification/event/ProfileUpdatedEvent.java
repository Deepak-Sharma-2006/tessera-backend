package com.studencollabfin.server.gamification.event;

public record ProfileUpdatedEvent(String userId, boolean isProfileComplete) {
}
