package com.studencollabfin.server.model;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "collabPods")
@SuppressWarnings("null")
public class CollabPod {
    @Id
    private String id;

    private String title;
    private String description;

    private PodType type;
    private List<String> memberIds;
    private List<ChatMessage> messages;
    private LocalDateTime createdAt;

    private String creatorId;
    private String creatorName; // ✅ ADDED: Stores the creator's name for display

    private List<String> moderatorIds;
    private int maxCapacity;
    private List<String> topics;
    private PodStatus status;
    private LocalDateTime lastActive;
    private List<String> resources;
    private List<Meeting> meetings;

    // ... (Keep your existing ChatMessage, Meeting, Enums, etc. exactly as they
    // are) ...

    // Copy-paste your existing inner classes (ChatMessage, Meeting, etc.) here.
    // I am omitting them for brevity, but DO NOT DELETE THEM.

    @Data
    public static class ChatMessage {
        // ... keep existing code ...
        private String id;
        private String authorId;
        private String authorName;
        private String content;
        private LocalDateTime createdAt;

        // Reply and attachment fields
        private String replyToId; // ID of the message being replied to
        private String replyToName; // Name of the person being replied to
        private String replyToContent; // Snippet of the text being replied to
        private String attachmentUrl; // URL of the uploaded file/image
        private AttachmentType attachmentType; // NONE, IMAGE, FILE

        public ChatMessage() {
        }

        public ChatMessage(String authorId, String authorName, String content) {
            this.id = java.util.UUID.randomUUID().toString();
            this.authorId = authorId;
            this.authorName = authorName;
            this.content = content;
            this.createdAt = LocalDateTime.now();
            this.attachmentType = AttachmentType.NONE;
        }
    }

    // ... Keep Enums ...
    public enum AttachmentType {
        NONE, IMAGE, FILE
    }

    public enum PodType {
        LOOKING_FOR, TEAM
    }

    public enum PodStatus {
        ACTIVE, FULL, ARCHIVED, CLOSED
    }

    public enum MeetingStatus {
        SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
    }

    @lombok.Data
    public static class PodMessage {
        private String id = java.util.UUID.randomUUID().toString();
        private String authorName;
        private String content;
        private LocalDateTime createdAt = LocalDateTime.now();
    }

    @Data
    public static class Meeting {
        private String id;
        private String title;
        private String description;
        private LocalDateTime scheduledTime;
        private String meetingLink;
        private List<String> attendeeIds;
        private MeetingStatus status;
    }
}