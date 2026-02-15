package com.studencollabfin.server.service;

import com.studencollabfin.server.model.HardModeBadge;
import com.studencollabfin.server.model.User;
import com.studencollabfin.server.repository.HardModeBadgeRepository;
import com.studencollabfin.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hard-Mode Badge System Service
 * Manages 20 elite badges with strict unlock criteria, daily limits, and
 * maintenance.
 */
@Service
@RequiredArgsConstructor
public class HardModeBadgeService {

    private final HardModeBadgeRepository hardModeBadgeRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ==================== BADGE DEFINITIONS ====================

    private static final Map<String, BadgeDefinition> BADGE_DEFINITIONS = new LinkedHashMap<>();

    static {
        // Initialize all 20 hard-mode badges
        BADGE_DEFINITIONS.put("discussion-architect", new BadgeDefinition(
                "discussion-architect", "Discussion Architect",
                "Start a Global Hub thread that reaches 50+ total replies.",
                "LEGENDARY", "gold-glow", 50, "replies"));
        BADGE_DEFINITIONS.put("active-talker-elite", new BadgeDefinition(
                "active-talker-elite", "Active Talker (Elite)",
                "Post 150 replies across various college threads in 7 days.",
                "EPIC", "purple-shimmer", 150, "weeklyReplies"));
        BADGE_DEFINITIONS.put("ultra-responder", new BadgeDefinition(
                "ultra-responder", "Ultra-Responder", "Reply to an Inbox message in <30 seconds, 20 times in a row.",
                "RARE", "electric-blue", 20, "fastReplies"));
        BADGE_DEFINITIONS.put("midnight-legend", new BadgeDefinition(
                "midnight-legend", "Midnight Legend",
                "Post a reply in the Global Hub between 2 AM – 4 AM for 3 nights straight.",
                "RARE", "dark-moon-glow", 3, "midnightReplies"));
        BADGE_DEFINITIONS.put("bridge-master", new BadgeDefinition(
                "bridge-master", "Bridge Master", "Start DMs with students from 5 different colleges in 24 hours.",
                "EPIC", "green-aurora", 5, "crossCollegeDMs"));
        BADGE_DEFINITIONS.put("doubt-destroyer", new BadgeDefinition(
                "doubt-destroyer", "Doubt Destroyer", "Provide the first reply to 25 questions tagged as #HelpNeeded.",
                "EPIC", "ruby-red", 25, "helpNeededReplies"));
        BADGE_DEFINITIONS.put("resource-titan", new BadgeDefinition(
                "resource-titan", "Resource Titan", "Have 25 of your shared files/links \"pinned\" by other users.",
                "LEGENDARY", "emerald-shine", 25, "pinnedResources"));
        BADGE_DEFINITIONS.put("lead-architect", new BadgeDefinition(
                "lead-architect", "Lead Architect",
                "Fill 10 Collab Rooms with members from 4+ different colleges each.",
                "LEGENDARY", "molten-gold", 10, "multiCollegeCollabRooms"));
        BADGE_DEFINITIONS.put("team-engine", new BadgeDefinition(
                "team-engine", "Team Engine", "Join 15 Collab Rooms and contribute 20+ replies to each.",
                "EPIC", "cobalt-steel", 15, "activeCollabRooms"));
        BADGE_DEFINITIONS.put("first-responder", new BadgeDefinition(
                "first-responder", "First Responder",
                "Be the first reply to a campus question within 30 minutes of posting.",
                "COMMON", "silver-gloss", 1, "firstReplyCount" // Progressive badge
        ));
        BADGE_DEFINITIONS.put("streak-seeker-lvl3", new BadgeDefinition(
                "streak-seeker-lvl3", "Streak Seeker (Lvl 3)",
                "Log in for 100 consecutive days. (Progress resets to 0 if one day is missed).",
                "LEGENDARY", "animated-fire", 100, "loginStreak"));
        BADGE_DEFINITIONS.put("collab-master-lvl3", new BadgeDefinition(
                "collab-master-lvl3", "Collab Master (Lvl 3)", "Contribute to 50 different Collab Rooms total.",
                "EPIC", "cyan-pulse", 50, "totalCollabRooms"));
        BADGE_DEFINITIONS.put("voice-of-hub-lvl3", new BadgeDefinition(
                "voice-of-hub-lvl3", "Voice of the Hub (Lvl 3)", "Reach 1,500 total replies in the Global Hub.",
                "LEGENDARY", "solar-flare", 1500, "totalReplies"));
        BADGE_DEFINITIONS.put("profile-perfectionist", new BadgeDefinition(
                "profile-perfectionist", "Profile Perfectionist",
                "Fill all fields and update \"Project Links\" every 30 days to keep the badge.",
                "COMMON", "polished-chrome", 1, "profileMaintenance"));
        BADGE_DEFINITIONS.put("the-oracle-gm", new BadgeDefinition(
                "the-oracle-gm", "The Oracle (GM)", "Correctly predict the winning outcome of 50 community Polls.",
                "EPIC", "amethyst-eye", 50, "correctPolls"));
        BADGE_DEFINITIONS.put("silent-sentinel", new BadgeDefinition(
                "silent-sentinel", "Silent Sentinel", "Reach 500 replies with a 100% report-free record.",
                "RARE", "white-marble", 500, "reportFreeTotalReplies"));
        BADGE_DEFINITIONS.put("campus-helper", new BadgeDefinition(
                "campus-helper", "Campus Helper",
                "Provide 10 replies that are marked as \"Helpful\" by institutional peers.",
                "COMMON", "bronze-oak", 10, "helpfulReplies"));
        BADGE_DEFINITIONS.put("event-vanguard", new BadgeDefinition(
                "event-vanguard", "Event Vanguard", "Reply to an Event announcement within 5 minutes of its creation.",
                "RARE", "orange-neon", 1, "eventRepliesCount" // Progressive badge
        ));
        BADGE_DEFINITIONS.put("cross-domain-pro", new BadgeDefinition(
                "cross-domain-pro", "Cross-Domain Pro",
                "Join Collab Rooms in 5 different academic branches (IT, Mech, Civil, etc).",
                "EPIC", "multicolor-prism", 5, "academicBranchesCount"));
        BADGE_DEFINITIONS.put("spam-alert-sanction", new BadgeDefinition(
                "spam-alert-sanction", "Spam Alert (Sanction)",
                "Triggered by any valid report; locks profile for 24 hours.",
                "PENALTY", "red-pulsing-cross", 1, "reportCount"));
    }

