package com.studencollabfin.server.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

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
    private LocalDateTime endDate;
    private int maxParticipants;
    private List<String> participantIds;
    private List<String> requiredSkills;
    private String location; // Can be physical or virtual
    private String meetingLink;
    private EventStatus status;
    private List<String> tags;
    
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

