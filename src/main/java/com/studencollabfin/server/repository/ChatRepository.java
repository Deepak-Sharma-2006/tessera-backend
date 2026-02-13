package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.Chat;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatRepository extends MongoRepository<Chat, String> {
    List<Chat> findBySenderId(String senderId);
    List<Chat> findByReceiverId(String receiverId);
    List<Chat> findByIsReportedTrue();
    Chat findBySenderIdAndReceiverId(String senderId, String receiverId);
}

