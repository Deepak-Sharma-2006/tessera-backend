package com.studencollabfin.server.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "chats")
public class Chat {
    @Id
    private String id;
    private String senderId;
    private String receiverId;
    private List<Message> messages;
    private LocalDateTime lastActivity;
    private boolean isBlocked;
    private boolean isReported;
    
    @Data
    public static class Message {
        private String id;
        private String senderId;
        private String content;
        private LocalDateTime timestamp;
        private boolean isRead;
        private MessageType type;
    }
    
    public enum MessageType {
        TEXT,
        IMAGE,
        FILE,
        SYSTEM
    }
}

