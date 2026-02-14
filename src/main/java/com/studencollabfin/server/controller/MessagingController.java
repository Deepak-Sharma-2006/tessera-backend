package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.Conversation;
import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.service.MessagingService;
import com.studencollabfin.server.dto.ConversationInviteResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class MessagingController {
    @Autowired
    private MessagingService messagingService;

    @Autowired
    private com.studencollabfin.server.repository.CollabPodRepository collabPodRepository;

    @Autowired
    private com.studencollabfin.server.repository.MessageRepository messageRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @GetMapping("/conversations/{userId}")
    public List<Conversation> getUserConversations(@PathVariable String userId) {
        return messagingService.getUserConversations(userId);
    }

    @PostMapping("/conversations")
    public Conversation createConversation(@RequestBody Map<String, List<String>> body) {
        return messagingService.createConversation(body.get("participantIds"));
    }

    @GetMapping("/conversation/{conversationId}")
    public Conversation getConversation(@PathVariable String conversationId) {
        return messagingService.getConversation(conversationId).orElse(null);
    }

    @GetMapping("/conversation/{conversationId}/messages")
    public List<Message> getMessages(@PathVariable String conversationId) {
        return messagingService.getMessages(conversationId);
    }

    @PostMapping("/conversation/{conversationId}/send")
    public Message sendMessage(@PathVariable String conversationId, @RequestBody Map<String, Object> body) {
        String senderId = (String) body.get("senderId");
        String text = (String) body.get("text");
        @SuppressWarnings("unchecked")
        List<String> attachmentUrls = (List<String>) body.getOrDefault("attachmentUrls", null);

        // ✅ NEW: Support for reply fields
        String replyToId = (String) body.getOrDefault("replyToId", null);
        String replyToContent = (String) body.getOrDefault("replyToContent", null);
        String replyToSenderName = (String) body.getOrDefault("replyToSenderName", null);

        return messagingService.sendMessage(conversationId, senderId, text, attachmentUrls, replyToId, replyToContent,
                replyToSenderName);
    }

    /**
     * Send a collaboration invite to another user.
     * Creates a PENDING conversation and sends WebSocket notification.
     * 🔄 HANDLES DUPLICATES: Returns 409 Conflict if already connected.
     * 
     * @param targetId User to send invite to
     * @param body     Request body with senderId
     * @return Created conversation with PENDING status, or 409 if already exists
     */
    @PostMapping("/invite/{targetId}")
    public ResponseEntity<?> sendInvite(
            @PathVariable String targetId,
            @RequestBody Map<String, String> body) {
        try {
            String senderId = body.get("senderId");
            if (senderId == null || senderId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "senderId is required"));
            }

            Conversation conversation = messagingService.sendInvite(senderId, targetId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Invite sent successfully",
                    "conversationId", conversation.getId()));
        } catch (RuntimeException e) {
            // 🔄 Check if error is "already exists" - return 409 Conflict instead of 400
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                return ResponseEntity.status(409).body(Map.of(
                        "success", false,
                        "error", "Already connected with this user",
                        "code", "ALREADY_EXISTS"));
            }
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all pending invites for a user.
     * 
     * @param userId User to fetch pending invites for
     * @return List of PENDING conversations
     */
    @GetMapping("/invites/pending/{userId}")
    public ResponseEntity<List<Conversation>> getPendingInvites(@PathVariable String userId) {
        List<Conversation> pendingInvites = messagingService.getPendingInvites(userId);
        return ResponseEntity.ok(pendingInvites);
    }

    /**
     * ✅ Get ALL invites for a user (both PENDING and ACCEPTED)
     * Persists accepted invites in the inbox display.
     * 
     * @param userId User to fetch all invites for
     * @return List of all conversations (PENDING and ACCEPTED) where user is
     *         recipient
     */
    @GetMapping("/invites/all/{userId}")
    public ResponseEntity<List<Conversation>> getAllUserInvites(@PathVariable String userId) {
        List<Conversation> allInvites = messagingService.getAllUserInvites(userId);
        return ResponseEntity.ok(allInvites);
    }

    /**
     * ✅ Get ALL invites FOR a user with ENRICHED user details
     * Returns invites SENT TO them with initiator name, college, department
     * 
     * @param userId User to fetch all invites for
     * @return List of enriched conversations with initiator details
     */
    @GetMapping("/invites/enriched/{userId}")
    public ResponseEntity<List<ConversationInviteResponse>> getAllUserInvitesEnriched(@PathVariable String userId) {
        List<ConversationInviteResponse> enrichedInvites = messagingService.getAllUserInvitesEnriched(userId);
        return ResponseEntity.ok(enrichedInvites);
    }

    /**
     * Respond to a pending invite.
     * 
     * @param conversationId ID of the PENDING conversation
     * @param body           Request body with "accept" boolean
     * @return Success response
     */
    @PostMapping("/invite/{conversationId}/respond")
    public ResponseEntity<?> respondToInvite(
            @PathVariable String conversationId,
            @RequestBody Map<String, Boolean> body) {
        try {
            Boolean accept = body.getOrDefault("accept", false);
            messagingService.respondToInvite(conversationId, accept);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", accept ? "Invite accepted" : "Invite declined"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ NEW: Mark a conversation as read by the current user
     * Updates the last read timestamp
     * 
     * @param conversationId ID of the conversation
     * @param body           Request body with "userId"
     * @return Success response
     */
    @PostMapping("/conversation/{conversationId}/markAsRead")
    public ResponseEntity<?> markConversationAsRead(
            @PathVariable String conversationId,
            @RequestBody Map<String, String> body) {
        try {
            String userId = body.get("userId");
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
            }
            messagingService.markConversationAsRead(conversationId, userId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Conversation marked as read"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ NEW: Delete a single message (Pod Moderation)
     * Only Admin or Owner of the pod can delete messages.
     * 
     * @param messageId ID of the message to delete
     * @param body      Request body with "actorId" and "podId"
     * @return Success response or permission error
     */
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<?> deleteMessage(
            @PathVariable String messageId,
            @RequestBody Map<String, String> body) {
        try {
            String actorId = body.get("actorId");
            String podId = body.get("podId");

            if (actorId == null || actorId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "actorId is required"));
            }
            if (podId == null || podId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "podId is required"));
            }

            // Step 1: Fetch the pod to verify actor's role
            @SuppressWarnings("null")
            java.util.Optional<com.studencollabfin.server.model.CollabPod> podOpt = collabPodRepository
                    .findById(podId);
            if (podOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Pod not found"));
            }

            com.studencollabfin.server.model.CollabPod pod = podOpt.get();

            // Step 2: Verify actor is Admin or Owner
            boolean isOwner = pod.getOwnerId() != null && pod.getOwnerId().equals(actorId);
            boolean isAdmin = pod.getAdminIds() != null && pod.getAdminIds().contains(actorId);

            if (!isOwner && !isAdmin) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Only pod owner or admin can delete messages"));
            }

            // Step 3: Verify message exists
            @SuppressWarnings("null")
            java.util.Optional<Message> messageOpt = messageRepository.findById(messageId);
            if (messageOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Message not found"));
            }

            Message message = messageOpt.get();

            // Step 4: Verify message belongs to this pod
            if ((message.getPodId() == null || !message.getPodId().equals(podId)) &&
                    (message.getConversationId() == null || !message.getConversationId().equals(podId))) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Message does not belong to this pod"));
            }

            // Step 5: Delete the message
            messageRepository.deleteById(messageId);
            System.out.println("✅ Message " + messageId + " deleted by " + actorId + " (pod: " + podId + ")");

            // ✅ LIVE SIGNAL: Broadcast MESSAGE_DELETED event to all pod members in
            // real-time
            try {
                Map<String, Object> deleteEvent = new java.util.HashMap<>();
                deleteEvent.put("eventType", "MESSAGE_DELETED");
                deleteEvent.put("messageId", messageId);
                deleteEvent.put("podId", podId);
                deleteEvent.put("deletedBy", actorId);
                deleteEvent.put("timestamp", System.currentTimeMillis());

                messagingTemplate.convertAndSend("/topic/pod." + podId + ".chat", deleteEvent);
                System.out.println("📡 Broadcasted MESSAGE_DELETED event to /topic/pod." + podId + ".chat");
            } catch (Exception e) {
                System.err.println("⚠️ Failed to broadcast deletion event: " + e.getMessage());
                e.printStackTrace();
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Message deleted successfully",
                    "deletedMessageId", messageId));
        } catch (Exception e) {
            System.err.println("❌ Error deleting message: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to delete message",
                    "details", e.getMessage()));
        }
    }
}
