package com.studencollabfin.server.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
public class Message {
    @Id
    private String id;

    // ✅ NEW: Message type categorization with enum
    private MessageType messageType; // CHAT, SYSTEM (for actions like 'User X was kicked')
    
    // DEPRECATED (kept for backward compatibility)
    private String messageTypeString; // CAMPUS_POD or GLOBAL_ROOM
    private String scope; // CAMPUS (for campus pods) or GLOBAL (for inter-college rooms)

    // Context identifiers
    private String conversationId; // Primary ID (podId for campus, roomId for global)
    private String podId; // Specific to campus pods
    private String roomId; // Specific to global rooms

    // Sender information
    private String senderId;
    private String senderName;

    // Message content
    private String text;
    private String content; // Alternative field name for text
    private List<String> attachmentUrls; // URLs to files/images

    // Attachment fields (singular for single file/image uploads)
    private String attachmentUrl; // URL to single file/image
    private String attachmentType; // IMAGE, FILE, NONE
    private String fileName; // Original file name

    // Reply-to fields (for quoted/threaded replies)
    private String replyToId; // ID of the message being replied to
    private String replyToName; // Sender name of the message being replied to
    private String replyToContent; // Content of the message being replied to

    // Metadata
    private Date sentAt;
    private boolean read;

    // ✅ NEW: Enum for message types
    public enum MessageType {
        CHAT,    // Regular user messages
        SYSTEM   // System-generated messages (user kicked, user joined, etc.)
    }
}
