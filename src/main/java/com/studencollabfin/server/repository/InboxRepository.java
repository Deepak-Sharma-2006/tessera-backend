package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.Inbox;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface InboxRepository extends MongoRepository<Inbox, String> {
    // Find all inbox items for a specific user
    List<Inbox> findByUserId(String userId);

    // Find unread inbox items for a user
    List<Inbox> findByUserIdAndReadFalse(String userId);

    // Find inbox items by type and user
    List<Inbox> findByUserIdAndType(String userId, String type);
}
