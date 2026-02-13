package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends MongoRepository<Conversation, String> {
    // Find all conversations for a user (both sent and received)
    List<Conversation> findByParticipantIdsContaining(String userId);

    // Find pending invites for a user (received but not accepted/declined)
    List<Conversation> findByParticipantIdsContainingAndStatus(String userId, String status);

    // Find existing conversation between two users
    @Query("{ 'participantIds': { $all: [?0, ?1] } }")
    Optional<Conversation> findByParticipantsIn(String userId1, String userId2);
}
