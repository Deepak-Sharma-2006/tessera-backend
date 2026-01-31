package com.studencollabfin.server.service;

import com.studencollabfin.server.model.User;
import com.studencollabfin.server.model.XPAction;
import com.studencollabfin.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GamificationService {
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Award XP to a user for completing an action
     * Handles level progression and broadcasts updates via WebSocket
     *
     * @param userId The ID of the user earning XP
     * @param action The action being rewarded
     */
    public void awardXp(String userId, XPAction action) {
        System.out.println("🎯 [GamificationService] Attempting to award XP - userId: " + userId + ", action: "
                + action.name() + " (" + action.getPoints() + " points)");

        userRepository.findById(userId).ifPresent(user -> {
            // Calculate points with multiplier
            int points = (int) (action.getPoints() * user.getXpMultiplier());
            int oldLevel = user.getLevel();

            System.out.println("📊 [GamificationService] User found: " + user.getFullName() + ", Old Level: " + oldLevel
                    + ", Old XP: " + user.getXp());
            System.out.println("💰 [GamificationService] Points to award: " + points + " (base: " + action.getPoints()
                    + " * multiplier: " + user.getXpMultiplier() + ")");

            // Award XP
            user.setXp(user.getXp() + points);
            user.setTotalXp(user.getTotalXp() + points);

            // Level progression: 100 XP per level
            while (user.getXp() >= 100) {
                user.setXp(user.getXp() - 100);
                user.setLevel(user.getLevel() + 1);
                System.out.println("⬆️  [GamificationService] LEVEL UP! New level: " + user.getLevel());
            }

            // Save updated user
            userRepository.save(user);
            System.out.println("✅ [GamificationService] User saved - New Level: " + user.getLevel() + ", New XP: "
                    + user.getXp() + ", Total XP: " + user.getTotalXp());

            // Broadcast the update via WebSocket to the user's personal topic
            System.out.println("📡 [GamificationService] Broadcasting to /user/" + userId + "/topic/xp-updates");
            messagingTemplate.convertAndSendToUser(
                    userId, "/topic/xp-updates", user);
            System.out.println("✔️  [GamificationService] Broadcast sent!");

            // Optional: Broadcast level-up to all users in a global topic
            if (user.getLevel() > oldLevel) {
                String levelUpMsg = user.getFullName() + " reached Level " + user.getLevel() + "!";
                System.out.println("🎉 [GamificationService] Broadcasting level-up: " + levelUpMsg);
                messagingTemplate.convertAndSend(
                        "/topic/level-ups",
                        levelUpMsg);
            }
        });

        if (!userRepository.existsById(userId)) {
            System.out.println("⚠️  [GamificationService] User not found! userId: " + userId);
        }
    }

    /**
     * Get current XP status for a user
     *
     * @param userId The ID of the user
     * @return The user's current XP data or null if not found
     */
    public User getXpStatus(String userId) {
        return userRepository.findById(userId).orElse(null);
    }

    /**
     * Apply an XP multiplier to a user (for achievements/prestige)
     *
     * @param userId     The ID of the user
     * @param multiplier The multiplier value (e.g., 1.5 for 50% bonus)
     */
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
