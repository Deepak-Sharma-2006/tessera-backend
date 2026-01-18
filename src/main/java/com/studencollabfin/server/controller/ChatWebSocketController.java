package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.Chat;
import com.studencollabfin.server.service.ChatService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;

@Controller
public class ChatWebSocketController {
    
    @Autowired
    private ChatService chatService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatMessage chatMessage) {
        Chat chat = chatService.sendMessage(chatMessage.getChatId(), chatMessage.getSenderId(), chatMessage.getContent());
        
        // Send to both sender and receiver
        messagingTemplate.convertAndSend("/queue/user/" + chat.getSenderId() + "/messages", chat);
        messagingTemplate.convertAndSend("/queue/user/" + chat.getReceiverId() + "/messages", chat);
    }

    @MessageMapping("/chat.typing")
    public void notifyTyping(@Payload TypingNotification notification) {
        messagingTemplate.convertAndSend(
            "/queue/user/" + notification.getReceiverId() + "/typing",
            notification
        );
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

