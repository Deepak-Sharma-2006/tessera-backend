package com.studencollabfin.server.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.studencollabfin.server.model.BuddyBeacon;

@SuppressWarnings("null")
public interface BuddyBeaconRepository extends MongoRepository<BuddyBeacon, String> {
    // We can add specific query methods here later if needed,
    // for example: List<BuddyBeacon> findByEventId(String eventId);
}
