package com.studencollabfin.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ✅ EVENT NOTIFICATION: DTO for event creation notifications sent to users'
 * inboxes
 * 
 * Payload includes:
 * - Event title and basic details
 * - Sender info (System/Tessera Events Bot)
 * - Link to Events Hub for more details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventNotificationDTO {
    private String eventId;
    private String eventTitle;
    private String eventCategory;
    private String eventDescription;
    private String sender; // "Tessera Events" or similar
    private String message; // Full message: "New Event Added: {eventTitle}. Check the Events Hub for more
                            // details."
    private String notificationType; // "NEW_EVENT"
    private long timestamp;
    private String icon; // 📅 for events
}
