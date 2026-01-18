package com.studencollabfin.server.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true) // Important for Lombok to include parent class fields
@Document(collection = "posts")
public class TeamFindingPost extends Post {

    private String eventId; // ID of the event this post is for
    private List<String> requiredSkills;
    private int maxTeamSize;
    private List<String> currentTeamMembers;

    // status field for compatibility with services
    private String status;

    // Remove string status, use PostState if needed
    private PostState postState;

    // Compatibility getters delegating to superclass where appropriate
    public java.time.LocalDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    public String getAuthorId() {
        return super.getAuthorId();
    }

    public String getId() {
        return super.getId();
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getCurrentTeamMemberIds() {
        return this.currentTeamMembers;
    }

    public void setCurrentTeamMemberIds(List<String> ids) {
        this.currentTeamMembers = ids;
    }

    /**
     * Computes the post state based on createdAt and current time.
     * ACTIVE: 0-20h, CLOSED: 20-24h, EXPIRED: >24h
     */
    public PostState computePostState() {
        if (getCreatedAt() == null)
            return PostState.ACTIVE;
        LocalDateTime now = LocalDateTime.now();
        long hours = java.time.Duration.between(getCreatedAt(), now).toHours();
        if (hours < 20)
            return PostState.ACTIVE;
        if (hours < 24)
            return PostState.CLOSED;
        return PostState.EXPIRED;
    }
}
