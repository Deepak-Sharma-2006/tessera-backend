package com.studencollabfin.server.service;

import com.studencollabfin.server.model.User;
import com.studencollabfin.server.model.XPAction;
import com.studencollabfin.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class GamificationService {
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public void addXp(String userId, int points) {
        if (points <= 0) {
            return;
        }

        applyXp(userId, points, "CUSTOM");
    }

    /**
     * Award XP to a user for completing an action
     * Handles level progression and broadcasts updates via WebSocket
     *
     * @param userId The ID of the user earning XP
     * @param action The action being rewarded
     */
    @SuppressWarnings("null")
    public void awardXp(String userId, XPAction action) {
        applyXp(userId, action, action.name());
    }

    private void applyXp(String userId, XPAction action, String actionLabel) {
        int points = action.getPoints();

        System.out.println("🎯 [GamificationService] Attempting to award XP - userId: " + userId + ", action: "
                + actionLabel + " (" + points + " points)");

        userRepository.findById(userId).ifPresent(user -> {
            int adjustedPoints = (int) (points * user.getXpMultiplier());
            applyXpToUser(user, userId, adjustedPoints, points, actionLabel);
        });

        if (!userRepository.existsById(userId)) {
            System.out.println("⚠️  [GamificationService] User not found! userId: " + userId);
        }
    }

    private void applyXp(String userId, int points, String actionLabel) {
        System.out.println("🎯 [GamificationService] Attempting to award XP - userId: " + userId + ", action: "
                + actionLabel + " (" + points + " points)");

        userRepository.findById(userId).ifPresent(user -> {
            int adjustedPoints = (int) (points * user.getXpMultiplier());
            applyXpToUser(user, userId, adjustedPoints, points, actionLabel);
        });

        if (!userRepository.existsById(userId)) {
            System.out.println("⚠️  [GamificationService] User not found! userId: " + userId);
        }
    }

    private void applyXpToUser(User user, String userId, int adjustedPoints, int basePoints, String actionLabel) {
        int oldLevel = user.getLevel();

        System.out.println("📊 [GamificationService] User found: " + user.getFullName() + ", Old Level: " + oldLevel
                + ", Old XP: " + user.getXp());
        System.out.println("💰 [GamificationService] Points to award: " + adjustedPoints + " (base: " + basePoints
                + " * multiplier: " + user.getXpMultiplier() + ")");

        user.setXp(user.getXp() + adjustedPoints);
        user.setTotalXp(user.getTotalXp() + adjustedPoints);

        while (user.getXp() >= 100) {
            user.setXp(user.getXp() - 100);
            user.setLevel(user.getLevel() + 1);
            System.out.println("⬆️  [GamificationService] LEVEL UP! New level: " + user.getLevel());
        }

        userRepository.save(user);
        System.out.println("✅ [GamificationService] User saved - New Level: " + user.getLevel() + ", New XP: "
                + user.getXp() + ", Total XP: " + user.getTotalXp());

        System.out.println("📡 [GamificationService] Broadcasting to /user/" + userId + "/topic/xp-updates");
        messagingTemplate.convertAndSendToUser(userId, "/topic/xp-updates", user);
        System.out.println("✔️  [GamificationService] Broadcast sent! for action " + actionLabel);

        if (user.getLevel() > oldLevel) {
            Map<String, Object> levelUpPayload = Map.of(
                    "userId", userId,
                    "newLevel", user.getLevel(),
                    "xp", user.getXp(),
                    "totalXp", user.getTotalXp());

            messagingTemplate.convertAndSendToUser(userId, "/queue/level-up", levelUpPayload);

            System.out.println("🎉 [GamificationService] Sent /queue/level-up payload: " + levelUpPayload);

            String levelUpMsg = user.getFullName() + " reached Level " + user.getLevel() + "!";
            System.out.println("🎉 [GamificationService] Broadcasting level-up: " + levelUpMsg);
            messagingTemplate.convertAndSend("/topic/level-ups", levelUpMsg);
        }
    }

    /**
     * Get current XP status for a user
     *
     * @param userId The ID of the user
     * @return The user's current XP data or null if not found
     */
    @SuppressWarnings("null")
    public User getXpStatus(String userId) {
        return userRepository.findById(userId).orElse(null);
    }

    /**
     * Apply an XP multiplier to a user (for achievements/prestige)
     *
     * @param userId     The ID of the user
     * @param multiplier The multiplier value (e.g., 1.5 for 50% bonus)
     */
    @SuppressWarnings("null")
    public void setXpMultiplier(String userId, double multiplier) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setXpMultiplier(multiplier);
            userRepository.save(user);

            // Notify user of multiplier update
            messagingTemplate.convertAndSendToUser(
                    userId, "/topic/xp-updates", user);
        });
    }
}
