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
        // Create default achievements for new users
        createAchievement(userId, "Profile Pioneer", "Complete your profile", 
            Achievement.AchievementType.PARTICIPATION, 50);
        createAchievement(userId, "Social Butterfly", "Join 5 CollabPods", 
            Achievement.AchievementType.COLLABORATION, 100);
        createAchievement(userId, "Event Enthusiast", "Attend 3 events", 
            Achievement.AchievementType.PARTICIPATION, 150);
        createAchievement(userId, "Mentor Material", "Help 5 other students", 
            Achievement.AchievementType.MENTORSHIP, 200);
        createAchievement(userId, "Project Pro", "Complete 3 collaborative projects", 
            Achievement.AchievementType.COLLABORATION, 250);
        createAchievement(userId, "Campus Champion", "Reach level 10", 
            Achievement.AchievementType.SPECIAL, 500);
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

    public void checkProfileCompletion(User user) {
        if (isProfileComplete(user)) {
            unlockAchievement(user.getId(), "Profile Pioneer");
        }
    }

    public void checkCollabPodAchievements(String userId, int podCount) {
        if (podCount >= 5) {
            unlockAchievement(userId, "Social Butterfly");
        }
    }

    public void checkEventAttendance(String userId, int eventCount) {
        if (eventCount >= 3) {
            unlockAchievement(userId, "Event Enthusiast");
        }
    }

    public void checkMentorshipProgress(String userId, int helpCount) {
        if (helpCount >= 5) {
            unlockAchievement(userId, "Mentor Material");
        }
    }

    public void checkProjectCompletion(String userId, int projectCount) {
        if (projectCount >= 3) {
            unlockAchievement(userId, "Project Pro");
        }
    }

    public void checkLevelAchievement(User user) {
        if (user.getLevel() >= 10) {
            unlockAchievement(user.getId(), "Campus Champion");
        }
    }

    private void unlockAchievement(String userId, String title) {
        Achievement achievement = achievementRepository.findByUserIdAndTitle(userId, title)
            .orElseThrow(() -> new RuntimeException("Achievement not found"));

        if (!achievement.isUnlocked()) {
            achievement.setUnlocked(true);
            achievement.setUnlockedAt(LocalDateTime.now());
            achievementRepository.save(achievement);

            notificationService.notifyUser(userId, Map.of(
                "type", "ACHIEVEMENT_UNLOCKED",
                "achievementId", achievement.getId(),
                "title", achievement.getTitle(),
                "xpValue", achievement.getXpValue()
            ));
        }
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

