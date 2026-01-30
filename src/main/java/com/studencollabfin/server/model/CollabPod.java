package com.studencollabfin.server.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "collabPods")
public class CollabPod {
    @Id
    private String id;
    private String name;
    private String description;
    private String creatorId;
    private List<String> memberIds;
    private List<String> moderatorIds;
    private int maxCapacity;
    private List<String> topics;
    private PodType type; // Changed from 'podType' to 'type' to match frontend
    private PodStatus status;
    private PodScope scope; // CAMPUS or GLOBAL
    private LocalDateTime createdAt;
    private LocalDateTime lastActive;
    private List<String> resources; // Links to study materials
    private List<Meeting> meetings;
    private List<String> applicants; // Users who have applied to join this pod
    private String linkedPostId; // For GLOBAL pods: reference to the original COLLAB post

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

    // This enum now matches the values sent from the frontend
    public enum PodType {
        DISCUSSION,
        ASK,
        HELP,
        PROJECT_TEAM,
        MENTORSHIP,
        COURSE_SPECIFIC,
        LOOKING_FOR,
        COLLAB // Global collaboration rooms created from COLLAB posts
    }

    public enum PodStatus {
        ACTIVE,
        FULL,
        ARCHIVED,
        CLOSED
    }

    public enum MeetingStatus {
        SCHEDULED,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED
    }

    /**
     * Get the member count, handling null memberIds gracefully
     */
    public int getMemberCount() {
        return memberIds != null ? memberIds.size() : 0;
    }
}