    // ==================== CORE METHODS ====================

    /**
     * Initialize hard-mode badges for a new user.
     */
    public void initializeHardModeBadgesForUser(String userId) {
        System.out.println("[HardModeBadgeService] Initializing hard-mode badges for user: " + userId);

        BADGE_DEFINITIONS.forEach((badgeId, definition) -> {
            HardModeBadge badge = new HardModeBadge();
            badge.setUserId(userId);
            badge.setBadgeId(badgeId);
            badge.setBadgeName(definition.name);
            badge.setTier(definition.tier);
            badge.setVisualStyle(definition.visualStyle);
            badge.setProgressTotal(definition.threshold);
            badge.setProgressCurrent(0);
            badge.setUnlocked(false);
            badge.setEquipped(false);
            badge.setProgressData(new HashMap<>());

            hardModeBadgeRepository.save(badge);
        });

        System.out.println("[HardModeBadgeService] ✅ Hard-mode badges initialized for user: " + userId);
    }

    /**
     * Check if user can unlock more badges today (max 2 per day).
     */
    public boolean canUnlockMoreToday(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return false;

        LocalDate today = LocalDate.now();

        // If lastUnlockDate is not today, reset count
        if (user.getLastUnlockDate() == null || !user.getLastUnlockDate().isEqual(today)) {
            user.setDailyUnlocksCount(0);
            user.setLastUnlockDate(today);
            userRepository.save(user);
            return true;
        }

        return user.getDailyUnlocksCount() < 2;
    }

    /**
     * Get remaining unlocks for today.
     */
    public int getRemainUnlocksToday(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return 0;

        LocalDate today = LocalDate.now();
        if (user.getLastUnlockDate() == null || !user.getLastUnlockDate().isEqual(today)) {
            return 2;
        }

        return Math.max(0, 2 - user.getDailyUnlocksCount());
    }

