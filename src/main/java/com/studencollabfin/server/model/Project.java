package com.studencollabfin.server.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "projects")
public class Project {
    @Id
    private String id;
    private String name;
    private String description;
    private String leaderId;
    private List<String> memberIds;
    private List<String> requiredSkills;
    private int maxTeamSize;
    private ProjectStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String githubLink;
    private String documentationLink;
    private List<String> tags;
    private List<Milestone> milestones;
    
    @Data
    public static class Milestone {
        private String id;
        private String title;
        private String description;
        private LocalDateTime dueDate;
        private boolean completed;
        private List<Task> tasks;
    }
    
    @Data
    public static class Task {
        private String id;
        private String description;
        private String assignedTo;
        private boolean completed;
        private LocalDateTime dueDate;
    }
    
    public enum ProjectStatus {
        PLANNING,
        IN_PROGRESS,
        COMPLETED,
        ON_HOLD,
        CANCELLED
    }
}

