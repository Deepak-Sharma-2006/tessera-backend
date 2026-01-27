package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.CollabPod;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CollabPodRepository extends MongoRepository<CollabPod, String> {
    List<CollabPod> findByCreatorId(String creatorId);
    List<CollabPod> findByMemberIdsContaining(String userId);
    List<CollabPod> findByModeratorIdsContaining(String userId);
    List<CollabPod> findByType(CollabPod.PodType type);
    List<CollabPod> findByStatus(CollabPod.PodStatus status);
    List<CollabPod> findByTopicsContaining(String topic);
}