    /**
     * Try to unlock a specific badge. Enforces daily limit.
     */
    public Map<String, Object> tryUnlockBadge(String userId, String badgeId) {
        Map<String, Object> result = new HashMap<>();

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            result.put("success", false);
            result.put("message", "User not found");
            return result;
        }

        HardModeBadge badge = hardModeBadgeRepository.findByUserIdAndBadgeId(userId, badgeId).orElse(null);
        if (badge == null) {
            result.put("success", false);
            result.put("message", "Badge not found");
            return result;
        }

        // Check if badge already equipped
        if (badge.isEquipped()) {
            result.put("success", false);
            result.put("message", "Badge already equipped");
            return result;
        }

        // Check if unlock criteria met
        if (!badge.isUnlocked()) {
            result.put("success", false);
            result.put("message",
                    "Badge criteria not met. Progress: " + badge.getProgressCurrent() + "/" + badge.getProgressTotal());
            result.put("progress", Map.of("current", badge.getProgressCurrent(), "total", badge.getProgressTotal()));
            return result;
        }

        // Check daily limit
        if (!canUnlockMoreToday(userId)) {
            // Save to locked list for later
            if (!user.getHardModeBadgesLocked().contains(badgeId)) {
                user.getHardModeBadgesLocked().add(badgeId);
                userRepository.save(user);
            }

            result.put("success", false);
            result.put("status", "pending-unlock");
            result.put("message", "Daily unlock limit reached. Badge waiting for tomorrow.");
            result.put("remainingTime", getTimeUntilMidnight());
            return result;
        }

        // Equip the badge
        badge.setEquipped(true);
        badge.setEquippedAt(LocalDateTime.now());
        hardModeBadgeRepository.save(badge);

        // Add to user's hard-mode badges and increment daily count
        if (!user.getHardModeBadgesEarned().contains(badgeId)) {
            user.getHardModeBadgesEarned().add(badgeId);
        }
        if (user.getHardModeBadgesLocked().contains(badgeId)) {
            user.getHardModeBadgesLocked().remove(badgeId);
        }

        user.setDailyUnlocksCount(user.getDailyUnlocksCount() + 1);
        user.setLastUnlockDate(LocalDate.now());
        userRepository.save(user);

        // Broadcast unlock via WebSocket
        broadcastBadgeUnlock(userId, badge);

        result.put("success", true);
        result.put("message", "🎉 " + badge.getBadgeName() + " badge unlocked!");
        result.put("badgeName", badge.getBadgeName());
        result.put("tier", badge.getTier());
        result.put("visualStyle", badge.getVisualStyle());

