package com.studencollabfin.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * DTO for Campus Overview Statistics
 * Provides real-time metrics for dashboard display
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampusStatsDTO {
    private long totalStudents; // Total students in the user's institution
    private long openCollaborations; // LOOKING_FOR pods in campus
    private long myTeams; // Team pods where user is owner or member
    private List<CampusActivityDTO> activityFeed; // Latest 20 activities
}
