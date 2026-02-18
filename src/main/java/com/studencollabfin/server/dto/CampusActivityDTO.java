package com.studencollabfin.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

/**
 * DTO for Campus Activity Feed (simplified for live feed UI)
 * Provides minimal data for Twitch-style activity stream
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampusActivityDTO {
    private String type; // COLLAB_POD, TEAM_POD, POLL, BEACON
    private String username;
    private String userId;
    private String title; // Pod/poll title, null for beacons
    private Date createdAt;
}
