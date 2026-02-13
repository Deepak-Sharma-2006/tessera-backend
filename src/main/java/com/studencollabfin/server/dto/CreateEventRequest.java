package com.studencollabfin.server.dto;

import lombok.Data;
import java.util.List;

@Data
public class CreateEventRequest {
    private String title;
    private String category;
    private String date; // e.g., "2025-09-19"
    private String time; // e.g., "21:30"
    private String description;
    private List<String> requiredSkills;
    private Integer maxTeamSize;
    private String registrationLink; // ✅ CHANGED: From externalLink to registrationLink
    private String linkEndDate; // ✅ NEW: Registration deadline (ISO 8601 format)
    private String organizer;
    private Integer maxTeams; // ✅ NEW: Maximum teams limit (for team events)
}
