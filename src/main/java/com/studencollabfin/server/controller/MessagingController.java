package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.Conversation;
import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.service.MessagingService;
import com.studencollabfin.server.dto.ConversationInviteResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class MessagingController {
    @Autowired
    private MessagingService messagingService;

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
}
