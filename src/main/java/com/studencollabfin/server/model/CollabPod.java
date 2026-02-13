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

    // ✅ NEW: Role-based system (replaces simple memberIds + moderatorIds)
    private String ownerId; // Immutable - the creator of the pod
    private String ownerName; // Store owner's name for display
    private List<String> adminIds; // Admins with moderation rights
    private List<String> adminNames; // Store admin names parallel to adminIds
    private List<String> memberIds; // Regular members
    private List<String> memberNames; // Store member names parallel to memberIds
    private List<String> bannedIds; // Banned users (permanently removed)

    // DEPRECATED (kept for backward compatibility - use role-based fields above)
    private String creatorId;
    private List<String> moderatorIds;
    private int maxCapacity;
    private List<String> topics;
    private PodType type; // Changed from 'podType' to 'type' to match frontend
    private PodStatus status;
    private PodScope scope; // CAMPUS or GLOBAL
    private String college; // Denormalized college name for campus isolation (e.g., "IIT", "Sinhgad",
                            // "GLOBAL")

    // ✅ NEW: Buddy Beacon Fields - Link to Event & Track Origin
    private String eventId; // Link to the Hackathon/Event (null for general pods, set for event-based
                            // teams)
    private LocalDateTime createdAt;
    private LocalDateTime lastActive;
    private List<String> resources; // Links to study materials
    private List<Meeting> meetings;
    private List<String> applicants; // Users who have applied to join this pod
    private String linkedPostId; // For GLOBAL pods: reference to the original COLLAB post

    // ✅ NEW: Pod Source tracking (distinguishes Team Pods from Collab Pods)
    private PodSource podSource; // TEAM_POD | COLLAB_POD | COLLAB_ROOM

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
        COLLAB, // Global collaboration rooms created from COLLAB posts
        TEAM // ✅ NEW: Event-based team pods (created from TeamFindingPost expiry)
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

    // ✅ NEW: Pod Source Enum - Track the origin of pods
    public enum PodSource {
        TEAM_POD, // Created when TeamFindingPost/BuddyBeaconPost expires (team formation)
        COLLAB_POD, // Created from LOOKING_FOR posts in Campus Hub
        COLLAB_ROOM // Created from COLLAB posts in Inter Hub (global)
    }

    /**
     * Get the member count, handling null memberIds gracefully
     */
    public int getMemberCount() {
        return memberIds != null ? memberIds.size() : 0;
    }
}
