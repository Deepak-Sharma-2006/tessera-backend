package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.CollabPod;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CollabPodRepository extends MongoRepository<CollabPod, String> {
    // Query for public LOOKING_FOR pods
    List<CollabPod> findByType(CollabPod.PodType type);

    // Query for pods where user is a member
    List<CollabPod> findByMemberIdsContaining(String userId);

    // Legacy methods for backward compatibility
    List<CollabPod> findByCreatorId(String creatorId);

    List<CollabPod> findByModeratorIdsContaining(String userId);

    List<CollabPod> findByStatus(CollabPod.PodStatus status);

    List<CollabPod> findByTopicsContaining(String topic);
}
