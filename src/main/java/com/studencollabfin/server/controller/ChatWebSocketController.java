package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.Chat;
import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.service.ChatService;
import com.studencollabfin.server.service.MessagingService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;

@Controller
public class ChatWebSocketController {
    
    @Autowired
    private ChatService chatService;
    
    @Autowired
    private MessagingService messagingService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // ✅ UPGRADED: Handle 1:1 direct messages via /chat.send endpoint
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        Chat chat = chatService.sendMessage(chatMessage.getChatId(), chatMessage.getSenderId(), chatMessage.getContent());
        
        // Send to both sender and receiver
        messagingTemplate.convertAndSend("/queue/user/" + chat.getSenderId() + "/messages", chat);
        messagingTemplate.convertAndSend("/queue/user/" + chat.getReceiverId() + "/messages", chat);
    }

    // ✅ UPGRADED: Handle conversation messages with full 7-parameter support for replies
    @MessageMapping("/chat.sendMessage")
    public void handleConversationMessage(@Payload Message message) {
        try {
            System.out.println("📨 [WS.MESSAGE] Received message for conversation: " + message.getConversationId());
            System.out.println("   Sender: " + message.getSenderName() + " (" + message.getSenderId() + ")");
            System.out.println("   Text: " + message.getText());
            if (message.getReplyToId() != null) {
                System.out.println("   ReplyToId: " + message.getReplyToId());
                System.out.println("   ReplyToName: " + message.getReplyToName());
            }
            
            // Save message to DB with ALL fields including reply information
            Message saved = messagingService.sendMessage(
                    message.getConversationId(),
                    message.getSenderId(),
                    message.getText(),
                    message.getAttachmentUrls(),
                    message.getReplyToId(),           // ✅ NEW: Reply ID
                    message.getReplyToContent(),      // ✅ NEW: Reply content
                    message.getReplyToName());        // ✅ NEW: Reply sender name
            
            System.out.println("💾 [DB] Message saved with ID: " + saved.getId());
            
            // Send to topic for conversation
            String topicPath = "/topic/conversation." + message.getConversationId();
            System.out.println("📤 [WS] Broadcasting to: " + topicPath);
            messagingTemplate.convertAndSend(topicPath, saved);
            System.out.println("✅ [WS] Broadcast complete");
            
        } catch (Exception e) {
            System.err.println("❌ Error handling message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ✅ UPGRADED: Broadcast typing indicators to conversation topic (NOT individual user queue)
    @MessageMapping("/chat.typing")
    public void notifyTyping(@Payload Map<String, Object> typingData) {
        try {
            String conversationId = (String) typingData.get("conversationId");
            String userName = (String) typingData.get("userName");
            
            System.out.println("⌨️ [WS.TYPING] User typing in conversation: " + conversationId);
            System.out.println("   User: " + userName);
            
            // ✅ FIXED: Broadcast to conversation topic (not individual user queue)
            String typingTopic = "/topic/conversation." + conversationId + ".typing";
            System.out.println("📤 [WS] Broadcasting typing to: " + typingTopic);
            messagingTemplate.convertAndSend(typingTopic, typingData);
            System.out.println("✅ [WS] Typing broadcast complete");
            
        } catch (Exception e) {
            System.err.println("❌ Error handling typing indicator: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ✅ NEW: Handle read receipt notifications
    @MessageMapping("/chat.read")
    public void markMessageAsRead(@Payload Map<String, Object> readData) {
        try {
            String conversationId = (String) readData.get("conversationId");
            String messageId = (String) readData.get("messageId");
            String senderId = (String) readData.get("senderId");
            
            System.out.println("📖 [WS.READ] Marking message as read");
            System.out.println("   ConversationId: " + conversationId);
            System.out.println("   MessageId: " + messageId);
            System.out.println("   SenderId: " + senderId);
            
            // Mark conversation as read in database
            messagingService.markConversationAsRead(conversationId, senderId);
            System.out.println("💾 [DB] Conversation marked as read");
            
            // ✅ CRITICAL: Broadcast READ status back to sender so their tick turns blue
            String readTopic = "/topic/read." + conversationId;
            System.out.println("📤 [WS] Broadcasting READ status to: " + readTopic);
            messagingTemplate.convertAndSend(readTopic, readData);
            System.out.println("✅ [WS] READ broadcast complete");
            
        } catch (Exception e) {
            System.err.println("❌ Error handling read receipt: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static class ChatMessage {
        private String chatId;
        private String senderId;
        private String content;
        
        // Getters and setters
        public String getChatId() { return chatId; }
        public void setChatId(String chatId) { this.chatId = chatId; }
        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class TypingNotification {
        private String senderId;
        private String receiverId;
        private boolean isTyping;
        
        // Getters and setters
        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }
        public String getReceiverId() { return receiverId; }
        public void setReceiverId(String receiverId) { this.receiverId = receiverId; }
        public boolean getIsTyping() { return isTyping; }
        public void setIsTyping(boolean isTyping) { this.isTyping = isTyping; }
    }
}

