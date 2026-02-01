package com.studencollabfin.server.service;

import com.studencollabfin.server.model.Conversation;
import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.repository.ConversationRepository;
import com.studencollabfin.server.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class MessagingService {
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public List<Conversation> getUserConversations(String userId) {
        return conversationRepository.findByParticipantIdsContaining(userId);
    }

    public Optional<Conversation> getConversation(String id) {
        if (id == null)
            return Optional.empty();
        return conversationRepository.findById(id);
    }

    public Conversation createConversation(List<String> participantIds) {
        Conversation conv = new Conversation();
        conv.setParticipantIds(participantIds);
        conv.setCreatedAt(new Date());
        conv.setUpdatedAt(new Date());
        return conversationRepository.save(conv);
    }

    public Message sendMessage(String conversationId, String senderId, String text, List<String> attachmentUrls) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRoomId(conversationId); // For global rooms, roomId = conversationId
        msg.setSenderId(senderId);
        msg.setText(text);
        msg.setAttachmentUrls(attachmentUrls);
        msg.setSentAt(new Date());
        msg.setRead(false);

        // Set messageType and scope for global inter-college/inter-campus conversations
        msg.setMessageType(Message.MessageType.CHAT);
        msg.setScope("GLOBAL");

        if (conversationId != null) {
            Conversation conv = conversationRepository.findById(conversationId).orElseThrow();
            conv.setUpdatedAt(new Date());
            conversationRepository.save(conv);
        }
        return messageRepository.save(msg);
    }

    public List<Message> getMessages(String conversationId) {
        return messageRepository.findByConversationIdOrderBySentAtAsc(conversationId);
    }

    /**
     * Send a collaboration invite to a user.
     * Creates a PENDING conversation and triggers a WebSocket notification.
     * 
     * @param senderId User sending the invite
     * @param targetId User receiving the invite
     * @return The created Conversation
     * @throws RuntimeException if conversation already exists
     */
    public Conversation sendInvite(String senderId, String targetId) {
        // Check if a conversation already exists between these two users
        Optional<Conversation> existing = conversationRepository.findByParticipantsIn(senderId, targetId);
        if (existing.isPresent()) {
            throw new RuntimeException("Conversation already exists between these users");
        }

        // Create new PENDING conversation
        Conversation conv = new Conversation();
        conv.setParticipantIds(Arrays.asList(senderId, targetId));
        conv.setInitiatorId(senderId);
        conv.setStatus("PENDING");
        conv.setCreatedAt(new Date());
        conv.setUpdatedAt(new Date());
        conversationRepository.save(conv);

        // Send WebSocket alert to target user
        try {
            messagingTemplate.convertAndSendToUser(
                    targetId,
                    "/queue/invites",
                    "You have a new collaboration request from a peer with matching skills!");
        } catch (Exception e) {
            System.err.println("WebSocket notification failed: " + e.getMessage());
            // Continue anyway - the invite was created
        }

        return conv;
    }

    /**
     * Respond to a pending invite.
     * 
     * @param conversationId ID of the PENDING conversation
     * @param accept         true to accept, false to decline
     */
    public void respondToInvite(String conversationId, boolean accept) {
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            if (accept) {
                // Accept: change status to ACCEPTED
                conv.setStatus("ACCEPTED");
                conv.setUpdatedAt(new Date());
                conversationRepository.save(conv);
            } else {
                // Decline: delete the conversation
                conversationRepository.delete(conv);
            }
        });
    }

    /**
     * Get all pending invites FOR a user (i.e., invites SENT TO them, not sent BY
     * them).
     * Only returns PENDING conversations where the user is a PARTICIPANT but NOT
     * the INITIATOR.
     * 
     * @param userId User to fetch pending invites for (recipient)
     * @return List of PENDING conversations where this user received an invite
     */
    public List<Conversation> getPendingInvites(String userId) {
        // Get all conversations where user is a participant
        List<Conversation> allUserConversations = conversationRepository.findByParticipantIdsContaining(userId);

        // Filter to only PENDING conversations where userId is NOT the initiator
        // (i.e., invites received, not sent)
        return allUserConversations.stream()
                .filter(conv -> "PENDING".equals(conv.getStatus()))
                .filter(conv -> !userId.equals(conv.getInitiatorId()))
                .toList();
    }
}
