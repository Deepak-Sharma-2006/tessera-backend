package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.service.CollabPodService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * WebSocket controller for pod chat messages and typing indicators.
 * Handles real-time messaging within Collab Pods.
 * 
 * Message flow:
 * 1. Frontend sends message to /app/pod.{podId}.chat
 * 2. Handler saves message to database BEFORE broadcasting
 * 3. Broadcast to /topic/pod.{podId}.chat for all pod members
 * 
 * Typing indicator flow:
 * 1. Frontend sends typing event to /app/pod.{podId}.typing with userName
 * 2. Broadcast to /topic/pod.{podId}.typing for all pod members
 */
@Controller
@RequiredArgsConstructor
public class PodChatWSController {
    private final CollabPodService collabPodService;
    private final SimpMessagingTemplate messagingTemplate;

    @SuppressWarnings("null")
    @MessageMapping("/pod.{podId}.chat")
    public void handlePodMessage(@DestinationVariable String podId, @Payload Message message) {
        try {
            System.out.println("📨 [WS] Message received for pod: " + podId);
            System.out.println("   Content: " + message.getContent());
            System.out.println("   Sender: " + message.getSenderName() + " (" + message.getSenderId() + ")");

            // Ensure pod context is properly set
            message.setPodId(podId);
            message.setConversationId(podId);
            message.setMessageType(Message.MessageType.CHAT);
            message.setScope("CAMPUS");

            // CRITICAL: Save message to database BEFORE broadcasting
            // This ensures data persistence even if WebSocket connection drops
            System.out.println("💾 [DB] Saving message to database...");
            Message savedMessage = collabPodService.saveMessage(message);
            System.out.println("✅ [DB] Message saved with ID: " + savedMessage.getId());

            // Broadcast saved message to all subscribers of this pod
            String topicPath = String.format("/topic/pod.%s.chat", podId);
            System.out.println("📤 [WS] Broadcasting to: " + topicPath);
            messagingTemplate.convertAndSend(topicPath, savedMessage);
            System.out.println("✅ [WS] Broadcast complete");

            System.out.println("✓ Pod message handled for pod " + podId + ": " + savedMessage.getId());
        } catch (Exception e) {
            System.err.println("✗ Error handling pod message: " + e.getMessage());
            e.printStackTrace();
            // Don't throw exception - just log it to prevent connection issues
        }
    }

    /**
     * ✅ NEW: Handle typing indicators for pod chat
     * Broadcasts userName so UI can show "X is typing..."
     * 
     * Typing indicator flow:
     * 1. Frontend sends to /app/pod.{podId}.typing with payload containing userName
     * 2. Handler broadcasts to /topic/pod.{podId}.typing
     * 3. Frontend receives and displays typing indicator
     */
    @MessageMapping("/pod.{podId}.typing")
    public void handlePodTyping(@DestinationVariable String podId, @Payload java.util.Map<String, String> payload) {
        try {
            String userId = payload.get("userId");
            String userName = payload.get("userName");

            System.out.println("⌨️  [WS] Typing indicator received for pod: " + podId);
            System.out.println("   User: " + userName + " (" + userId + ")");

            // Create typed indicator payload with userName included
            java.util.Map<String, Object> typingEvent = new java.util.HashMap<>();
            typingEvent.put("userId", userId);
            typingEvent.put("userName", userName);
            typingEvent.put("timestamp", System.currentTimeMillis());
            typingEvent.put("isTyping", true);

            // Broadcast to all subscribers of this pod's typing topic
            String typingTopic = String.format("/topic/pod.%s.typing", podId);
            System.out.println("📤 [WS] Broadcasting typing indicator to: " + typingTopic);
            messagingTemplate.convertAndSend(typingTopic, typingEvent);
            System.out.println("✅ [WS] Typing indicator broadcast complete");
        } catch (Exception e) {
            System.err.println("✗ Error handling typing indicator: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
