package com.studencollabfin.server.gamification.event;

public record PodJoinedEvent(String userId, String podId, boolean isFirstPod) {
}
