package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.PodCooldown;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repository for PodCooldown collection
 * Manages cooldown records for pod leave/rejoin operations
 */
@Repository
public interface PodCooldownRepository extends MongoRepository<PodCooldown, String> {

    /**
     * Find all active cooldowns for a specific user and pod
     */
    Optional<PodCooldown> findByUserIdAndPodId(String userId, String podId);

    /**
     * Find all cooldowns for a user (to check all active cooldowns)
     */
    List<PodCooldown> findByUserId(String userId);

    /**
     * Find all cooldowns for a pod (useful for admin monitoring)
     */
    List<PodCooldown> findByPodId(String podId);

    /**
     * Check if a user is on cooldown for a specific pod
     */
    boolean existsByUserIdAndPodId(String userId, String podId);

    /**
     * Delete all expired cooldowns (called periodically or after expiry)
     */
    void deleteByUserIdAndPodId(String userId, String podId);
}
