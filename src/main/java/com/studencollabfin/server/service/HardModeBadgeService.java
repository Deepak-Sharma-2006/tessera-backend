package com.studencollabfin.server.service;

import com.studencollabfin.server.model.HardModeBadge;
import com.studencollabfin.server.model.Achievement;
import com.studencollabfin.server.model.User;
import com.studencollabfin.server.repository.AchievementRepository;
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
    private final AchievementRepository achievementRepository;
    private final GamificationService gamificationService;
    private final SimpMessagingTemplate messagingTemplate;

    // ==================== BADGE DEFINITIONS ====================

    private static final Map<String, BadgeDefinition> BADGE_DEFINITIONS = new LinkedHashMap<>();
    private static final Map<String, String> BADGE_REQUIREMENTS = Map.ofEntries(
            Map.entry("discussion-architect", "Create a Global Hub thread and grow that same thread to 50 replies."),
            Map.entry("active-talker-elite", "Accumulate 150 replies inside any rolling 7-day window."),
            Map.entry("ultra-responder", "Record 20 consecutive inbox replies with latency at or below 30 seconds."),
            Map.entry("midnight-legend",
                    "Post one qualifying Global Hub reply in the 2:00–3:59 AM IST window for 3 nights in a row."),
            Map.entry("bridge-master", "Start DMs with 5 distinct domains in a rolling 24-hour window."),
            Map.entry("doubt-destroyer", "Be the first reply on 25 ASK_HELP posts."),
            Map.entry("resource-titan", "Earn 50 resource thread points; each thread contributes at most once."),
            Map.entry("lead-architect", "Create or fill 10 collab rooms where each includes 4+ distinct colleges."),
            Map.entry("team-engine", "Reach 20 replies in each of 15 different COLLAB_ROOM threads."),
            Map.entry("first-responder", "Be the first reply and post it within 30 minutes."),
            Map.entry("streak-seeker-lvl3",
                    "Complete 100 consecutive IST-day logins; missing a day resets progress."),
            Map.entry("collab-master-lvl3", "Reply in 50 unique COLLAB_ROOM threads."),
            Map.entry("voice-of-hub-lvl3", "Post 1,500 replies counted only from Global Hub scope."),
            Map.entry("profile-perfectionist", "Fill all required profile fields and save your profile updates."),
            Map.entry("the-oracle-gm", "In 100 polls, vote for the option leading at the moment you vote."),
            Map.entry("silent-sentinel",
                    "Maintain a 500-message DM streak with one partner who keeps your report status clean."),
            Map.entry("campus-helper", "Reply to 50 different ASK_HELP posts where you are not the author."),
            Map.entry("event-vanguard", "Read 30 distinct EVENT notifications within 60 minutes of creation."),
            Map.entry("cross-domain-pro", "Participate in collab activity spanning 5 distinct academic branches."),
            Map.entry("spam-alert-sanction",
                    "Three valid reports from the same user trigger a 24-hour DM ban for that user pair."),
            Map.entry("founding-dev", "Assigned the admin/developer role by an authorized admin."),
            Map.entry("campus-catalyst", "Assigned a verified campus leader role by authorized admins."),
            Map.entry("pod-pioneer", "Join one collaboration pod for the first time."),
            Map.entry("bridge-builder", "Send one inter-college or inter-domain direct message."));

    private static final Map<String, String> BADGE_UNLOCK_TIPS = Map.ofEntries(
            Map.entry("discussion-architect",
                    "Start a Global Hub thread and push it to 50 total replies. One breakout thread is enough."),
            Map.entry("active-talker-elite",
                    "Post 150 replies in a rolling 7-day window. Keep your reply pace consistent all week."),
            Map.entry("ultra-responder",
                    "Land 20 fast replies in a row with latency at 30 seconds or less. A slow reply resets the streak."),
            Map.entry("midnight-legend", "Reply in Global Hub between 2:00 and 3:59 AM IST for 3 consecutive nights."),
            Map.entry("bridge-master", "Send DMs to 5 distinct domains within a rolling 24-hour window."),
            Map.entry("doubt-destroyer",
                    "Be the first reply on 25 ASK_HELP posts. Speed and first-response consistency matter."),
            Map.entry("resource-titan",
                    "Earn 50 thread points by posting resources or sharing links in other users' threads. Each thread counts once."),
            Map.entry("lead-architect",
                    "Create or fill 10 collab rooms with at least 4 distinct colleges in each room."),
            Map.entry("team-engine", "In 15 different COLLAB_ROOM threads, reach 20 replies per room."),
            Map.entry("first-responder", "Be the first reply and do it within 30 minutes."),
            Map.entry("streak-seeker-lvl3",
                    "Log in for 100 consecutive IST days. Break the chain and the streak restarts from 1."),
            Map.entry("collab-master-lvl3", "Reply in 50 distinct COLLAB_ROOM threads."),
            Map.entry("voice-of-hub-lvl3", "Post 1,500 replies in Global Hub scope. Only Global Hub replies count."),
            Map.entry("profile-perfectionist", "Complete your profile fully and save it."),
            Map.entry("the-oracle-gm", "Vote for the option currently leading at vote time in 100 polls."),
            Map.entry("silent-sentinel", "Build a 500-message streak with one DM partner who has not reported you."),
            Map.entry("campus-helper", "Reply to 50 distinct ASK_HELP posts where you are not the author."),
            Map.entry("event-vanguard",
                    "Read 30 distinct EVENT notifications within 60 minutes. Re-reading the same event won't count."),
            Map.entry("cross-domain-pro", "Participate across 5 distinct academic branches in collab activity."),
            Map.entry("spam-alert-sanction",
                    "If the same user reports you 3 times, a 24-hour DM ban applies for that user."),
            Map.entry("founding-dev", "Granted by admin/developer role assignment."),
            Map.entry("campus-catalyst", "Granted by authorized campus leader role assignment."),
            Map.entry("pod-pioneer", "Join your first collaboration pod."),
            Map.entry("bridge-builder", "Send an inter-college/inter-domain DM."));

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
                "resource-titan", "Resource Titan",
                "Earn 50 points by sharing resources in posts or links in others' threads.",
                "LEGENDARY", "emerald-shine", 50, "resourceThreads"));
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
                "the-oracle-gm", "The Oracle (GM)",
                "Choose the majority-leading option at vote time in 100 community polls.",
                "EPIC", "amethyst-eye", 100, "majorityChoicePolls"));
        BADGE_DEFINITIONS.put("silent-sentinel", new BadgeDefinition(
                "silent-sentinel", "Silent Sentinel", "Reach 500 replies with a 100% report-free record.",
                "RARE", "white-marble", 500, "reportFreeTotalReplies"));
        BADGE_DEFINITIONS.put("campus-helper", new BadgeDefinition(
                "campus-helper", "Campus Helper",
                "Reply to 50 distinct ASK_HELP posts where you are not the author.",
                "COMMON", "bronze-oak", 50, "distinctHelpThreads"));
        BADGE_DEFINITIONS.put("event-vanguard", new BadgeDefinition(
                "event-vanguard", "Event Vanguard",
                "Open and mark an EVENT notification as read within 1 hour of creation for 30 distinct events.",
                "RARE", "orange-neon", 30, "eventNotificationReads"));
        BADGE_DEFINITIONS.put("cross-domain-pro", new BadgeDefinition(
                "cross-domain-pro", "Cross-Domain Pro",
                "Join Collab Rooms in 5 different academic branches (IT, Mech, Civil, etc).",
                "EPIC", "multicolor-prism", 5, "academicBranchesCount"));
        BADGE_DEFINITIONS.put("spam-alert-sanction", new BadgeDefinition(
                "spam-alert-sanction", "Spam Alert (Sanction)",
                "Triggered by any valid report; locks profile for 24 hours.",
                "PENALTY", "red-pulsing-cross", 1, "reportCount"));

        // ✅ POWER-FIVE BADGES (Special Management Badges)
        BADGE_DEFINITIONS.put("founding-dev", new BadgeDefinition(
                "founding-dev", "Founding Dev",
                "Platform architect and founding developer.",
                "LEGENDARY", "gold-glow", 1, "isDev"));
        BADGE_DEFINITIONS.put("campus-catalyst", new BadgeDefinition(
                "campus-catalyst", "Campus Catalyst",
                "Authorized event creator and campus leader.",
                "EPIC", "purple-shimmer", 1, "role"));
        BADGE_DEFINITIONS.put("pod-pioneer", new BadgeDefinition(
                "pod-pioneer", "Pod Pioneer",
                "Joined your first collaboration pod.",
                "UNCOMMON", "green-shine", 1, "podJoined"));
        BADGE_DEFINITIONS.put("bridge-builder", new BadgeDefinition(
                "bridge-builder", "Bridge Builder",
                "Collaborated across colleges.",
                "RARE", "cyan-bridge", 1, "interCollege"));
    }

    // ==================== CORE METHODS ====================

    public record BadgeMetadata(String badgeId, String badgeName, String tier, String visualStyle, int threshold) {
    }

    public BadgeMetadata getBadgeMetadata(String badgeId) {
        BadgeDefinition definition = BADGE_DEFINITIONS.get(badgeId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown badgeId: " + badgeId);
        }

        return new BadgeMetadata(
                definition.id,
                definition.name,
                definition.tier,
                definition.visualStyle,
                definition.threshold);
    }

    public void awardBadge(String userId, String badgeId) {
        HardModeBadge badge = hardModeBadgeRepository.findByUserIdAndBadgeId(userId, badgeId).orElse(null);
        if (badge == null || badge.isUnlocked()) {
            return;
        }

        if (badge.getProgressCurrent() < badge.getProgressTotal()) {
            return;
        }

        if (isInvalidated(badge)) {
            return;
        }

        badge.setUnlocked(true);
        badge.setUnlockedAt(LocalDateTime.now());
        hardModeBadgeRepository.save(badge);

        persistHardModeBadgeEarned(userId, badgeId);
        awardHardModeUnlockRewards(userId, badge);

        messagingTemplate.convertAndSendToUser(
                userId, "/queue/badge-criterion-met",
                Map.of(
                        "badgeName", badge.getBadgeName(),
                        "message",
                        "🎯 " + badge.getBadgeName() + " criteria unlocked! Use /api/badges/hard-mode/unlock to equip.",
                        "timestamp", System.currentTimeMillis()));

        broadcastBadgeAddedEvent(userId, badge, "🎯 " + badge.getBadgeName() + " criteria met!");
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
            awardBadge(userId, badgeId);

            System.out.println(
                    "[HardModeBadgeService] ✅ Badge criteria met: " + badge.getBadgeName() + " for user " + userId);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isInvalidated(HardModeBadge badge) {
        if (badge == null || badge.getProgressData() == null) {
            return false;
        }

        Object invalidated = badge.getProgressData().get("silentSentinelInvalidated");
        if (invalidated instanceof Boolean) {
            return (Boolean) invalidated;
        }
        return false;
    }

    private void awardHardModeUnlockRewards(String userId, HardModeBadge badge) {
        if (userId == null || userId.isEmpty() || badge == null) {
            return;
        }

        try {
            Achievement achievement = achievementRepository
                    .findByUserIdAndTitle(userId, badge.getBadgeName())
                    .orElse(null);

            if (achievement == null) {
                achievement = new Achievement();
                achievement.setUserId(userId);
                achievement.setTitle(badge.getBadgeName());
                achievement.setDescription("Hard-mode achievement: " + badge.getBadgeName());
                achievement.setType(Achievement.AchievementType.HARD_MODE);
                achievement.setXpValue(25);
                achievement.setUnlocked(true);
                achievement.setUnlockedAt(LocalDateTime.now());
                achievementRepository.save(achievement);
            } else {
                if (!achievement.isUnlocked()) {
                    achievement.setUnlocked(true);
                    achievement.setUnlockedAt(LocalDateTime.now());
                }
                achievement.setType(Achievement.AchievementType.HARD_MODE);
                achievement.setXpValue(25);
                achievementRepository.save(achievement);
            }

            gamificationService.addXp(userId, 25);
            System.out.println("[HardModeBadgeService] 💰 Awarded 25 XP for hard-mode unlock: " + badge.getBadgeId());
        } catch (Exception e) {
            System.err.println("[HardModeBadgeService] ⚠️ Failed to persist hard-mode reward: " + e.getMessage());
        }
    }

    private void persistHardModeBadgeEarned(String userId, String badgeId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return;
            }

            if (user.getHardModeBadgesEarned() == null) {
                user.setHardModeBadgesEarned(new ArrayList<>());
            }

            if (!user.getHardModeBadgesEarned().contains(badgeId)) {
                user.getHardModeBadgesEarned().add(badgeId);
                userRepository.save(user);
            }
        } catch (Exception e) {
            System.err.println(
                    "[HardModeBadgeService] ⚠️ Failed to persist hard-mode badge in user doc: " + e.getMessage());
        }
    }

    /**
     * Generic hard-mode event dispatcher used by other services/controllers.
     */
    public void trackEvent(String userId, String eventType, Map<String, Object> metadata) {
        if (userId == null || userId.isEmpty() || eventType == null || eventType.isEmpty()) {
            return;
        }

        switch (eventType) {
            case "reply":
            case "help-needed-first-reply":
            case "fast-reply":
            case "midnight-reply":
                trackReplyAction(userId, eventType, metadata != null ? metadata : Map.of());
                break;

            case "resource-upload":
            case "upload":
                incrementUserStatAndCheck(userId, "pinnedResources", "resource-titan");
                break;

            case "team-activity":
            case "team-join":
                incrementUserStatAndCheck(userId, "activeCollabRooms", "team-engine");
                break;

            case "discussion-start":
                incrementBadgeProgressAndCheck(userId, "discussion-architect");
                break;

            default:
                System.out.println("[HardModeBadgeService] ℹ️ Unknown event type ignored: " + eventType);
        }
    }

    private void incrementBadgeProgressAndCheck(String userId, String badgeId) {
        HardModeBadge badge = hardModeBadgeRepository.findByUserIdAndBadgeId(userId, badgeId).orElse(null);
        if (badge == null) {
            return;
        }

        badge.setProgressCurrent(badge.getProgressCurrent() + 1);
        hardModeBadgeRepository.save(badge);
        checkAndUnlockBadgeCriteria(userId, badgeId);
    }

    private void incrementUserStatAndCheck(String userId, String statKey, String badgeId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        if (user.getStatsMap() == null) {
            user.setStatsMap(new HashMap<>());
        }

        int currentValue = user.getStatsMap().getOrDefault(statKey, 0);
        user.getStatsMap().put(statKey, currentValue + 1);
        userRepository.save(user);

        checkAndUnlockBadgeCriteria(userId, badgeId);
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
                // Requires 150 replies tracked via badge progress
                return badge.getProgressCurrent() >= badge.getProgressTotal();

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
                // 500 replies and not invalidated by report event
                return badge.getProgressCurrent() >= badge.getProgressTotal() && !isInvalidated(badge);

            case "campus-helper":
                // 10 replies marked as helpful
                return user.getStatsMap().getOrDefault("helpfulReplies", 0) >= 10;

            case "event-vanguard":
                return badge.getProgressCurrent() >= badge.getProgressTotal();

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

        boolean isMidnight = readBooleanFlag(metadata, "midnight");
        boolean isFast = readBooleanFlag(metadata, "fast");
        boolean isHelp = readBooleanFlag(metadata, "help");

        // Backward-compatible aliases
        if ("midnight-reply".equals(replyType)) {
            isMidnight = true;
        }
        if ("fast-reply".equals(replyType)) {
            isFast = true;
        }
        if ("help-needed-first-reply".equals(replyType)) {
            isHelp = true;
        }

        // Update badge progress based on context and reply type
        switch (replyType) {
            case "help-needed-first-reply":
                user.getStatsMap().put("helpNeededReplies",
                        user.getStatsMap().getOrDefault("helpNeededReplies", 0) + 1);
                break;

            case "reply":
                // Context-driven processing handled below.
                break;
        }

        if (isHelp) {
            user.getStatsMap().put("helpNeededReplies",
                    user.getStatsMap().getOrDefault("helpNeededReplies", 0) + 1);
        }

        if (isFast) {
            incrementBadgeProgressAndCheck(userId, "ultra-responder");
            incrementBadgeProgressAndCheck(userId, "first-responder");
        }

        if (isMidnight) {
            incrementBadgeProgressAndCheck(userId, "midnight-legend");
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

    private boolean readBooleanFlag(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key)) {
            return false;
        }

        Object value = metadata.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
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
     * Get all hard-mode badges for user with hydration merge.
     * Returns exactly 24 badges by overlaying DB trackers on top of static
     * registry.
     */
    public List<Map<String, Object>> getUserHardModeBadges(String userId) {
        System.out.println("[HardModeBadgeService] 🔍 Fetching badges for user: " + userId);

        List<HardModeBadge> hardModeBadges = hardModeBadgeRepository.findByUserId(userId);
        User user = userRepository.findById(userId).orElse(null);

        System.out.println("[HardModeBadgeService] 📊 Found " + (hardModeBadges != null ? hardModeBadges.size() : 0)
                + " hard-mode badges");

        if (hardModeBadges == null) {
            hardModeBadges = new ArrayList<>();
        }

        Map<String, HardModeBadge> trackerByBadgeId = hardModeBadges.stream()
                .collect(Collectors.toMap(HardModeBadge::getBadgeId, badge -> badge, (left, right) -> right));

        List<Map<String, Object>> result = new ArrayList<>();
        for (BadgeDefinition definition : BADGE_DEFINITIONS.values()) {
            HardModeBadge tracker = trackerByBadgeId.get(definition.id);

            int progressCurrent = tracker != null ? tracker.getProgressCurrent() : 0;
            int progressTotal = tracker != null && tracker.getProgressTotal() > 0 ? tracker.getProgressTotal()
                    : definition.threshold;
            boolean isUnlocked = tracker != null && tracker.isUnlocked();
            boolean isEquipped = tracker != null && tracker.isEquipped();
            LocalDateTime unlockedAt = tracker != null ? tracker.getUnlockedAt() : null;

            if (user != null) {
                if ("founding-dev".equals(definition.id) && user.isDev()) {
                    progressCurrent = progressTotal;
                    isUnlocked = true;
                }

                if ("campus-catalyst".equals(definition.id)
                        && user.getRole() != null
                        && "COLLEGE_HEAD".equalsIgnoreCase(user.getRole())) {
                    progressCurrent = progressTotal;
                    isUnlocked = true;
                }
            }

            Map<String, Object> badgeInfo = new HashMap<>();
            badgeInfo.put("badgeId", definition.id);
            badgeInfo.put("badgeName", definition.name);
            badgeInfo.put("tier", definition.tier);
            badgeInfo.put("visualStyle", definition.visualStyle);
            badgeInfo.put("description", definition.description);
            badgeInfo.put("requirement", getRequirement(definition));
            badgeInfo.put("unlockedBy", getUnlockTip(definition));
            badgeInfo.put("progress", Map.of("current", progressCurrent, "total", progressTotal));
            badgeInfo.put("isUnlocked", isUnlocked);
            badgeInfo.put("isEquipped", isEquipped);
            badgeInfo.put("unlockedAt", unlockedAt);

            if (isUnlocked && !isEquipped && user != null && user.getHardModeBadgesLocked() != null
                    && user.getHardModeBadgesLocked().contains(definition.id)) {
                badgeInfo.put("status", "pending-unlock");
                badgeInfo.put("remainingTime", getTimeUntilMidnight());
            } else if (!isUnlocked) {
                badgeInfo.put("status", "locked");
            } else if (isEquipped) {
                badgeInfo.put("status", "equipped");
            }

            result.add(badgeInfo);
        }

        System.out.println("[HardModeBadgeService] 🎯 Total badges returned: " + result.size());
        return result;
    }

    /**
     * Broadcast badge unlock to user via WebSocket.
     */
    private void broadcastBadgeUnlock(String userId, HardModeBadge badge) {
        broadcastBadgeAddedEvent(
                userId,
                badge,
                "🎉 " + badge.getBadgeName() + " badge unlocked and equipped!");
    }

    private void broadcastBadgeAddedEvent(String userId, HardModeBadge badge, String message) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId, "/queue/badge-unlock",
                    Map.of(
                            "badgeId", badge.getBadgeId(),
                            "badgeName", badge.getBadgeName(),
                            "tier", badge.getTier(),
                            "visualStyle", badge.getVisualStyle(),
                            "message", message,
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

    private String getRequirement(BadgeDefinition definition) {
        if (definition == null) {
            return "Meet criteria";
        }
        return BADGE_REQUIREMENTS.getOrDefault(definition.id, definition.description);
    }

    private String getUnlockTip(BadgeDefinition definition) {
        if (definition == null) {
            return null;
        }
        return BADGE_UNLOCK_TIPS.getOrDefault(definition.id, definition.description);
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
