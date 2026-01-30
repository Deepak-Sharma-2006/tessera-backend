package com.studencollabfin.server.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "inbox")
public class Inbox {
    @Id
    private String id;

    private String userId; // Recipient of the message
    private String type; // e.g., "APPLICATION_FEEDBACK", "TEAM_UPDATE", "INVITE", etc.
    private String title; // Brief title for the notification
    private String message; // Full message content

    // Application feedback specific fields
    private String applicationId; // ID of the application being referenced
    private String postId; // ID of the post/team
    private String postTitle; // Title of the post for easy reference
    private String senderId; // Who sent this notification (team leader, system, etc.)

    // Application status tracking
    private String applicationStatus; // REJECTED, ACCEPTED, etc.
    private String rejectionReason; // Why was it rejected
    private String rejectionNote; // Additional note from the rejector

    // Metadata
    private LocalDateTime createdAt;
    private boolean read = false;

    public Inbox() {
        this.createdAt = LocalDateTime.now();
    }
}
