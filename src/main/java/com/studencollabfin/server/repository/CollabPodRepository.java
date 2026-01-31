package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.model.PodScope;
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

    List<CollabPod> findByScope(PodScope scope);

    List<CollabPod> findByScopeAndStatus(PodScope scope, CollabPod.PodStatus status);

    List<CollabPod> findByLinkedPostId(String linkedPostId);

    List<CollabPod> findByMemberIdsContainsAndScope(String memberId, PodScope scope);

    // Campus isolation: Find pods by scope and college
    List<CollabPod> findByScopeAndCollege(PodScope scope, String college);

    // Find all pods of a specific college
    List<CollabPod> findByCollege(String college);

    // ✅ NEW: Find pods by eventId and type (for Buddy Beacon stats)
    List<CollabPod> findByEventIdAndType(String eventId, CollabPod.PodType type);

    // ✅ NEW: Check if user is already a member of a pod for this event (double
    // booking prevention)
    boolean existsByEventIdAndMemberIdsContains(String eventId, String userId);
}
