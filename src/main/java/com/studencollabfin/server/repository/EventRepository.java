package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.Event;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EventRepository extends MongoRepository<Event, String> {
    // ADD THIS METHOD
    List<Event> findByCategory(String category);
}

