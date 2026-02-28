package com.studencollabfin.server.gamification.event;

public record CollabRoomParticipatedEvent(String userId, String roomId, String academicBranch) {
}
