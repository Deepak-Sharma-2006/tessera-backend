package com.studencollabfin.server.service;

import com.studencollabfin.server.model.Achievement;
import com.studencollabfin.server.model.User;
import com.studencollabfin.server.model.Conversation;
import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.repository.AchievementRepository;
import com.studencollabfin.server.repository.UserRepository;
import com.studencollabfin.server.repository.ConversationRepository;
import com.studencollabfin.server.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Service
public class AchievementService {
    @Autowired
    private AchievementRepository achievementRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired(required = false)
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    @Autowired
    private GamificationService gamificationService;

    public void initializeUserAchievements(String userId) {
        // --- Power Five MVP ---
        createAchievement(userId, "Founding Dev", "Platform Architect", Achievement.AchievementType.FOUNDING_DEV, 1000);
        createAchievement(userId, "Campus Catalyst", "Authorized Event Creator",
                Achievement.AchievementType.CAMPUS_CATALYST, 500);
        createAchievement(userId, "Pod Pioneer", "Joined your first collaboration pod",
                Achievement.AchievementType.POD_PIONEER, 100);
        createAchievement(userId, "Bridge Builder", "Collaborated across colleges",
                Achievement.AchievementType.BRIDGE_BUILDER, 150);

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
        System.out.println("[BadgeService] 🌱 Pod Pioneer badge unlocked for user " + userId);
    }

    public void onInterCollegeMessage(String userId) {
        unlockAchievement(userId, "Bridge Builder");
        System.out.println("[BadgeService] 🌉 Bridge Builder badge unlocked for user " + userId);
    }

    public void unlockAchievement(String userId, String title) {
        // ✅ CRITICAL: Add badge to user.badges array immediately (achievement doc
        // optional)
        @SuppressWarnings("null")
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            if (user.getBadges() == null) {
                user.setBadges(new ArrayList<>());
            }

            // Only add if not already present
            if (!user.getBadges().contains(title)) {
                user.getBadges().add(title);
                userRepository.save(user);
                System.out.println("[BadgeService] ✅ Added '" + title + "' to user.badges array for " + userId);

                // Award XP for badge unlock
                try {
                    gamificationService.awardXp(userId, com.studencollabfin.server.model.XPAction.BADGE_UNLOCK);
                    System.out.println("[BadgeService] 💰 Awarded 25 XP for " + title + " unlock");
                } catch (Exception e) {
                    System.err.println("[BadgeService] ⚠️ Failed to award XP: " + e.getMessage());
                }

                // ✅ REAL-TIME: Broadcast badge unlock via WebSocket
                if (messagingTemplate != null) {
                    try {
                        messagingTemplate.convertAndSendToUser(
                                userId,
                                "/queue/badge-unlock",
                                Map.of(
                                        "badgeName", title,
                                        "message", "🎉 " + title + " badge unlocked!",
                                        "timestamp", System.currentTimeMillis()));
                        System.out.println("[BadgeService] ✅ WebSocket broadcast sent for " + title + " unlock");
                    } catch (Exception e) {
                        System.err.println("[BadgeService] ⚠️ WebSocket broadcast failed: " + e.getMessage());
                    }
                }
            } else {
                System.out.println("[BadgeService] ℹ️ User already has '" + title + "' badge");
            }
        } else {
            System.err.println("[BadgeService] ❌ User not found: " + userId);
        }

