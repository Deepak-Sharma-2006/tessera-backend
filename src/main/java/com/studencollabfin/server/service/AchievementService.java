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
        System.out.println("[BadgeService] 🌱 Pod Pioneer badge unlocked for user " + userId);
    }

    public void onInterCollegeMessage(String userId) {
        unlockAchievement(userId, "Bridge Builder");
        System.out.println("[BadgeService] 🌉 Bridge Builder badge unlocked for user " + userId);
    }

    public void unlockAchievement(String userId, String title) {
        @SuppressWarnings("null")
        Achievement achievement = achievementRepository.findByUserIdAndTitle(userId, title).orElse(null);
        if (achievement != null && !achievement.isUnlocked()) {
            achievement.setUnlocked(true);
            achievement.setUnlockedAt(LocalDateTime.now());
            achievementRepository.save(achievement);

            // ✅ CRITICAL: Also add badge to user.badges array in MongoDB so frontend can
            // check it
            @SuppressWarnings("null")
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                if (user.getBadges() == null) {
                    user.setBadges(new ArrayList<>());
                }
                if (!user.getBadges().contains(title)) {
                    user.getBadges().add(title);
                    userRepository.save(user);

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
                }
            }

            notificationService.notifyUser(userId, Map.of("type", "ACHIEVEMENT_UNLOCKED", "title", title));
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

        System.out.println("\n🔄 SYNCING BADGES FOR USER: " + user.getId());
        System.out.println("   isDev: " + user.isDev() + " | role: " + user.getRole());

        // ╔════════════════════════════════════════════════════════════════╗
        // ║ BADGE 1: FOUNDING DEV - 100% isDev DEPENDENT ║
        // ║ DOES NOT depend on role. INDEPENDENT of Campus Catalyst. ║
        // ╚════════════════════════════════════════════════════════════════╝

        if (user.isDev()) {
            // isDev is TRUE → Must have Founding Dev badge
            if (!currentBadges.contains("Founding Dev")) {
                currentBadges.add("Founding Dev");
                updated = true;
                System.out.println("   ✅ ACTION: ADDED 'Founding Dev' (isDev=true)");
                System.out.println(
                        "   [BadgeService] ✅ Unlocking Founding Dev and granting Event Creation privileges for user "
                                + user.getId());
            } else {
                System.out.println("   ℹ️ NO CHANGE: Already has 'Founding Dev'");
            }
        } else {
            // isDev is FALSE → Must NOT have Founding Dev badge
            if (currentBadges.contains("Founding Dev")) {
                currentBadges.remove("Founding Dev");
                updated = true;
                System.out.println("   ✅ ACTION: REMOVED 'Founding Dev' (isDev=false)");
                System.out.println("   [BadgeService] ❌ Revoking Founding Dev and Event Creation privileges for user "
                        + user.getId());
            }
        }

        // ╔════════════════════════════════════════════════════════════════╗
        // ║ BADGE 2: CAMPUS CATALYST - 100% ROLE DEPENDENT ║
        // ║ DOES NOT depend on isDev. INDEPENDENT of Founding Dev. ║
        // ╚════════════════════════════════════════════════════════════════╝

        if ("COLLEGE_HEAD".equals(user.getRole())) {
            // Role is COLLEGE_HEAD → Must have Campus Catalyst badge
            if (!currentBadges.contains("Campus Catalyst")) {
                currentBadges.add("Campus Catalyst");
                updated = true;
                System.out.println("   ✅ ACTION: ADDED 'Campus Catalyst' (role=COLLEGE_HEAD)");
                System.out.println(
                        "   [BadgeService] ✅ Unlocking Campus Catalyst and granting Event Creation privileges for user "
                                + user.getId());
            } else {
                System.out.println("   ℹ️ NO CHANGE: Already has 'Campus Catalyst'");
            }
        } else {
            // Role is NOT COLLEGE_HEAD → Must NOT have Campus Catalyst badge
            if (currentBadges.contains("Campus Catalyst")) {
                currentBadges.remove("Campus Catalyst");
                updated = true;
                System.out.println("   ✅ ACTION: REMOVED 'Campus Catalyst' (role != COLLEGE_HEAD)");
                System.out
                        .println("   [BadgeService] ❌ Revoking Campus Catalyst and Event Creation privileges for user "
                                + user.getId());
            }
        }

        // ╔════════════════════════════════════════════════════════════════╗
        // ║ BADGE 3: SKILL SAGE - endorsementsCount >= 3 ║
        // ╚════════════════════════════════════════════════════════════════╝

        if (user.getEndorsementsCount() >= 3) {
            if (!currentBadges.contains("Skill Sage")) {
                currentBadges.add("Skill Sage");
                updated = true;
                System.out
                        .println("   ✅ ACTION: ADDED 'Skill Sage' (endorsements=" + user.getEndorsementsCount() + ")");
            }
        } else {
            if (currentBadges.contains("Skill Sage")) {
                currentBadges.remove("Skill Sage");
                updated = true;
                System.out.println(
                        "   ✅ ACTION: REMOVED 'Skill Sage' (endorsements=" + user.getEndorsementsCount() + ")");
            }
        }

        // ╔════════════════════════════════════════════════════════════════╗
        // ║ BADGE 4: SIGNAL GUARDIAN - postsCount >= 5 ║
        // ╚════════════════════════════════════════════════════════════════╝

        if (user.getPostsCount() >= 5) {
            if (!currentBadges.contains("Signal Guardian")) {
                currentBadges.add("Signal Guardian");
                updated = true;
                System.out.println("   ✅ ACTION: ADDED 'Signal Guardian' (posts=" + user.getPostsCount() + ")");
            }
        } else {
            if (currentBadges.contains("Signal Guardian")) {
                currentBadges.remove("Signal Guardian");
                updated = true;
                System.out.println("   ✅ ACTION: REMOVED 'Signal Guardian' (posts=" + user.getPostsCount() + ")");
            }
        }

        // ╔════════════════════════════════════════════════════════════════╗
        // ║ BADGE 5: POD PIONEER - activity-based (permanent once earned) ║
        // ╚════════════════════════════════════════════════════════════════╝
        // Pod Pioneer is added by achievement trigger, never removed

        // SAVE if any updates were made
        if (updated) {
            user.setBadges(currentBadges);
            User savedUser = userRepository.save(user);
            System.out.println("   💾 SAVED: User badges updated in MongoDB");
            System.out.println("   📦 FINAL BADGES: " + savedUser.getBadges());
            System.out.println();
            return savedUser;
        } else {
            System.out.println("   ℹ️ NO CHANGES: Badges already synchronized");
            System.out.println("   📦 CURRENT BADGES: " + user.getBadges());
            System.out.println();
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
