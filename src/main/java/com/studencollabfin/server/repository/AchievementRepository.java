package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.Achievement;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AchievementRepository extends MongoRepository<Achievement, String> {
    List<Achievement> findByUserId(String userId);
    List<Achievement> findByUserIdAndIsUnlockedTrue(String userId);
    List<Achievement> findByType(Achievement.AchievementType type);
    Optional<Achievement> findByUserIdAndTitle(String userId, String title);
}

