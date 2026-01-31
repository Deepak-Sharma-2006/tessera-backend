package com.studencollabfin.server.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * ✅ INBOX FEATURE: Notification management for users
 * 
 * Supports multiple notification types:
 * - POD_BAN: User was removed from a pod
 * - APPLICATION_REJECTION: Application was rejected
 */
@Data
@Document(collection = "inbox")
public class Inbox {

    /**
     * Notification type enum
     */
    public enum NotificationType {
        POD_BAN,
        APPLICATION_REJECTION
    }

    /**
     * Severity level for UI styling
     */
    public enum NotificationSeverity {
        LOW,
        MEDIUM,
        HIGH
    }

    @Id
    private String id;

    // Core notification fields
    private String userId; // Recipient of the notification
    private NotificationType type; // Type of notification
    private String title; // Brief title for the notification
    private String message; // Full message content
    private NotificationSeverity severity = NotificationSeverity.MEDIUM; // Severity for styling

    // Pod ban specific fields
    private String podId; // ID of the pod (for POD_BAN)
    private String podName; // Name of the pod (for POD_BAN)
    private String reason; // Reason for ban or rejection

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
    private LocalDateTime timestamp; // Additional timestamp field
    private boolean read = false;

    public Inbox() {
        this.createdAt = LocalDateTime.now();
        this.timestamp = LocalDateTime.now();
    }
}
