package com.studencollabfin.server.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "event_reminders")
public class EventReminder {
    @Id
    private String id;
    private String eventId;
    private String userId;
    private LocalDateTime reminderTime;
    private boolean sent;
    private ReminderType type;

    public enum ReminderType {
        ONE_DAY_BEFORE,
        ONE_HOUR_BEFORE,
        FIFTEEN_MINUTES_BEFORE,
        CUSTOM
    }
}

