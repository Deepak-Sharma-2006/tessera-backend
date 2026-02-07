package com.studencollabfin.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

/**
 * DTO for Activity Feed Items
 * Used for Recent Activity dashboard display
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDTO {
    private String id;
    private String type; // BUDDY_BEACON, POD_CREATED, POD_JOINED, etc.
    private String title; // Display text for activity
    private String icon; // Emoji or icon identifier
    private int participantCount; // Number of participants involved
    private Date timestamp; // When the activity occurred
    private String initiatorName; // User who initiated the activity
    private String podId; // Reference to pod if applicable
}
