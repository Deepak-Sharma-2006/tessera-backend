package com.studencollabfin.server.service;

import com.studencollabfin.server.gamification.event.DirectMessageSentEvent;
import com.studencollabfin.server.gamification.tracker.ModerationTracker;
import com.studencollabfin.server.model.Conversation;
import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.repository.ConversationRepository;
import com.studencollabfin.server.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.studencollabfin.server.repository.UserRepository;
import com.studencollabfin.server.model.User;
import com.studencollabfin.server.dto.ConversationInviteResponse;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
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
    private FcmNotificationService fcmNotificationService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ModerationTracker moderationTracker;

    public List<Conversation> getUserConversations(String userId) {
        List<Conversation> conversations = conversationRepository.findByParticipantIdsContaining(userId);

        // ✅ NEW: Calculate unread counts for each conversation
        for (Conversation conv : conversations) {
            calculateUnreadCount(conv, userId);
        }

        return conversations;
    }

    /**
     * ✅ NEW: Calculate unread message count for a user in a conversation
     * Count = messages sent AFTER user's last read timestamp
     */
    private void calculateUnreadCount(Conversation conv, String userId) {
        if (conv == null)
            return;

        try {
            // Get the user's last read timestamp (default to conversation creation time)
            Date lastReadTime = conv.getLastReadTimestamps() != null
                    ? conv.getLastReadTimestamps().get(userId)
                    : conv.getCreatedAt();

            // Create effectively final variable for lambda expression
            final Date effectiveLastReadTime = (lastReadTime != null) ? lastReadTime : conv.getCreatedAt();

            // Count messages sent AFTER the last read time
            List<Message> allMessages = messageRepository.findByConversationIdOrderBySentAtAsc(conv.getId());
            long unreadCount = allMessages.stream()
                    .filter(msg -> msg.getSentAt().after(effectiveLastReadTime))
                    .filter(msg -> !msg.getSenderId().equals(userId)) // Don't count own messages
                    .count();

            // Initialize unreadCounts map if null
            if (conv.getUnreadCounts() == null) {
                conv.setUnreadCounts(new java.util.HashMap<>());
            }

            // Store unread count
            conv.getUnreadCounts().put(userId, (int) unreadCount);

            // ✅ FIXED: Persist the calculation back to database
            conversationRepository.save(conv);

            System.out
                    .println("[UnreadCount] User: " + userId + ", Conv: " + conv.getId() + ", Unread: " + unreadCount);
        } catch (Exception e) {
            System.err.println("Error calculating unread count: " + e.getMessage());
            // Fallback to 0 on error
            if (conv.getUnreadCounts() == null) {
                conv.setUnreadCounts(new java.util.HashMap<>());
            }
            conv.getUnreadCounts().put(userId, 0);
        }
    }

    /**
     * ✅ NEW: Mark messages in a conversation as read by a user
     * Updates the last read timestamp
     */
    public void markConversationAsRead(String conversationId, String userId) {
        try {
            Conversation conv = conversationRepository.findById(conversationId).orElse(null);
            if (conv == null)
                return;

            // Initialize maps if needed
            if (conv.getLastReadTimestamps() == null) {
                conv.setLastReadTimestamps(new java.util.HashMap<>());
            }
            if (conv.getUnreadCounts() == null) {
                conv.setUnreadCounts(new java.util.HashMap<>());
            }

            // Update last read timestamp to now
            conv.getLastReadTimestamps().put(userId, new Date());
            conv.getUnreadCounts().put(userId, 0);

            conversationRepository.save(conv);
            System.out.println("[MarkAsRead] User: " + userId + ", Conv: " + conversationId);
        } catch (Exception e) {
            System.err.println("Error marking conversation as read: " + e.getMessage());
        }
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

    public Message sendMessage(String conversationId, String senderId, String text, List<String> attachmentUrls,
            String replyToId, String replyToContent, String replyToSenderName, String replyToSenderId) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setRoomId(conversationId); // For global rooms, roomId = conversationId
        msg.setSenderId(senderId);
        msg.setText(text);
        msg.setAttachmentUrls(attachmentUrls);
        msg.setSentAt(new Date());
        msg.setRead(false);

        // ✅ NEW: Set reply fields if present
        if (replyToId != null && !replyToId.isEmpty()) {
            msg.setReplyToId(replyToId);
            msg.setReplyToContent(replyToContent);
            msg.setReplyToName(replyToSenderName); // Field name is replyToName, not replyToSenderName
            msg.setReplyToSenderId(replyToSenderId); // ✅ CRITICAL: Save the sender ID for mirror logic
        }

        // Set messageType and scope for global inter-college/inter-campus conversations
        msg.setMessageType(Message.MessageType.CHAT);
        msg.setScope("GLOBAL");

        String receiverIdForEvent = null;
        String senderDomainForEvent = "";
        String receiverDomainForEvent = "";
        String receiverCollegeIdForEvent = "";

        if (conversationId != null) {
            Conversation conv = conversationRepository.findById(conversationId).orElseThrow();

            // ✅ CRITICAL: Only allow messaging if conversation is ACCEPTED
            if (!"ACCEPTED".equals(conv.getStatus())) {
                throw new RuntimeException(
                        "Cannot send message in PENDING conversation. Invite must be accepted first.");
            }

            conv.setUpdatedAt(new Date());
            conversationRepository.save(conv);

            if (conv.getParticipantIds() != null) {
                receiverIdForEvent = conv.getParticipantIds().stream()
                        .filter(participantId -> participantId != null && !participantId.equals(senderId))
                        .findFirst()
                        .orElse(null);
            }

            enforceDirectMessageBan(senderId, receiverIdForEvent);

            User senderUser = userRepository.findById(senderId).orElse(null);
            if (senderUser != null && senderUser.getEmail() != null) {
                senderDomainForEvent = extractDomain(senderUser.getEmail());
            }
            if (receiverIdForEvent != null) {
                User receiverUser = userRepository.findById(receiverIdForEvent).orElse(null);
                if (receiverUser != null) {
                    if (receiverUser.getEmail() != null && !receiverUser.getEmail().isBlank()) {
                        receiverDomainForEvent = extractDomain(receiverUser.getEmail());
                    }
                    if (receiverUser.getCollegeName() != null && !receiverUser.getCollegeName().isBlank()) {
                        receiverCollegeIdForEvent = receiverUser.getCollegeName().trim().toLowerCase();
                    }
                }
            }

            if (receiverDomainForEvent == null || receiverDomainForEvent.isBlank()) {
                receiverDomainForEvent = (receiverCollegeIdForEvent != null && !receiverCollegeIdForEvent.isBlank())
                        ? "college:" + receiverCollegeIdForEvent
                        : "unknown";
            }

            if (senderDomainForEvent == null || senderDomainForEvent.isBlank()) {
                senderDomainForEvent = "unknown";
            }

        }

        Message saved = messageRepository.save(msg);

        if (receiverIdForEvent != null) {
            eventPublisher.publishEvent(new DirectMessageSentEvent(
                    senderId,
                    receiverIdForEvent,
                    senderDomainForEvent,
                    receiverDomainForEvent,
                    receiverCollegeIdForEvent));
        }

        // ✅ FCM: DM notifications (token-based) with Android tag stacking per sender
        try {
            if (conversationId != null && senderId != null) {
                Conversation conv = conversationRepository.findById(conversationId).orElse(null);
                if (conv != null && conv.getParticipantIds() != null) {
                    User sender = userRepository.findById(senderId).orElse(null);
                    String senderNameSafe = (sender != null && sender.getFullName() != null)
                            ? sender.getFullName()
                            : "New message";

                    for (String participantId : conv.getParticipantIds()) {
                        if (participantId == null || participantId.equals(senderId)) {
                            continue;
                        }

                        User recipient = userRepository.findById(participantId).orElse(null);
                        if (recipient == null || recipient.getFcmToken() == null || recipient.getFcmToken().isBlank()) {
                            continue;
                        }

                        String preview = (text != null && !text.isBlank())
                                ? text
                                : ((attachmentUrls != null && !attachmentUrls.isEmpty()) ? "Sent an attachment"
                                        : "New message");

                        HashMap<String, String> data = new HashMap<>();
                        data.put("type", FcmNotificationService.TYPE_DM);
                        data.put("conversationId", conversationId);
                        data.put("senderId", senderId);
                        if (sender != null && sender.getFullName() != null) {
                            data.put("senderName", sender.getFullName());
                        }
                        if (saved.getId() != null) {
                            data.put("messageId", saved.getId());
                        }

                        fcmNotificationService.sendToToken(
                                recipient.getFcmToken(),
                                senderNameSafe,
                                preview,
                                data,
                                FcmNotificationService.CHANNEL_CHATS,
                                senderId,
                                recipient); // ✅ Pass recipient for preference check
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ [FCM] DM notify failed: " + e.getMessage());
        }

        return saved;
    }

    private void enforceDirectMessageBan(String senderId, String receiverId) {
        if (senderId == null || senderId.isBlank() || receiverId == null || receiverId.isBlank()) {
            return;
        }

        if (moderationTracker.isDmBanActiveBetween(senderId, receiverId)) {
            long remainingMinutes = moderationTracker.getRemainingDmBanMinutes(senderId, receiverId);
            throw new RuntimeException(
                    "You are temporarily blocked from messaging this user for another " + remainingMinutes
                            + " minute(s).");
        }
    }

    // ✅ NEW: Overload for backward compatibility (7 parameters)
    public Message sendMessage(String conversationId, String senderId, String text, List<String> attachmentUrls,
            String replyToId, String replyToContent, String replyToSenderName) {
        return sendMessage(conversationId, senderId, text, attachmentUrls, replyToId, replyToContent,
                replyToSenderName, null);
    }

    // ✅ NEW: Overload for backward compatibility (4 parameters)
    public Message sendMessage(String conversationId, String senderId, String text, List<String> attachmentUrls) {
        return sendMessage(conversationId, senderId, text, attachmentUrls, null, null, null, null);
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

        // ✅ FCM: INBOX notification for invite
        try {
            User sender = userRepository.findById(senderId).orElse(null);
            User target = userRepository.findById(targetId).orElse(null);
            if (target != null && target.getFcmToken() != null && !target.getFcmToken().isBlank()) {
                String senderNameSafe = (sender != null && sender.getFullName() != null) ? sender.getFullName()
                        : "Someone";

                HashMap<String, String> data = new HashMap<>();
                data.put("type", FcmNotificationService.TYPE_INBOX);
                data.put("subType", "INVITE");
                data.put("conversationId", conv.getId());

                fcmNotificationService.sendToToken(
                        target.getFcmToken(),
                        "New invite",
                        senderNameSafe + " sent you a collaboration request",
                        data,
                        FcmNotificationService.CHANNEL_UPDATES,
                        null,
                        target); // ✅ Pass target for preference check
            }
        } catch (Exception e) {
            System.err.println("⚠️ [FCM] Invite notify failed: " + e.getMessage());
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

    /**
     * ✅ Get ALL invites FOR a user with ENRICHED user details
     * Returns invites SENT TO them (not sent BY them).
     * Includes initiator's name, college, department for UI display.
     * 
     * Used for app inbox display - includes initiator details so app can show:
     * - Sender name, college, department
     * - Conversation status (PENDING/ACCEPTED)
     * 
     * @param userId User to fetch invites for (recipient)
     * @return List of enriched conversations with initiator details
     */
    public List<ConversationInviteResponse> getAllUserInvitesEnriched(String userId) {
        // Get all conversations where user is a participant
        List<Conversation> allUserConversations = conversationRepository.findByParticipantIdsContaining(userId);

        // Filter to only conversations where userId is NOT the initiator
        // Include both PENDING and ACCEPTED statuses
        // Enrich with initiator user details
        return allUserConversations.stream()
                .filter(conv -> !userId.equals(conv.getInitiatorId()))
                .filter(conv -> "PENDING".equals(conv.getStatus()) || "ACCEPTED".equals(conv.getStatus()))
                .map(conv -> {
                    // Fetch initiator's user details
                    User initiator = userRepository.findById(conv.getInitiatorId()).orElse(null);
                    return new ConversationInviteResponse(conv, initiator);
                })
                .toList();
    }
}
