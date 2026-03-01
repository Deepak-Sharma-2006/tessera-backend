package com.studencollabfin.server.gamification.tracker;

import com.studencollabfin.server.gamification.event.NotificationReadEvent;
import com.studencollabfin.server.gamification.event.PostCreatedEvent;
import com.studencollabfin.server.gamification.event.ReplyCreatedEvent;
import com.studencollabfin.server.model.HardModeBadge;
import com.studencollabfin.server.model.Post;
import com.studencollabfin.server.model.PostType;
import com.studencollabfin.server.model.SocialPost;
import com.studencollabfin.server.repository.PostRepository;
import com.studencollabfin.server.service.HardModeBadgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class EngagementTracker {

    private static final ZoneId ZONE_IST = ZoneId.of("Asia/Kolkata");

    private static final String BADGE_EVENT_VANGUARD = "event-vanguard";
    private static final String BADGE_ACTIVE_TALKER_ELITE = "active-talker-elite";
    private static final String BADGE_DISCUSSION_ARCHITECT = "discussion-architect";
    private static final String BADGE_ULTRA_RESPONDER = "ultra-responder";
    private static final String BADGE_MIDNIGHT_LEGEND = "midnight-legend";
    private static final String BADGE_DOUBT_DESTROYER = "doubt-destroyer";
    private static final String BADGE_RESOURCE_TITAN = "resource-titan";
    private static final String BADGE_FIRST_RESPONDER = "first-responder";
    private static final String BADGE_VOICE_OF_HUB = "voice-of-hub-lvl3";
    private static final String BADGE_CAMPUS_HELPER = "campus-helper";
    private static final String ACTIVE_TALKER_TIMESTAMPS_KEY = "activeTalkerReplyTimestampsIst";
    private static final String MIDNIGHT_LEGEND_DATES_KEY = "midnightLegendIstDates";
    private static final String MIDNIGHT_LEGEND_LAST_NIGHT_KEY = "midnightLegendLastNightIstDate";
    private static final String MIDNIGHT_LEGEND_STREAK_KEY = "midnightLegendConsecutiveStreak";
    private static final String CAMPUS_HELPER_DISTINCT_POSTS_KEY = "campusHelperDistinctPosts";
    private static final String RESOURCE_TITAN_DISTINCT_POSTS_KEY = "resourceTitanDistinctPosts";
    private static final String EVENT_VANGUARD_EVENT_IDS_KEY = "eventVanguardEventIds";
    private static final String DISCUSSION_ARCHITECT_AWARDED_POSTS_KEY = "discussionArchitectAwardedPostIds";
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)(https?://\\S+|www\\.\\S+)");

    private final MongoTemplate mongoTemplate;
    private final HardModeBadgeService hardModeBadgeService;
    private final PostRepository postRepository;

    @Async
    @EventListener
    public void onNotificationRead(NotificationReadEvent event) {
        if (event == null) {
            log.error("Aborting event-vanguard tracker: userId is null");
            return;
        }

        if (event.userId() == null || event.userId().isBlank()) {
            log.error("Aborting event-vanguard tracker: userId is null");
            return;
        }

        if (!"EVENT".equalsIgnoreCase(event.notificationType())) {
            return;
        }

        if (event.timeToReadInMinutes() > 60) {
            return;
        }

        upsertDistinctEventReadProgressAndAward(event.userId(), event.eventId());
    }

    @Async
    @EventListener
    public void onPostCreated(PostCreatedEvent event) {
        if (event == null) {
            log.error("Aborting resource-titan tracker: userId is null");
            return;
        }

        if (event.userId() == null || event.userId().isBlank()) {
            log.error("Aborting resource-titan tracker: userId is null");
            return;
        }

        if (event.hasResources()) {
            upsertDistinctPostProgressAndAward(
                    event.userId(),
                    BADGE_RESOURCE_TITAN,
                    RESOURCE_TITAN_DISTINCT_POSTS_KEY,
                    event.postId());
        }
    }

    @Async
    @EventListener
    public void onReplyCreated(ReplyCreatedEvent event) {
        log.info("BREADCRUMB 3: Tracker received ReplyCreatedEvent for PostID: {}",
                event != null ? event.postId() : null);

        if (event == null) {
            log.error("Aborting reply-driven engagement trackers: userId is null");
            return;
        }

        if (event.userId() == null || event.userId().isBlank()) {
            log.error("Aborting reply-driven engagement trackers: userId is null");
            return;
        }

        LocalDateTime replyServerTime = event.replyCreatedAt() != null ? event.replyCreatedAt() : LocalDateTime.now();
        LocalDateTime replyIst = toIst(replyServerTime);

        upsertRollingWindowProgressAndAward(
                event.userId(),
                BADGE_ACTIVE_TALKER_ELITE,
                ACTIVE_TALKER_TIMESTAMPS_KEY,
                replyIst,
                Duration.ofDays(7));

        boolean isGlobalHubReply = isGlobalHubScope(event.parentPostScope());
        if (isGlobalHubReply) {
            upsertProgressAndMaybeAward(event.userId(), BADGE_VOICE_OF_HUB, 1);
        }

        if (isGlobalHubReply) {
            int istHour = replyIst.getHour();
            if (istHour >= 2 && istHour < 4) {
                upsertMidnightLegendConsecutiveStreak(event.userId(), replyIst.toLocalDate());
            }

            if (event.parentPostAuthorId() != null && !event.parentPostAuthorId().isBlank()
                    && event.parentPostTotalReplyCount() >= 50) {
                awardDiscussionArchitectAtThreshold(event.parentPostAuthorId(), event.postId());
            }
        }

        if (event.replyLatencySeconds() <= 30) {
            upsertProgressAndMaybeAward(event.userId(), BADGE_ULTRA_RESPONDER, 1);
        } else {
            resetBadgeProgress(event.userId(), BADGE_ULTRA_RESPONDER);
        }

        log.info("Evaluating Reply for First-Responder. PostID: {}, isFirst: {}, Latency: {}",
                event.postId(), event.isFirstReplyToPost(), event.replyLatencySeconds());

        if (event.isFirstReplyToPost() && event.replyLatencySeconds() <= 1800) {
            upsertProgressAndMaybeAward(event.userId(), BADGE_FIRST_RESPONDER, 1);
        }

        Post parentPost = getPost(event.postId());
        if (event.isFirstReplyToPost() && isAskHelpCategoryPost(parentPost)) {
            upsertProgressAndMaybeAward(event.userId(), BADGE_DOUBT_DESTROYER, 1);
        }

        if (isAskHelpPostFromOtherAuthor(parentPost, event.userId())) {
            upsertDistinctPostProgressAndAward(
                    event.userId(),
                    BADGE_CAMPUS_HELPER,
                    CAMPUS_HELPER_DISTINCT_POSTS_KEY,
                    event.postId());
        }

        if (isReplyOnOtherUsersPostWithLink(parentPost, event.userId(), event.replyContent())) {
            upsertDistinctPostProgressAndAward(
                    event.userId(),
                    BADGE_RESOURCE_TITAN,
                    RESOURCE_TITAN_DISTINCT_POSTS_KEY,
                    event.postId());
        }
    }

    private void upsertProgressAndMaybeAward(String userId, String badgeId, int incrementBy) {
        try {
            HardModeBadgeService.BadgeMetadata metadata = hardModeBadgeService.getBadgeMetadata(badgeId);
            HardModeBadge tracker = ensureTrackerExists(userId, badgeId);
            if (tracker == null) {
                log.warn("[EngagementTracker] Tracker not found after ensure, user={}, badge={}", userId, badgeId);
                return;
            }

            if (tracker.getProgressTotal() <= 0) {
                tracker.setProgressTotal(metadata.threshold());
            }

            int currentProgress = Math.max(tracker.getProgressCurrent(), 0);
            tracker.setProgressCurrent(currentProgress + Math.max(incrementBy, 0));
            tracker.setLastCheckedAt(LocalDateTime.now());
            mongoTemplate.save(tracker);

            if (!tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
                hardModeBadgeService.awardBadge(userId, badgeId);
                log.info("[EngagementTracker] Badge threshold met for user={}, badge={}", userId, badgeId);
            }
        } catch (Exception ex) {
            log.error("[EngagementTracker] Progress upsert failed for user={}, badge={}. Possible schema mismatch.",
                    userId, badgeId, ex);
        }
    }

    private void upsertDistinctEventReadProgressAndAward(String userId, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }

        HardModeBadge tracker = ensureTrackerExists(userId, BADGE_EVENT_VANGUARD);
        if (tracker == null) {
            return;
        }

        Map<String, Object> progressData = tracker.getProgressData();
        if (progressData == null) {
            progressData = new HashMap<>();
        }

        List<String> distinctEventIds = getStringList(progressData.get(EVENT_VANGUARD_EVENT_IDS_KEY));
        if (!distinctEventIds.contains(eventId)) {
            distinctEventIds.add(eventId);
        }

        progressData.put(EVENT_VANGUARD_EVENT_IDS_KEY, distinctEventIds);
        tracker.setProgressData(progressData);
        tracker.setProgressCurrent(distinctEventIds.size());
        tracker.setLastCheckedAt(LocalDateTime.now());
        mongoTemplate.save(tracker);

        if (!tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
            hardModeBadgeService.awardBadge(userId, BADGE_EVENT_VANGUARD);
            log.info("[EngagementTracker] Badge threshold met for user={}, badge={}", userId, BADGE_EVENT_VANGUARD);
        }
    }

    private void resetBadgeProgress(String userId, String badgeId) {
        Query query = Query.query(Criteria.where("userId").is(userId).and("badgeId").is(badgeId));
        HardModeBadge tracker = mongoTemplate.findOne(query, HardModeBadge.class);
        if (tracker == null) {
            return;
        }

        tracker.setProgressCurrent(0);
        tracker.setUnlocked(false);
        tracker.setLastCheckedAt(LocalDateTime.now());
        mongoTemplate.save(tracker);
    }

    private void upsertRollingWindowProgressAndAward(
            String userId,
            String badgeId,
            String progressDataKey,
            LocalDateTime eventIstTime,
            Duration windowDuration) {
        HardModeBadge tracker = ensureTrackerExists(userId, badgeId);
        if (tracker == null) {
            return;
        }

        Map<String, Object> progressData = tracker.getProgressData();
        if (progressData == null) {
            progressData = new HashMap<>();
        }

        List<String> timestampStrings = getStringList(progressData.get(progressDataKey));
        LocalDateTime cutoff = eventIstTime.minus(windowDuration);

        timestampStrings.removeIf(rawTimestamp -> {
            try {
                LocalDateTime parsed = LocalDateTime.parse(rawTimestamp);
                return parsed.isBefore(cutoff);
            } catch (Exception ex) {
                return true;
            }
        });

        timestampStrings.add(eventIstTime.toString());

        progressData.put(progressDataKey, timestampStrings);
        tracker.setProgressData(progressData);
        tracker.setProgressCurrent(timestampStrings.size());
        tracker.setLastCheckedAt(LocalDateTime.now());
        mongoTemplate.save(tracker);

        if (!tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
            hardModeBadgeService.awardBadge(userId, badgeId);
            log.info("[EngagementTracker] Badge threshold met for user={}, badge={}", userId, badgeId);
        }
    }

    private void upsertDistinctDateProgressAndAward(
            String userId,
            String badgeId,
            String progressDataKey,
            LocalDate eventDateIst) {
        HardModeBadge tracker = ensureTrackerExists(userId, badgeId);
        if (tracker == null) {
            return;
        }

        Map<String, Object> progressData = tracker.getProgressData();
        if (progressData == null) {
            progressData = new HashMap<>();
        }

        List<String> dateStrings = getStringList(progressData.get(progressDataKey));
        String eventDate = eventDateIst.toString();
        if (!dateStrings.contains(eventDate)) {
            dateStrings.add(eventDate);
        }

        progressData.put(progressDataKey, dateStrings);
        tracker.setProgressData(progressData);
        tracker.setProgressCurrent(dateStrings.size());
        tracker.setLastCheckedAt(LocalDateTime.now());
        mongoTemplate.save(tracker);

        if (!tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
            hardModeBadgeService.awardBadge(userId, badgeId);
            log.info("[EngagementTracker] Badge threshold met for user={}, badge={}", userId, badgeId);
        }
    }

    private void upsertMidnightLegendConsecutiveStreak(String userId, LocalDate istDate) {
        HardModeBadge tracker = ensureTrackerExists(userId, BADGE_MIDNIGHT_LEGEND);
        if (tracker == null) {
            return;
        }

        Map<String, Object> progressData = tracker.getProgressData();
        if (progressData == null) {
            progressData = new HashMap<>();
        }

        LocalDate lastNight = null;
        Object rawLastNight = progressData.get(MIDNIGHT_LEGEND_LAST_NIGHT_KEY);
        if (rawLastNight instanceof String lastNightString) {
            try {
                lastNight = LocalDate.parse(lastNightString);
            } catch (Exception ignored) {
                lastNight = null;
            }
        }

        int streak = 0;
        Object rawStreak = progressData.get(MIDNIGHT_LEGEND_STREAK_KEY);
        if (rawStreak instanceof Number number) {
            streak = number.intValue();
        } else if (rawStreak instanceof String streakString) {
            try {
                streak = Integer.parseInt(streakString);
            } catch (Exception ignored) {
                streak = 0;
            }
        }

        if (lastNight == null) {
            streak = 1;
        } else if (istDate.isEqual(lastNight)) {
            streak = Math.max(1, streak);
        } else if (istDate.isEqual(lastNight.plusDays(1))) {
            streak += 1;
        } else {
            streak = 1;
        }

        progressData.put(MIDNIGHT_LEGEND_LAST_NIGHT_KEY, istDate.toString());
        progressData.put(MIDNIGHT_LEGEND_STREAK_KEY, streak);
        tracker.setProgressData(progressData);
        tracker.setProgressCurrent(streak);
        tracker.setLastCheckedAt(LocalDateTime.now());
        mongoTemplate.save(tracker);

        if (!tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
            hardModeBadgeService.awardBadge(userId, BADGE_MIDNIGHT_LEGEND);
            log.info("[EngagementTracker] Badge threshold met for user={}, badge={}", userId, BADGE_MIDNIGHT_LEGEND);
        }
    }

    private void awardDiscussionArchitectAtThreshold(String postAuthorId, String postId) {
        if (postId == null || postId.isBlank()) {
            return;
        }

        HardModeBadge tracker = ensureTrackerExists(postAuthorId, BADGE_DISCUSSION_ARCHITECT);
        if (tracker == null) {
            return;
        }

        Map<String, Object> progressData = tracker.getProgressData();
        if (progressData == null) {
            progressData = new HashMap<>();
        }

        List<String> awardedPosts = getStringList(progressData.get(DISCUSSION_ARCHITECT_AWARDED_POSTS_KEY));
        if (awardedPosts.contains(postId)) {
            return;
        }

        awardedPosts.add(postId);
        progressData.put(DISCUSSION_ARCHITECT_AWARDED_POSTS_KEY, awardedPosts);
        tracker.setProgressData(progressData);
        tracker.setProgressCurrent(tracker.getProgressTotal());
        tracker.setLastCheckedAt(LocalDateTime.now());
        mongoTemplate.save(tracker);

        if (!tracker.isUnlocked()) {
            hardModeBadgeService.awardBadge(postAuthorId, BADGE_DISCUSSION_ARCHITECT);
            log.info("[EngagementTracker] Discussion Architect threshold met for author={}, postId={}",
                    postAuthorId, postId);
        }
    }

    private void upsertDistinctPostProgressAndAward(
            String userId,
            String badgeId,
            String progressDataKey,
            String postId) {
        if (postId == null || postId.isBlank()) {
            return;
        }

        HardModeBadge tracker = ensureTrackerExists(userId, badgeId);
        if (tracker == null) {
            return;
        }

        Map<String, Object> progressData = tracker.getProgressData();
        if (progressData == null) {
            progressData = new HashMap<>();
        }

        List<String> distinctPosts = getStringList(progressData.get(progressDataKey));
        if (!distinctPosts.contains(postId)) {
            distinctPosts.add(postId);
        }

        progressData.put(progressDataKey, distinctPosts);
        tracker.setProgressData(progressData);
        tracker.setProgressCurrent(distinctPosts.size());
        tracker.setLastCheckedAt(LocalDateTime.now());
        mongoTemplate.save(tracker);

        if (!tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
            hardModeBadgeService.awardBadge(userId, badgeId);
            log.info("[EngagementTracker] Badge threshold met for user={}, badge={}", userId, badgeId);
        }
    }

    private HardModeBadge ensureTrackerExists(String userId, String badgeId) {
        HardModeBadgeService.BadgeMetadata metadata = hardModeBadgeService.getBadgeMetadata(badgeId);
        Query query = Query.query(Criteria.where("userId").is(userId).and("badgeId").is(badgeId));

        Update ensureInsert = new Update()
                .setOnInsert("userId", userId)
                .setOnInsert("badgeId", metadata.badgeId())
                .setOnInsert("badgeName", metadata.badgeName())
                .setOnInsert("tier", metadata.tier())
                .setOnInsert("visualStyle", metadata.visualStyle())
                .setOnInsert("progressCurrent", 0)
                .setOnInsert("progressTotal", metadata.threshold())
                .setOnInsert("isUnlocked", false)
                .setOnInsert("isEquipped", false)
                .setOnInsert("progressData", new HashMap<String, Object>())
                .set("lastCheckedAt", LocalDateTime.now());

        mongoTemplate.upsert(query, ensureInsert, HardModeBadge.class);
        return mongoTemplate.findOne(query, HardModeBadge.class);
    }

    private boolean isHelpNeededPost(String postId) {
        Post post = getPost(postId);
        if (!(post instanceof SocialPost socialPost)) {
            return false;
        }

        if (socialPost.getType() == PostType.ASK_HELP) {
            return true;
        }

        String category = socialPost.getCategory();
        if (category == null) {
            return false;
        }

        String normalizedCategory = category.trim().toLowerCase();
        return normalizedCategory.contains("help") || normalizedCategory.contains("helpneeded");
    }

    private boolean isAskHelpCategoryPost(Post post) {
        if (!(post instanceof SocialPost socialPost)) {
            return false;
        }

        return socialPost.getType() == PostType.ASK_HELP;
    }

    private boolean isGlobalHubScope(String parentPostScope) {
        return parentPostScope != null && "GLOBAL_HUB".equalsIgnoreCase(parentPostScope.trim());
    }

    private Post getPost(String postId) {
        if (postId == null || postId.isBlank()) {
            return null;
        }

        return postRepository.findById(postId).orElse(null);
    }

    private boolean isAskHelpPostFromOtherAuthor(Post post, String replyAuthorId) {
        if (!(post instanceof SocialPost socialPost)) {
            return false;
        }

        if (replyAuthorId == null || replyAuthorId.isBlank()) {
            return false;
        }

        if (socialPost.getAuthorId() != null && socialPost.getAuthorId().equals(replyAuthorId)) {
            return false;
        }

        return socialPost.getType() == PostType.ASK_HELP;
    }

    private boolean isReplyOnOtherUsersPostWithLink(Post post, String replyAuthorId, String replyContent) {
        if (!(post instanceof SocialPost socialPost)) {
            return false;
        }

        if (replyAuthorId == null || replyAuthorId.isBlank()) {
            return false;
        }

        if (socialPost.getAuthorId() != null && socialPost.getAuthorId().equals(replyAuthorId)) {
            return false;
        }

        if (replyContent == null || replyContent.isBlank()) {
            return false;
        }

        return URL_PATTERN.matcher(replyContent).find();
    }

    private LocalDateTime toIst(LocalDateTime serverTimestamp) {
        return serverTimestamp.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZONE_IST)
                .toLocalDateTime();
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Object rawValue) {
        if (rawValue instanceof List<?>) {
            return (List<String>) rawValue;
        }
        return new java.util.ArrayList<>();
    }
}
