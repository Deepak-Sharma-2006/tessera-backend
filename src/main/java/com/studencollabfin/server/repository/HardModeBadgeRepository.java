package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.HardModeBadge;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface HardModeBadgeRepository extends MongoRepository<HardModeBadge, String> {
    List<HardModeBadge> findByUserId(String userId);

    Optional<HardModeBadge> findByUserIdAndBadgeId(String userId, String badgeId);

    List<HardModeBadge> findByUserIdAndIsUnlockedTrue(String userId);

    List<HardModeBadge> findByUserIdAndIsEquippedTrue(String userId);

    long countByUserIdAndIsUnlockedTrue(String userId);
}
