package com.studencollabfin.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Campus Overview Statistics
 * Provides real-time metrics for dashboard display
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampusStatsDTO {
    private long totalStudents; // Total students in the user's institution
    private long myTeams; // Collab Pods where user is owner
    private long collaborations; // Collab Pods where user is member but not owner
}
