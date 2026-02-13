package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.PodMessage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PodMessageRepository extends MongoRepository<PodMessage, String> {
    List<PodMessage> findByPodIdOrderByTimestampAsc(String podId);

    void deleteByPodId(String podId);
}
