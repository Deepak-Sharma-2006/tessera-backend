package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface MessageRepository extends MongoRepository<Message, String> {
    List<Message> findByConversationIdOrderBySentAtAsc(String conversationId);

    void deleteByConversationId(String conversationId);

    // Query methods for pod-specific messages
    List<Message> findByPodIdAndMessageTypeOrderBySentAtAsc(String podId, String messageType);

    // Query methods for room-specific messages
    List<Message> findByRoomIdAndMessageTypeOrderBySentAtAsc(String roomId, String messageType);

    // Query methods for filtering by scope
    List<Message> findByScopeOrderBySentAtAsc(String scope);

    // Query methods for filtering by message type
    List<Message> findByMessageTypeOrderBySentAtAsc(String messageType);

    // Query method for finding messages by conversation and sender (retroactive
    // badge check)
    List<Message> findByConversationIdAndSenderId(String conversationId, String senderId);

    // Delete messages by podId (campus pods)
    void deleteByPodId(String podId);

    // Delete messages by roomId (global rooms)
    void deleteByRoomId(String roomId);
}
