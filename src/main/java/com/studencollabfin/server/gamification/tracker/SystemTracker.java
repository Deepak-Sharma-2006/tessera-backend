package com.studencollabfin.server.gamification.tracker;

import com.studencollabfin.server.gamification.event.PollResolvedEvent;
import com.studencollabfin.server.gamification.event.ProfileUpdatedEvent;
import com.studencollabfin.server.gamification.event.UserLoginEvent;
import com.studencollabfin.server.model.HardModeBadge;
import com.studencollabfin.server.model.User;
import com.studencollabfin.server.repository.UserRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemTracker {

    private static final ZoneId ZONE_IST = ZoneId.of("Asia/Kolkata");

    private static final String BADGE_STREAK_SEEKER = "streak-seeker-lvl3";
    private static final String BADGE_ORACLE = "the-oracle-gm";
    private static final String BADGE_PROFILE_PERFECTIONIST = "profile-perfectionist";
    private static final String STREAK_LAST_LOGIN_DATE_IST_KEY = "streakLastLoginIstDate";
    private static final String STREAK_LAST_LOGIN_TIMESTAMP_IST_KEY = "streakLastLoginIstTimestamp";

    private final MongoTemplate mongoTemplate;
    private final HardModeBadgeService hardModeBadgeService;
    private final UserRepository userRepository;

    @Async
    @EventListener
    public void onUserLogin(UserLoginEvent event) {
        if (event == null) {
            log.error("Aborting streak-seeker-lvl3 tracker: userId is null");
            return;
        }

        if (event.userId() == null || event.userId().isBlank()) {
            log.error("Aborting streak-seeker-lvl3 tracker: userId is null");
            return;
        }

        upsertIstStreakProgressFromUserAndMaybeAward(event.userId());
    }

    @Async
    @EventListener
    public void onPollResolved(PollResolvedEvent event) {
        if (event == null) {
            log.error("Aborting the-oracle-gm tracker: userId is null");
            return;
        }

        if (event.userId() == null || event.userId().isBlank()) {
            log.error("Aborting the-oracle-gm tracker: userId is null");
            return;
        }

        if (!event.isMajorityChoice()) {
            return;
        }

        upsertProgressAndMaybeAward(event.userId(), BADGE_ORACLE, 1);
    }

    @Async
    @EventListener
    public void onProfileUpdated(ProfileUpdatedEvent event) {
        if (event == null) {
            log.error("Aborting profile-perfectionist tracker: userId is null");
            return;
        }

        if (event.userId() == null || event.userId().isBlank()) {
            log.error("Aborting profile-perfectionist tracker: userId is null");
            return;
        }

        if (!event.isProfileComplete()) {
            return;
        }

        upsertProgressAndMaybeAward(event.userId(), BADGE_PROFILE_PERFECTIONIST, 1);
    }

    private void upsertProgressAndMaybeAward(String userId, String badgeId, int incrementBy) {
        HardModeBadgeService.BadgeMetadata metadata = hardModeBadgeService.getBadgeMetadata(badgeId);

        Query query = Query.query(Criteria.where("userId").is(userId).and("badgeId").is(badgeId));
        Update update = new Update()
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
                .set("lastCheckedAt", LocalDateTime.now())
                .inc("progressCurrent", incrementBy);

        mongoTemplate.upsert(query, update, HardModeBadge.class);

        HardModeBadge tracker = mongoTemplate.findOne(query, HardModeBadge.class);
        if (tracker == null) {
            return;
        }

        if (!tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
            hardModeBadgeService.awardBadge(userId, badgeId);
            log.info("[SystemTracker] Badge threshold met for user={}, badge={}", userId, badgeId);
        }
    }

    private void upsertIstStreakProgressFromUserAndMaybeAward(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.error("[SystemTracker] Aborting streak sync: user not found for userId={}", userId);
            return;
        }

        int userStreak = Math.max(user.getLoginStreak(), 0);
        String loginIstDateString = LocalDate.now(ZONE_IST).toString();

        HardModeBadge tracker = ensureTrackerExists(userId, BADGE_STREAK_SEEKER);
        if (tracker == null) {
            return;
        }

        Map<String, Object> progressData = tracker.getProgressData();
        if (progressData == null) {
            progressData = new HashMap<>();
        }

        progressData.put(STREAK_LAST_LOGIN_DATE_IST_KEY, loginIstDateString);
        progressData.put(STREAK_LAST_LOGIN_TIMESTAMP_IST_KEY, LocalDateTime.now(ZONE_IST).toString());

        tracker.setProgressData(progressData);
        tracker.setProgressCurrent(userStreak);
        tracker.setLastCheckedAt(LocalDateTime.now());
        mongoTemplate.save(tracker);

        log.info(
                "[SystemTracker] Synced streak-seeker progress from User.loginStreak. userId={}, userStreak={}, istDate={}",
                userId, userStreak, loginIstDateString);

        if (!tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
            hardModeBadgeService.awardBadge(userId, BADGE_STREAK_SEEKER);
            log.info("[SystemTracker] Badge threshold met for user={}, badge={}", userId, BADGE_STREAK_SEEKER);
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

}
