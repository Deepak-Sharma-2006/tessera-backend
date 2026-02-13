package com.studencollabfin.server.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "beacons")
public class BuddyBeacon {
    @Id
    private String id;
    private String authorId;
    private String eventId;
    private String eventName;
    private String title;
    private String description;
    private List<String> requiredSkills;
    private int maxTeamSize;
    private List<String> currentTeamMemberIds;
    private String status;
    private List<String> applicants; // For tracking users who applied to this beacon
    private List<Map<String, Object>> applicantObjects; // ✅ FEATURE: Full applicant data with profiles

    private LocalDateTime createdAt;

    // Compatibility getters/setters to match service expectations
    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getCurrentTeamMemberIds() {
        return this.currentTeamMemberIds;
    }

    public void setCurrentTeamMemberIds(List<String> ids) {
        this.currentTeamMemberIds = ids;
    }

    public void setApplicantObjects(List<Map<String, Object>> applicantObjects) {
        this.applicantObjects = applicantObjects;
    }

    public List<Map<String, Object>> getApplicantObjects() {
        return this.applicantObjects;
    }
}
