package com.studencollabfin.server.service;

import com.studencollabfin.server.model.Conversation;
import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.repository.ConversationRepository;
import com.studencollabfin.server.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.studencollabfin.server.repository.UserRepository;
import com.studencollabfin.server.model.User;

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
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AchievementService achievementService;

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

            // ✅ Check for Bridge Builder badge unlock (inter-college message)
            checkAndUnlockBridgeBuilder(senderId, conv);
        }
        return messageRepository.save(msg);
    }

    private void checkAndUnlockBridgeBuilder(String senderId, Conversation conv) {
        try {
            // Get sender's user details
            User sender = userRepository.findById(senderId).orElse(null);
            if (sender == null || sender.getEmail() == null) {
                System.out.println("[BadgeService] ⚠️ Bridge Builder check: Sender not found or email missing");
                return;
            }

            String senderDomain = extractDomain(sender.getEmail());
            List<String> participantIds = conv.getParticipantIds();

            if (participantIds == null || participantIds.size() < 2) {
                System.out.println("[BadgeService] ℹ️ Bridge Builder check: Conversation has less than 2 participants");
                return;
            }

            System.out.println("[BadgeService] 🔍 Checking Bridge Builder eligibility...");
            System.out.println("   Sender: " + senderId);
            System.out.println("   Sender domain: " + senderDomain);

            // Check if any other participant has different institution domain
            boolean isInterCollege = false;
            String recipientDomain = "";
            String recipientId = "";

            for (String participantId : participantIds) {
                if (!participantId.equals(senderId)) {
                    User participant = userRepository.findById(participantId).orElse(null);
                    if (participant != null && participant.getEmail() != null) {
                        String participantDomain = extractDomain(participant.getEmail());
                        System.out.println("   Checking participant: " + participantId);
                        System.out.println("   Participant domain: " + participantDomain);

                        if (!senderDomain.equals(participantDomain)) {
                            isInterCollege = true;
                            recipientDomain = participantDomain;
                            recipientId = participantId;
                            break;
                        }
                    }
                }
            }

            System.out.println("   Is inter-college: " + isInterCollege);
            System.out.println("   Already has badge: " + sender.getBadges().contains("Bridge Builder"));

            // If inter-college and sender doesn't have badge yet, unlock it
            if (isInterCollege && !sender.getBadges().contains("Bridge Builder")) {
                System.out.println("[BadgeService] ✅ UNLOCKING Bridge Builder!");
                System.out.println("   Sender domain: " + senderDomain + " → Recipient domain: " + recipientDomain);
                achievementService.onInterCollegeMessage(senderId);
                System.out.println("[BadgeService] 🌉 Bridge Builder badge UNLOCKED for " + senderId);
            } else if (!isInterCollege) {
                System.out.println("[BadgeService] ℹ️ Not inter-college message - same domain conversation");
            } else {
                System.out.println("[BadgeService] ℹ️ Badge already owned by " + senderId);
            }
        } catch (Exception e) {
            System.err.println("❌ Error checking Bridge Builder badge: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String extractDomain(String email) {
        if (email == null || !email.contains("@")) {
            return "";
        }
        return email.substring(email.indexOf("@") + 1).toLowerCase();
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
            @SuppressWarnings("null")
            String _targetId = targetId;
            messagingTemplate.convertAndSendToUser(
                    _targetId,
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
        @SuppressWarnings("null")
        String _convId = conversationId;
        conversationRepository.findById(_convId).ifPresent(conv -> {
            if (accept) {
                // Accept: change status to ACCEPTED
                conv.setStatus("ACCEPTED");
                conv.setUpdatedAt(new Date());
                conversationRepository.save(conv);
            } else {
                // Decline: delete the conversation
                @SuppressWarnings("null")
                Conversation _conv = conv;
                conversationRepository.delete(_conv);
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

    /**
     * ✅ Get ALL invites FOR a user (both PENDING and ACCEPTED)
     * Returns invites SENT TO them (not sent BY them).
     * Filters out conversations initiated by the user.
     * 
     * Used for inbox display - includes both pending and accepted invites.
     * 
     * @param userId User to fetch invites for (recipient)
     * @return List of conversations where this user received an invite (any status)
     */
    public List<Conversation> getAllUserInvites(String userId) {
        // Get all conversations where user is a participant
        List<Conversation> allUserConversations = conversationRepository.findByParticipantIdsContaining(userId);

        // Filter to only conversations where userId is NOT the initiator
        // (i.e., invites received, not sent)
        // Include both PENDING and ACCEPTED statuses
        return allUserConversations.stream()
                .filter(conv -> !userId.equals(conv.getInitiatorId()))
                .filter(conv -> "PENDING".equals(conv.getStatus()) || "ACCEPTED".equals(conv.getStatus()))
                .toList();
    }
}
