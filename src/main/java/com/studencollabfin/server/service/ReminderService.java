package com.studencollabfin.server.service;

import com.studencollabfin.server.model.Event;
import com.studencollabfin.server.model.EventReminder;
import com.studencollabfin.server.repository.EventReminderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ReminderService {
    
    @Autowired
    private EventReminderRepository reminderRepository;
    
    @Autowired
    private NotificationService notificationService;
    
    public EventReminder createReminder(String eventId, String userId, EventReminder.ReminderType type) {
        EventReminder reminder = new EventReminder();
        reminder.setEventId(eventId);
        reminder.setUserId(userId);
        reminder.setType(type);
        reminder.setSent(false);
        
        LocalDateTime reminderTime;
        LocalDateTime eventTime = getEventStartTime(eventId);
        
        switch (type) {
            case ONE_DAY_BEFORE:
                reminderTime = eventTime.minusDays(1);
                break;
            case ONE_HOUR_BEFORE:
                reminderTime = eventTime.minusHours(1);
                break;
            case FIFTEEN_MINUTES_BEFORE:
                reminderTime = eventTime.minusMinutes(15);
                break;
            default:
                throw new IllegalArgumentException("Invalid reminder type");
        }
        
        reminder.setReminderTime(reminderTime);
        return reminderRepository.save(reminder);
    }

    public List<EventReminder> getUserReminders(String userId, String eventId) {
        return reminderRepository.findByUserIdAndEventId(userId, eventId);
    }

    @Scheduled(fixedRate = 60000) // Check every minute
    public void checkAndSendReminders() {
        List<EventReminder> dueReminders = reminderRepository.findDueReminders(LocalDateTime.now());
        
        for (EventReminder reminder : dueReminders) {
            try {
                sendReminderNotification(reminder);
                reminder.setSent(true);
                reminderRepository.save(reminder);
            } catch (Exception e) {
                // Log error but continue processing other reminders
                System.err.println("Error sending reminder: " + e.getMessage());
            }
        }
    }
    
    private void sendReminderNotification(EventReminder reminder) {
        Event event = getEvent(reminder.getEventId());
        String message = String.format("Reminder: Event '%s' starting soon!", event.getTitle());
        
        notificationService.notifyUser(reminder.getUserId(), Map.of(
            "type", "EVENT_REMINDER",
            "eventId", reminder.getEventId(),
            "message", message,
            "reminderType", reminder.getType()
        ));
    }
    
    // These methods would be implemented to fetch event details
    private LocalDateTime getEventStartTime(String eventId) {
        return getEvent(eventId).getStartDate();
    }
    
    private Event getEvent(String eventId) {
        // This would be implemented to fetch the event from EventService
        throw new UnsupportedOperationException("Not implemented");
    }
}