        return result;
    }

    /**
     * Check individual badge unlock criteria and auto-unlock if met.
     */
    public void checkAndUnlockBadgeCriteria(String userId, String badgeId) {
        User user = userRepository.findById(userId).orElse(null);
        HardModeBadge badge = hardModeBadgeRepository.findByUserIdAndBadgeId(userId, badgeId).orElse(null);

        if (user == null || badge == null || badge.isUnlocked())
            return;

        boolean criteriaMetNow = evaluateBadgeCriteria(badge, user);

        if (criteriaMetNow && !badge.isUnlocked()) {
            badge.setUnlocked(true);
            badge.setUnlockedAt(LocalDateTime.now());
            hardModeBadgeRepository.save(badge);

            System.out.println(
                    "[HardModeBadgeService] ✅ Badge criteria met: " + badge.getBadgeName() + " for user " + userId);

            // Notify user via WebSocket
            messagingTemplate.convertAndSendToUser(
                    userId, "/queue/badge-criterion-met",
                    Map.of(
                            "badgeName", badge.getBadgeName(),
                            "message",
                            "🎯 " + badge.getBadgeName()
                                    + " criteria unlocked! Use /api/badges/hard-mode/unlock to equip.",
                            "timestamp", System.currentTimeMillis()));
        }
    }

    /**
     * Evaluate badge unlock criteria based on badge ID and user stats.
     */
    private boolean evaluateBadgeCriteria(HardModeBadge badge, User user) {
        switch (badge.getBadgeId()) {
            case "discussion-architect":
                // Requires 50+ replies to a single post
                return badge.getProgressCurrent() >= 50;

            case "active-talker-elite":
                // Requires 150 replies in 7 days
                return user.getWeeklyReplies() >= 150;

            case "ultra-responder":
                // Requires 20 fast replies (<30 seconds)
                return badge.getProgressCurrent() >= 20;

            case "midnight-legend":
                // Requires 3 nights of midnight replies
                return badge.getProgressCurrent() >= 3;

            case "bridge-master":
                // Requires DMs with 5 different colleges in 24 hours
                return badge.getProgressCurrent() >= 5;

            case "doubt-destroyer":
                // Requires first reply to 25 #HelpNeeded questions
                return user.getStatsMap().getOrDefault("helpNeededReplies", 0) >= 25;

            case "resource-titan":
                // Requires 25 pinned resources
                return user.getStatsMap().getOrDefault("pinnedResources", 0) >= 25;

            case "lead-architect":
                // Requires 10 multi-college collab rooms
                return user.getStatsMap().getOrDefault("multiCollegeCollabRooms", 0) >= 10;

            case "team-engine":
                // Requires 15 active collab rooms with 20+ replies each
                return user.getStatsMap().getOrDefault("activeCollabRooms", 0) >= 15;

            case "first-responder":
                // Progressive badge - track first replies
                return badge.getProgressCurrent() >= 1;

            case "streak-seeker-lvl3":
                // Requires 100 day login streak
                return user.getLoginStreak() >= 100;

            case "collab-master-lvl3":
                // Requires 50 different collab rooms
                return user.getStatsMap().getOrDefault("totalCollabRooms", 0) >= 50;

            case "voice-of-hub-lvl3":
                // Requires 1500 total replies
                return user.getTotalReplies() >= 1500;

            case "profile-perfectionist":
                // Profile complete and updated within 30 days
                return isProfileMaintained(user);

            case "the-oracle-gm":
                // Correctly predicted 50 polls
                return user.getStatsMap().getOrDefault("correctPolls", 0) >= 50;

            case "silent-sentinel":
                // 500 replies with 0 reports
                return user.getTotalReplies() >= 500 && user.getReportCount() == 0;

            case "campus-helper":
                // 10 replies marked as helpful
                return user.getStatsMap().getOrDefault("helpfulReplies", 0) >= 10;

            case "event-vanguard":
                // Progressive badge
                return badge.getProgressCurrent() >= 1;

            case "cross-domain-pro":
                // 5 different academic branches
                return user.getStatsMap().getOrDefault("academicBranchesCount", 0) >= 5;

            case "spam-alert-sanction":
                // Triggered by reports
                return user.getReportCount() > 0;

            default:
                return false;
        }
    }

    /**
     * Track a reply/response action and update relevant badge progress.
     */
    public void trackReplyAction(String userId, String replyType, Map<String, Object> metadata) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return;

        // Update core metrics
        user.setTotalReplies(user.getTotalReplies() + 1);
        user.setWeeklyReplies(user.getWeeklyReplies() + 1);

        // Update badge progress based on reply type
        switch (replyType) {
            case "help-needed-first-reply":
                user.getStatsMap().put("helpNeededReplies",
                        user.getStatsMap().getOrDefault("helpNeededReplies", 0) + 1);
                break;

            case "fast-reply":
                // Under 30 seconds
                HardModeBadge ultraResponder = hardModeBadgeRepository.findByUserIdAndBadgeId(userId, "ultra-responder")
                        .orElse(null);
                if (ultraResponder != null) {
                    ultraResponder.setProgressCurrent(ultraResponder.getProgressCurrent() + 1);
                    hardModeBadgeRepository.save(ultraResponder);
                    checkAndUnlockBadgeCriteria(userId, "ultra-responder");
                }
                break;

            case "midnight-reply":
                // Between 2-4 AM
                HardModeBadge midnightLegend = hardModeBadgeRepository.findByUserIdAndBadgeId(userId, "midnight-legend")
                        .orElse(null);
                if (midnightLegend != null) {
                    midnightLegend.setProgressCurrent(midnightLegend.getProgressCurrent() + 1);
                    hardModeBadgeRepository.save(midnightLegend);
                    checkAndUnlockBadgeCriteria(userId, "midnight-legend");
                }
                break;
        }

        // Check criteria for affected badges
        checkAndUnlockBadgeCriteria(userId, "active-talker-elite");
        checkAndUnlockBadgeCriteria(userId, "doubt-destroyer");
        checkAndUnlockBadgeCriteria(userId, "voice-of-hub-lvl3");
        checkAndUnlockBadgeCriteria(userId, "silent-sentinel");

        userRepository.save(user);

        // ✅ BROADCAST STAT UPDATES: Send updated stats to user via WebSocket
        try {
            Map<String, Object> statUpdate = new HashMap<>();
            statUpdate.put("totalReplies", user.getTotalReplies());
            statUpdate.put("postsCount", user.getPostsCount());
            statUpdate.put("loginStreak", user.getLoginStreak());
            statUpdate.put("correctPolls", user.getStatsMap().getOrDefault("correctPolls", 0));
            statUpdate.put("collabRoomsJoined", user.getStatsMap().getOrDefault("collabRoomsJoined", 0));
            statUpdate.put("statsMap", user.getStatsMap());

            if (messagingTemplate != null) {
                messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/stat-update",
                        statUpdate);
                System.out.println("[HardModeBadgeService] ✅ Stat update broadcasted to user: " + userId);
            }
        } catch (Exception e) {
            System.err.println("[HardModeBadgeService] ⚠️ Failed to broadcast stat update: " + e.getMessage());
        }
    }

    /**
     * Track login and update streak.
     */
    public void trackLogin(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return;

        LocalDate today = LocalDate.now();
        LocalDate lastLogin = user.getLastLoginDate();

        if (lastLogin == null) {
            // First login
            user.setLoginStreak(1);
            user.setLastLoginDate(today);
        } else if (lastLogin.isEqual(today)) {
            // Already logged in today
            return;
        } else if (lastLogin.plusDays(1).isEqual(today)) {
            // Consecutive day
            user.setLoginStreak(user.getLoginStreak() + 1);
            user.setLastLoginDate(today);
        } else {
            // Streak broken
            user.setLoginStreak(1);
            user.setLastLoginDate(today);

            // Remove maintenance badges
            removeBadgesRequiringMaintenance(userId);
        }

        userRepository.save(user);
        checkAndUnlockBadgeCriteria(userId, "streak-seeker-lvl3");
    }

    /**
     * Scheduled task to run at midnight - reset daily unlock count and check
     * streaks.
     */
    @Scheduled(cron = "0 0 0 * * *") // Every day at midnight
    public void midnightMaintenanceTask() {
        System.out.println("[HardModeBadgeService] 🌙 Running midnight maintenance...");

        // Unlock any pending badges from yesterday
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            if (!user.getHardModeBadgesLocked().isEmpty()) {
                String firstLockedBadge = user.getHardModeBadgesLocked().get(0);
                tryUnlockBadge(user.getId(), firstLockedBadge);
            }

            // Check streak
            LocalDate lastLogin = user.getLastLoginDate();
            if (lastLogin != null && !lastLogin.isEqual(LocalDate.now())) {
                if (user.getLoginStreak() > 0) {
                    System.out.println("[HardModeBadgeService] ⚠️ Login streak broken for user: " + user.getId());
                    user.setLoginStreak(0);
                    removeBadgesRequiringMaintenance(user.getId());
                    userRepository.save(user);
                }
            }
        }

        System.out.println("[HardModeBadgeService] ✅ Midnight maintenance complete");
    }

    /**
     * Scheduled task to reset weekly reply count every 7 days.
     */
    @Scheduled(cron = "0 0 0 * * MON") // Every Monday at midnight
    public void weeklyResetTask() {
        System.out.println("[HardModeBadgeService] 📅 Resetting weekly reply counts...");
        List<User> allUsers = userRepository.findAll();

        for (User user : allUsers) {
            user.setWeeklyReplies(0);
            userRepository.save(user);
        }

        System.out.println("[HardModeBadgeService] ✅ Weekly reset complete");
    }

    /**
     * Auto-remove badges that require active maintenance if criteria no longer met.
     */
    private void removeBadgesRequiringMaintenance(String userId) {
        // Remove: Profile Perfectionist, Streak Seeker
        hardModeBadgeRepository.findByUserIdAndIsEquippedTrue(userId).forEach(badge -> {
            if (badge.getBadgeId().equals("profile-perfectionist") ||
                    badge.getBadgeId().equals("streak-seeker-lvl3")) {
                badge.setEquipped(false);
                hardModeBadgeRepository.save(badge);

                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    user.getHardModeBadgesEarned().remove(badge.getBadgeId());
                    userRepository.save(user);
                }
            }
        });
    }

    /**
     * Check if profile is maintained (all fields filled + updated within 30 days).
     */
    private boolean isProfileMaintained(User user) {
        if (user.getFullName() == null || user.getFullName().isEmpty())
            return false;
        if (user.getCollegeName() == null || user.getCollegeName().isEmpty())
            return false;
        if (user.getDepartment() == null || user.getDepartment().isEmpty())
            return false;
        if (user.getPortfolioUrl() == null || user.getPortfolioUrl().isEmpty())
            return false;

        // Check if updated within 30 days
        if (user.getCreatedAt() != null) {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            return user.getCreatedAt().isAfter(thirtyDaysAgo);
        }

        return false;
    }

    /**
     * Get all hard-mode badges for user with current progress.
     */
    public List<Map<String, Object>> getUserHardModeBadges(String userId) {
        System.out.println("[HardModeBadgeService] 🔍 Fetching badges for user: " + userId);

        List<HardModeBadge> badges = hardModeBadgeRepository.findByUserId(userId);
        System.out.println("[HardModeBadgeService] 📊 Found " + (badges != null ? badges.size() : 0) + " badges");

        User user = userRepository.findById(userId).orElse(null);

        if (badges == null || badges.isEmpty()) {
            System.out.println("[HardModeBadgeService] ⚠️ No badges in database for user " + userId);
            return new ArrayList<>();
        }

        return badges.stream().map(badge -> {
            Map<String, Object> badgeInfo = new HashMap<>();
            badgeInfo.put("badgeId", badge.getBadgeId());
            badgeInfo.put("badgeName", badge.getBadgeName());
            badgeInfo.put("tier", badge.getTier());
            badgeInfo.put("visualStyle", badge.getVisualStyle());
            badgeInfo.put("progress", Map.of("current", badge.getProgressCurrent(), "total", badge.getProgressTotal()));
            badgeInfo.put("isUnlocked", badge.isUnlocked());
            badgeInfo.put("isEquipped", badge.isEquipped());
            badgeInfo.put("unlockedAt", badge.getUnlockedAt());

            // Check if locked by daily limit
            if (badge.isUnlocked() && !badge.isEquipped() && user != null &&
                    user.getHardModeBadgesLocked().contains(badge.getBadgeId())) {
                badgeInfo.put("status", "pending-unlock");
                badgeInfo.put("remainingTime", getTimeUntilMidnight());
            } else if (!badge.isUnlocked()) {
                badgeInfo.put("status", "locked");
            } else if (badge.isEquipped()) {
                badgeInfo.put("status", "equipped");
            }

            return badgeInfo;
        }).collect(Collectors.toList());
    }

    /**
     * Broadcast badge unlock to user via WebSocket.
     */
    private void broadcastBadgeUnlock(String userId, HardModeBadge badge) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId, "/queue/badge-unlock",
                    Map.of(
                            "badgeName", badge.getBadgeName(),
                            "tier", badge.getTier(),
                            "visualStyle", badge.getVisualStyle(),
                            "message", "🎉 " + badge.getBadgeName() + " badge unlocked and equipped!",
                            "timestamp", System.currentTimeMillis()));
        } catch (Exception e) {
            System.err.println("[HardModeBadgeService] ⚠️ WebSocket broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Calculate milliseconds until midnight for countdown timer.
     */
    private long getTimeUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.plusDays(1).toLocalDate().atStartOfDay();
        return java.time.Duration.between(now, midnight).toMillis();
    }

    // ==================== HELPER CLASSES ====================

    static class BadgeDefinition {
        String id;
        String name;
        String description;
        String tier;
        String visualStyle;
        int threshold;
        String trackingMetric;

        public BadgeDefinition(String id, String name, String description, String tier,
                String visualStyle, int threshold, String trackingMetric) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.tier = tier;
            this.visualStyle = visualStyle;
            this.threshold = threshold;
            this.trackingMetric = trackingMetric;
        }
    }
}
