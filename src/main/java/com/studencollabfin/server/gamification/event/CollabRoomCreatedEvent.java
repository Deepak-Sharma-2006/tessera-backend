package com.studencollabfin.server.gamification.event;

public record CollabRoomCreatedEvent(String userId, String roomId, boolean isMultiCollege,
        int distinctCollegeCountAtFill) {
}