        // Also create achievement record if it doesn't exist
        try {
            @SuppressWarnings("null")
            Achievement achievement = achievementRepository.findByUserIdAndTitle(userId, title).orElse(null);
            if (achievement == null) {
                achievement = new Achievement();
                achievement.setUserId(userId);
                achievement.setTitle(title);
                achievement.setDescription("Achievement: " + title);
                achievement.setType(Achievement.AchievementType.SPECIAL);
                achievement.setXpValue(25);
                achievement.setUnlocked(true);
                achievement.setUnlockedAt(LocalDateTime.now());
                achievementRepository.save(achievement);
                System.out.println("[BadgeService] 📝 Created achievement record for " + title);
            } else if (!achievement.isUnlocked()) {
                achievement.setUnlocked(true);
                achievement.setUnlockedAt(LocalDateTime.now());
                achievementRepository.save(achievement);
                System.out.println("[BadgeService] ✅ Marked achievement as unlocked for " + title);
            }
        } catch (Exception e) {
            System.err.println("[BadgeService] ⚠️ Achievement record creation failed: " + e.getMessage());
        }
    }

    public List<Achievement> getUserAchievements(String userId) {
        return achievementRepository.findByUserId(userId);
    }

    /**
     * ✅ ATTRIBUTE-DRIVEN BADGE SYNC - COMPLETE SEPARATION
     * 
     * CRITICAL: Founding Dev and Campus Catalyst are COMPLETELY INDEPENDENT
     * - Founding Dev: isDev flag ONLY (NOT role-dependent)
     * - Campus Catalyst: role ONLY (NOT isDev-dependent)
     * 
     * They do NOT affect each other in any way.
     */
    /**
     * ✅ STRICT: Badge Sync ONLY enforces existing badges, NEVER adds new ones
     * 
     * Purpose: Verify badges in database match current user state
     * - Removes badges that should be revoked (role changed, etc.)
     * - DOES NOT automatically grant new badges
     * 
     * Rule: All badges ONLY unlock through:
     * 1. Admin promotion (Campus Catalyst)
     * 2. Explicit triggers (Pod Pioneer on pod join)
     * 3. Achievement completion (Signal Guardian when posts >= 5)
     * 
     * NEVER auto-unlock on profile load
     */
    public User syncUserBadges(User user) {
        if (user == null) {
            System.err.println("❌ syncUserBadges: user is null");
            return null;
        }

        if (user.getBadges() == null) {
            user.setBadges(new ArrayList<>());
        }

        boolean updated = false;
        List<String> currentBadges = user.getBadges();
        List<String> changes = new ArrayList<>();

        // ✅ AUTO-UNLOCK: Add badges based on user attributes (role-based only)

        // 1. FOUNDING DEV: Auto-unlock if isDev=true
        if (user.isDev() && !currentBadges.contains("Founding Dev")) {
            currentBadges.add("Founding Dev");
            updated = true;
            changes.add("✅ AUTO-UNLOCKED 'Founding Dev' (isDev=true)");
            System.out.println("[BadgeSync] 🔓 Auto-unlocked Founding Dev for user " + user.getId());

            // Award XP for badge unlock
            try {
                gamificationService.awardXp(user.getId(), com.studencollabfin.server.model.XPAction.BADGE_UNLOCK);
                System.out.println("[BadgeSync] 💰 Awarded 25 XP for Founding Dev unlock");
            } catch (Exception e) {
                System.err.println("[BadgeSync] ⚠️ Failed to award XP: " + e.getMessage());
            }
        }
        // Remove if isDev=false
        if (!user.isDev() && currentBadges.contains("Founding Dev")) {
            currentBadges.remove("Founding Dev");
            updated = true;
            changes.add("✅ REMOVED 'Founding Dev' (isDev=false)");
        }

        // 2. CAMPUS CATALYST: Auto-unlock if role=COLLEGE_HEAD
        if ("COLLEGE_HEAD".equals(user.getRole()) && !currentBadges.contains("Campus Catalyst")) {
            currentBadges.add("Campus Catalyst");
            updated = true;
            changes.add("✅ AUTO-UNLOCKED 'Campus Catalyst' (role=COLLEGE_HEAD)");
            System.out.println("[BadgeSync] 🔓 Auto-unlocked Campus Catalyst for user " + user.getId());

            // Award XP for badge unlock
            try {
                gamificationService.awardXp(user.getId(), com.studencollabfin.server.model.XPAction.BADGE_UNLOCK);
                System.out.println("[BadgeSync] 💰 Awarded 25 XP for Campus Catalyst unlock");
            } catch (Exception e) {
                System.err.println("[BadgeSync] ⚠️ Failed to award XP: " + e.getMessage());
            }
        }
        // Remove if role != COLLEGE_HEAD
        if (!"COLLEGE_HEAD".equals(user.getRole()) && currentBadges.contains("Campus Catalyst")) {
            currentBadges.remove("Campus Catalyst");
            updated = true;
            changes.add("✅ REMOVED 'Campus Catalyst' (role != COLLEGE_HEAD)");
        }

        // 3. SIGNAL GUARDIAN: Auto-unlock if posts >= 5
        if (user.getPostsCount() >= 5 && !currentBadges.contains("Signal Guardian")) {
            currentBadges.add("Signal Guardian");
            updated = true;
            changes.add("✅ AUTO-UNLOCKED 'Signal Guardian' (posts >= 5)");
            System.out.println("[BadgeSync] 🔓 Auto-unlocked Signal Guardian for user " + user.getId());

            // Award XP for badge unlock
            try {
                gamificationService.awardXp(user.getId(), com.studencollabfin.server.model.XPAction.BADGE_UNLOCK);
                System.out.println("[BadgeSync] 💰 Awarded 25 XP for Signal Guardian unlock");
            } catch (Exception e) {
                System.err.println("[BadgeSync] ⚠️ Failed to award XP: " + e.getMessage());
            }
        }
        // Remove if posts < 5
        if (user.getPostsCount() < 5 && currentBadges.contains("Signal Guardian")) {
            currentBadges.remove("Signal Guardian");
            updated = true;
            changes.add("✅ REMOVED 'Signal Guardian' (posts < 5)");
        }

        // 4. POD PIONEER: Only added via explicit trigger (onJoinPod)
        // 5. BRIDGE BUILDER: Only added via explicit trigger (onInterCollegeMessage)

        // SAVE if any badges were added or removed
        if (updated) {
            System.out.println("\n🔄 BADGE ENFORCEMENT - User: " + user.getId());
            changes.forEach(System.out::println);
            // IMPORTANT: XP/level may have been updated via GamificationService.awardXp(),
            // which loads+saves a separate User instance.
            // Re-load the latest user before saving badges to avoid overwriting XP.
            User latestUser = userRepository.findById(user.getId()).orElse(user);
            latestUser.setBadges(currentBadges);
            return userRepository.save(latestUser);
        } else {
            return user;
        }
    }

    /**
     * ✅ RETROACTIVE BADGE CHECK - Called on login to unlock missed badges
     * Scans user's conversation history for inter-college messages
     * and retroactively unlocks Bridge Builder if applicable
     */
    public void retroactivelyUnlockBridgeBuilder(String userId) {
        try {
            System.out.println("\n🔄 [Login Check] Performing retroactive badge check for " + userId);
            User user = userRepository.findById(userId).orElse(null);

            if (user == null || user.getBadges().contains("Bridge Builder")) {
                System.out.println("   ℹ️ User not found or already has Bridge Builder badge");
                return;
            }

            String userDomain = extractDomain(user.getEmail());
            if (userDomain.isEmpty()) {
                System.out.println("   ⚠️ User email domain not found");
                return;
            }

            System.out.println("   Scanning conversations for inter-college messages...");
            System.out.println("   User domain: " + userDomain);

            // Get all conversations where user is a participant
            List<Conversation> conversations = conversationRepository.findByParticipantIdsContaining(userId);

            for (Conversation conv : conversations) {
                List<String> participantIds = conv.getParticipantIds();
                if (participantIds == null || participantIds.size() < 2) {
                    continue;
                }

                // Check if conversation has participants from different domains
                boolean hasInterCollege = false;
                for (String participantId : participantIds) {
                    if (!participantId.equals(userId)) {
                        User participant = userRepository.findById(participantId).orElse(null);
                        if (participant != null && participant.getEmail() != null) {
                            String participantDomain = extractDomain(participant.getEmail());
                            if (!userDomain.equals(participantDomain)) {
                                hasInterCollege = true;
                                System.out.println("   ✅ Found inter-college conversation!");
                                System.out.println("   User domain: " + userDomain + " ↔️  Participant domain: "
                                        + participantDomain);
                                break;
                            }
                        }
                    }
                }

                if (hasInterCollege) {
                    // Check if user has ANY messages in this conversation (not just received)
                    List<Message> userMessages = messageRepository.findByConversationIdAndSenderId(
                            conv.getId(), userId);

                    if (userMessages != null && !userMessages.isEmpty()) {
                        System.out.println("   ✅ User has sent messages in inter-college conversation");
                        System.out.println("[BadgeService] 🌉 RETROACTIVELY UNLOCKING Bridge Builder for " + userId);
                        onInterCollegeMessage(userId);
                        return;
                    }
                }
            }

            System.out.println("   ℹ️ No inter-college messages found in history");
        } catch (Exception e) {
            System.err.println("❌ Error during retroactive badge check: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String extractDomain(String email) {
        if (email == null || !email.contains("@")) {
            return "";
        }
        return email.substring(email.indexOf("@") + 1).toLowerCase();
    }
}
