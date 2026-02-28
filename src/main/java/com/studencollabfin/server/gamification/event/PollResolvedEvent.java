package com.studencollabfin.server.gamification.event;

public record PollResolvedEvent(String userId, String pollId, boolean isMajorityChoice) {
}
