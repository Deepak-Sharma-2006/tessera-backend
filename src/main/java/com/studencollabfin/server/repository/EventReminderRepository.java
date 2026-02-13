package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.EventReminder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface EventReminderRepository extends MongoRepository<EventReminder, String> {
    List<EventReminder> findByUserIdAndEventId(String userId, String eventId);
    
    @Query("{'reminderTime': {'$lte': ?0}, 'sent': false}")
    List<EventReminder> findDueReminders(LocalDateTime now);
    
    List<EventReminder> findByEventIdAndSentFalse(String eventId);
}

