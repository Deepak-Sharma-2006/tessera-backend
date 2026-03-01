package com.studencollabfin.server.gamification.tracker;

import com.studencollabfin.server.gamification.event.DirectMessageSentEvent;
import com.studencollabfin.server.gamification.event.UserReportedEvent;
import com.studencollabfin.server.model.HardModeBadge;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationTracker {

    private static final String BADGE_SILENT_SENTINEL = "silent-sentinel";
    private static final String BADGE_SPAM_ALERT_SANCTION = "spam-alert-sanction";
    private static final String REPORTS_BY_REPORTER_KEY = "reportsByReporter";
    private static final String DM_BAN_UNTIL_KEY = "dmBanUntil";
    private static final String MESSAGE_COUNT_WITH_KEY = "messageCountWith";
    private static final String BLACKLISTED_RECEIVERS_KEY = "blacklistedReceivers";

    private final MongoTemplate mongoTemplate;
    private final HardModeBadgeService hardModeBadgeService;

    @Async
    @EventListener
    public void onUserReported(UserReportedEvent event) {
        if (event == null
                || event.targetUserId() == null || event.targetUserId().isBlank()
                || event.reporterId() == null || event.reporterId().isBlank()) {
            return;
        }

        applySpamAlertDmBan(event.targetUserId(), event.reporterId());
        blacklistSilentSentinelReceiver(event.targetUserId(), event.reporterId());
    }

    @Async
    @EventListener
    public void onDirectMessageSent(DirectMessageSentEvent event) {
        if (event == null
                || event.senderId() == null || event.senderId().isBlank()
                || event.receiverId() == null || event.receiverId().isBlank()) {
            return;
        }

        upsertSilentSentinelPairProgress(event.senderId(), event.receiverId());
    }

    private void applySpamAlertDmBan(String targetUserId, String reporterId) {
        HardModeBadge tracker = ensureTrackerExists(targetUserId, BADGE_SPAM_ALERT_SANCTION);
        if (tracker == null) {
            return;
        }

        Map<String, Object> progressData = tracker.getProgressData();
        if (progressData == null) {
            progressData = new HashMap<>();
        }

        Map<String, Integer> reportsByReporter = getStringIntMap(progressData.get(REPORTS_BY_REPORTER_KEY));
        Map<String, String> dmBanUntil = getStringStringMap(progressData.get(DM_BAN_UNTIL_KEY));

        int reportCountFromReporter = reportsByReporter.getOrDefault(reporterId, 0) + 1;
        reportsByReporter.put(reporterId, reportCountFromReporter);

        LocalDateTime now = LocalDateTime.now();
        if (reportCountFromReporter >= 3) {
            dmBanUntil.put(reporterId, now.plusHours(24).toString());
        }

        dmBanUntil.entrySet().removeIf(entry -> {
            try {
                LocalDateTime expiry = LocalDateTime.parse(entry.getValue());
                return !expiry.isAfter(now);
            } catch (Exception ex) {
                return true;
            }
        });

        boolean hasActiveDmBan = !dmBanUntil.isEmpty();

        progressData.put(REPORTS_BY_REPORTER_KEY, reportsByReporter);
        progressData.put(DM_BAN_UNTIL_KEY, dmBanUntil);
        progressData.put("sanctionActive", hasActiveDmBan);

        tracker.setProgressData(progressData);
        tracker.setProgressCurrent(hasActiveDmBan ? tracker.getProgressTotal() : 0);
        tracker.setLastCheckedAt(now);
        mongoTemplate.save(tracker);

        if (hasActiveDmBan && !tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
            hardModeBadgeService.awardBadge(targetUserId, BADGE_SPAM_ALERT_SANCTION);
        }

        log.info("[ModerationTracker] DM moderation updated for target={}, reporter={}, activeBan={}",
                targetUserId, reporterId, hasActiveDmBan);
    }

    private void blacklistSilentSentinelReceiver(String targetUserId, String reporterId) {
        HardModeBadge tracker = ensureTrackerExists(targetUserId, BADGE_SILENT_SENTINEL);
        if (tracker == null) {
            return;
        }

        Map<String, Object> progressData = tracker.getProgressData();
        if (progressData == null) {
            progressData = new HashMap<>();
        }

        List<String> blacklistedReceivers = getStringList(progressData.get(BLACKLISTED_RECEIVERS_KEY));
        if (!blacklistedReceivers.contains(reporterId)) {
            blacklistedReceivers.add(reporterId);
        }

        progressData.put(BLACKLISTED_RECEIVERS_KEY, blacklistedReceivers);
        tracker.setProgressData(progressData);

        recomputeSilentSentinelState(tracker);
        mongoTemplate.save(tracker);

        if (!tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
            hardModeBadgeService.awardBadge(targetUserId, BADGE_SILENT_SENTINEL);
        }

        log.info("[ModerationTracker] Silent Sentinel receiver blacklisted for target={}, reporter={}",
                targetUserId, reporterId);
    }

    private void upsertSilentSentinelPairProgress(String senderId, String receiverId) {
        HardModeBadge tracker = ensureTrackerExists(senderId, BADGE_SILENT_SENTINEL);
        if (tracker == null) {
            return;
        }

        Map<String, Object> progressData = tracker.getProgressData();
        if (progressData == null) {
            progressData = new HashMap<>();
        }

        Map<String, Integer> messageCountWith = getStringIntMap(progressData.get(MESSAGE_COUNT_WITH_KEY));
        messageCountWith.put(receiverId, messageCountWith.getOrDefault(receiverId, 0) + 1);
        progressData.put(MESSAGE_COUNT_WITH_KEY, messageCountWith);

        tracker.setProgressData(progressData);
        recomputeSilentSentinelState(tracker);
        mongoTemplate.save(tracker);

        if (!tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
            hardModeBadgeService.awardBadge(senderId, BADGE_SILENT_SENTINEL);
        }
    }

    private void recomputeSilentSentinelState(HardModeBadge tracker) {
        Map<String, Object> progressData = tracker.getProgressData();
        if (progressData == null) {
            progressData = new HashMap<>();
            tracker.setProgressData(progressData);
        }

        Map<String, Integer> messageCountWith = getStringIntMap(progressData.get(MESSAGE_COUNT_WITH_KEY));
        List<String> blacklistedReceivers = getStringList(progressData.get(BLACKLISTED_RECEIVERS_KEY));

        int bestEligiblePairCount = 0;
        for (Map.Entry<String, Integer> entry : messageCountWith.entrySet()) {
            String receiverId = entry.getKey();
            int count = entry.getValue() != null ? entry.getValue() : 0;
            if (!blacklistedReceivers.contains(receiverId)) {
                bestEligiblePairCount = Math.max(bestEligiblePairCount, count);
            }
        }

        tracker.setProgressCurrent(bestEligiblePairCount);
        tracker.setLastCheckedAt(LocalDateTime.now());
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

    @SuppressWarnings("unchecked")
    private Map<String, String> getStringStringMap(Object raw) {
        Map<String, String> result = new HashMap<>();
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return result;
        }

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> getStringIntMap(Object raw) {
        Map<String, Integer> result = new HashMap<>();
        if (!(raw instanceof Map<?, ?> rawMap)) {
            return result;
        }

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            try {
                int parsedValue = Integer.parseInt(String.valueOf(entry.getValue()));
                result.put(String.valueOf(entry.getKey()), parsedValue);
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Object raw) {
        List<String> result = new ArrayList<>();
        if (!(raw instanceof List<?> rawList)) {
            return result;
        }

        for (Object value : rawList) {
            if (value != null) {
                result.add(String.valueOf(value));
            }
        }
        return result;
    }

    public boolean isDmBanActiveBetween(String senderId, String receiverId) {
        Query query = Query.query(Criteria.where("userId").is(senderId).and("badgeId").is(BADGE_SPAM_ALERT_SANCTION));
        HardModeBadge tracker = mongoTemplate.findOne(query, HardModeBadge.class);
        if (tracker == null || tracker.getProgressData() == null) {
            return false;
        }

        Map<String, String> dmBanUntil = getStringStringMap(tracker.getProgressData().get(DM_BAN_UNTIL_KEY));
        if (!dmBanUntil.containsKey(receiverId)) {
            return false;
        }

        try {
            LocalDateTime expiry = LocalDateTime.parse(dmBanUntil.get(receiverId));
            return expiry.isAfter(LocalDateTime.now());
        } catch (Exception ex) {
            return false;
        }
    }

    public long getRemainingDmBanMinutes(String senderId, String receiverId) {
        Query query = Query.query(Criteria.where("userId").is(senderId).and("badgeId").is(BADGE_SPAM_ALERT_SANCTION));
        HardModeBadge tracker = mongoTemplate.findOne(query, HardModeBadge.class);
        if (tracker == null || tracker.getProgressData() == null) {
            return 0;
        }

        Map<String, String> dmBanUntil = getStringStringMap(tracker.getProgressData().get(DM_BAN_UNTIL_KEY));
        if (!dmBanUntil.containsKey(receiverId)) {
            return 0;
        }

        try {
            LocalDateTime expiry = LocalDateTime.parse(dmBanUntil.get(receiverId));
            long remaining = ChronoUnit.MINUTES.between(LocalDateTime.now(), expiry);
            return Math.max(0, remaining);
        } catch (Exception ex) {
            return 0;
        }
    }
}
