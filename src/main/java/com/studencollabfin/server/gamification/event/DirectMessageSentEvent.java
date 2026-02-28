package com.studencollabfin.server.gamification.event;

public record DirectMessageSentEvent(
        String senderId,
        String receiverId,
        String senderDomain,
        String receiverDomain,
        String receiverCollegeId) {
}
