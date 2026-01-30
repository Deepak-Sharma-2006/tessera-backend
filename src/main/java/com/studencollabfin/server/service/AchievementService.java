package com.studencollabfin.server.service;

import com.studencollabfin.server.model.Achievement;
import com.studencollabfin.server.model.User;
import com.studencollabfin.server.repository.AchievementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AchievementService {
    @Autowired
    private AchievementRepository achievementRepository;
    @Autowired
    private NotificationService notificationService;

    public void initializeUserAchievements(String userId) {
        // --- Power Five MVP ---
        createAchievement(userId, "Founding Dev", "Platform Architect", Achievement.AchievementType.FOUNDING_DEV, 1000);
        createAchievement(userId, "Campus Catalyst", "Authorized Event Creator",
                Achievement.AchievementType.CAMPUS_CATALYST, 500);
        createAchievement(userId, "Pod Pioneer", "Joined your first collaboration pod",
                Achievement.AchievementType.POD_PIONEER, 100);
        createAchievement(userId, "Bridge Builder", "Collaborated across colleges",
                Achievement.AchievementType.BRIDGE_BUILDER, 150);
        createAchievement(userId, "Skill Sage", "Received 3+ skill endorsements",
                Achievement.AchievementType.SKILL_SAGE, 200);

        // --- Standard ---
        createAchievement(userId, "Profile Pioneer", "Complete your profile", Achievement.AchievementType.PARTICIPATION,
                50);
    }

    private Achievement createAchievement(String userId, String title, String description,
            Achievement.AchievementType type, int xpValue) {
        Achievement achievement = new Achievement();
        achievement.setUserId(userId);
        achievement.setTitle(title);
        achievement.setDescription(description);
        achievement.setType(type);
        achievement.setXpValue(xpValue);
        achievement.setUnlocked(false);
        return achievementRepository.save(achievement);
    }

    public void onJoinPod(String userId) {
        unlockAchievement(userId, "Pod Pioneer");
    }

    public void unlockAchievement(String userId, String title) {
        achievementRepository.findByUserIdAndTitle(userId, title).ifPresent(achievement -> {
            if (!achievement.isUnlocked()) {
                achievement.setUnlocked(true);
                achievement.setUnlockedAt(LocalDateTime.now());
                achievementRepository.save(achievement);
                notificationService.notifyUser(userId, Map.of("type", "ACHIEVEMENT_UNLOCKED", "title", title));
            }
        });
    }

    private boolean isProfileComplete(User user) {
        return user.getFullName() != null && !user.getFullName().isEmpty() &&
                user.getCollegeName() != null && !user.getCollegeName().isEmpty() &&
                user.getYearOfStudy() != null && !user.getYearOfStudy().isEmpty() &&
                user.getDepartment() != null && !user.getDepartment().isEmpty() &&
                user.getSkills() != null && !user.getSkills().isEmpty() &&
                user.getRolesOpenTo() != null && !user.getRolesOpenTo().isEmpty() &&
                user.getGoals() != null && !user.getGoals().isEmpty();
    }

    public List<Achievement> getUserAchievements(String userId) {
        return achievementRepository.findByUserId(userId);
    }
}
