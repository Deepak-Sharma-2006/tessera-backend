package com.studencollabfin.server.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Document(collection = "events")
public class Event {
    @Id
    private String id;
    private String title;
    private String description;
    private String category;
    private String organizer; // userId (previously organizedBy)
    private String collegeId;
    private EventType type;
    private LocalDateTime startDate;
    private LocalDateTime linkEndDate; // ✅ Registration deadline (formerly endDate)
    private int maxParticipants;
    private List<String> participantIds;
    private List<String> requiredSkills;
    private String location; // Can be physical or virtual
    private String meetingLink;
    private String registrationLink; // ✅ NEW: External registration link
    private EventStatus status;
    private List<String> tags;

    // ✅ NEW: Participation Counting System - CURRENT COUNTS (Stored in DB)
    private Long currentParticipants = 0L; // Actual count of participants registered
    private Long currentTeams = 0L; // Actual count of teams formed

    // ✅ NEW: OLD COUNTERS (For migration - can be deprecated)
    private Set<String> registeredUserIds = new HashSet<>(); // Track unique users who clicked Register
    private int participantsCount = 0; // Display count (legacy, use currentParticipants)
    private int teamsCount = 0; // Number of teams formed for this event (legacy, use currentTeams)

    // ✅ NEW: Capacity Limits
    private Integer maxTeams; // Maximum number of teams allowed (null = unlimited)

    // ✅ NEW: Transient field (not persisted to DB) - Set by controller based on
    // current user
    @Transient
    private boolean hasRegistered = false; // Whether current user has registered for this event

    public enum EventType {
        TECH_TALK,
        HACKATHON,
        WORKSHOP,
        STUDY_GROUP,
        CONFERENCE,
        OTHER
    }

    public enum EventStatus {
        UPCOMING,
        ONGOING,
        COMPLETED,
        CANCELLED
    }
}
