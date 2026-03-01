package com.studencollabfin.server.gamification.tracker;

import com.studencollabfin.server.gamification.event.CollabRoomCreatedEvent;
import com.studencollabfin.server.gamification.event.CollabRoomParticipatedEvent;
import com.studencollabfin.server.gamification.event.DirectMessageSentEvent;
import com.studencollabfin.server.gamification.event.PodJoinedEvent;
import com.studencollabfin.server.gamification.event.ReplyCreatedEvent;
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
public class CollabTracker {

    private static final String BADGE_BRIDGE_MASTER = "bridge-master";
    private static final String BADGE_LEAD_ARCHITECT = "lead-architect";
    private static final String BADGE_TEAM_ENGINE = "team-engine";
    private static final String BADGE_COLLAB_MASTER = "collab-master-lvl3";
    private static final String BADGE_CROSS_DOMAIN = "cross-domain-pro";
    private static final String BADGE_POD_PIONEER = "pod-pioneer";
    private static final String BADGE_BRIDGE_BUILDER = "bridge-builder";
    private static final String TEAM_ENGINE_REPLIES_PER_ROOM_KEY = "teamEngineRepliesPerRoom";
    private static final String TEAM_ENGINE_QUALIFIED_ROOMS_KEY = "teamEngineQualifiedRooms";
    private static final String COLLAB_MASTER_DISTINCT_ROOMS_KEY = "collabMasterDistinctRooms";
    private static final String LEAD_ARCHITECT_QUALIFIED_ROOMS_KEY = "leadArchitectQualifiedRooms";
    private static final String CROSS_DOMAIN_DISTINCT_BRANCHES_KEY = "crossDomainDistinctBranches";

    private final MongoTemplate mongoTemplate;
    private final HardModeBadgeService hardModeBadgeService;

    @Async
    @EventListener
    public void onDirectMessageSent(DirectMessageSentEvent event) {
        if (event == null || event.senderId() == null || event.senderId().isBlank()) {
            return;
        }

        String senderDomain = event.senderDomain() != null ? event.senderDomain().trim().toLowerCase() : "";
        String receiverDomain = event.receiverDomain() != null ? event.receiverDomain().trim().toLowerCase() : "";

        if (senderDomain.isBlank() || receiverDomain.isBlank() || senderDomain.equals(receiverDomain)) {
            return;
        }

        upsertProgressAndMaybeAward(event.senderId(), BADGE_BRIDGE_BUILDER, 1);
        upsertBridgeMasterDistinctWindow(event.senderId(), receiverDomain);
    }

    @Async
    @EventListener
    public void onPodJoined(PodJoinedEvent event) {
        if (event == null || event.userId() == null || event.userId().isBlank()) {
            return;
        }

        if (!event.isFirstPod()) {
            return;
        }

        upsertProgressAndMaybeAward(event.userId(), BADGE_POD_PIONEER, 1);
    }

    @Async
    @EventListener
    public void onCollabRoomCreated(CollabRoomCreatedEvent event) {
        if (event == null || event.userId() == null || event.userId().isBlank()) {
            return;
        }

        if (event.distinctCollegeCountAtFill() >= 4) {
            upsertDistinctRoomProgressAndAward(
                    event.userId(),
                    BADGE_LEAD_ARCHITECT,
                    LEAD_ARCHITECT_QUALIFIED_ROOMS_KEY,
                    event.roomId());
        }
    }

    @Async
    @EventListener
    public void onCollabRoomParticipated(CollabRoomParticipatedEvent event) {
        if (event == null || event.userId() == null || event.userId().isBlank()) {
            return;
        }

        upsertDistinctBranchProgressAndAward(event.userId(), event.academicBranch());
    }

    @Async
    @EventListener
    public void onReplyCreated(ReplyCreatedEvent event) {
        if (event == null || event.userId() == null || event.userId().isBlank()) {
            return;
        }

        if (!"COLLAB_ROOM".equalsIgnoreCase(event.parentPostType())) {
            return;
        }

        upsertTeamEngineProgressAndAward(event.userId(), event.postId());
        upsertDistinctRoomProgressAndAward(event.userId(), BADGE_COLLAB_MASTER, COLLAB_MASTER_DISTINCT_ROOMS_KEY,
                event.postId());
    }

    private void upsertTeamEngineProgressAndAward(String userId, String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return;
        }

        HardModeBadge tracker = ensureTrackerExists(userId, BADGE_TEAM_ENGINE);
        if (tracker == null) {
            return;
        }

        Map<String, Object> progressData = tracker.getProgressData();
        if (progressData == null) {
            progressData = new HashMap<>();
        }

        Map<String, Integer> repliesPerRoom = getStringIntMap(progressData.get(TEAM_ENGINE_REPLIES_PER_ROOM_KEY));
        List<String> qualifiedRooms = getStringList(progressData.get(TEAM_ENGINE_QUALIFIED_ROOMS_KEY));

        int updatedCount = repliesPerRoom.getOrDefault(roomId, 0) + 1;
        repliesPerRoom.put(roomId, updatedCount);

        if (updatedCount >= 20 && !qualifiedRooms.contains(roomId)) {
            qualifiedRooms.add(roomId);
        }

        progressData.put(TEAM_ENGINE_REPLIES_PER_ROOM_KEY, repliesPerRoom);
        progressData.put(TEAM_ENGINE_QUALIFIED_ROOMS_KEY, qualifiedRooms);

        tracker.setProgressData(progressData);
        tracker.setProgressCurrent(qualifiedRooms.size());
        tracker.setLastCheckedAt(LocalDateTime.now());
        mongoTemplate.save(tracker);

        if (!tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
            hardModeBadgeService.awardBadge(userId, BADGE_TEAM_ENGINE);
            log.info("[CollabTracker] Badge threshold met for user={}, badge={}", userId, BADGE_TEAM_ENGINE);
        }
    }

    private void upsertBridgeMasterDistinctWindow(String userId, String receiverDomain) {
        HardModeBadge tracker = ensureTrackerExists(userId, BADGE_BRIDGE_MASTER);
        if (tracker == null) {
            return;
        }

        Map<String, Object> progressData = tracker.getProgressData();
        if (progressData == null) {
            progressData = new HashMap<>();
        }

        Map<String, String> domainToIsoTimestamp = parseDomainTimestampMap(
                progressData.get("bridgeMasterDomainTimestamps"));
        LocalDateTime now = LocalDateTime.now();

        domainToIsoTimestamp.put(receiverDomain, now.toString());

        domainToIsoTimestamp.entrySet().removeIf(entry -> {
            try {
                LocalDateTime timestamp = LocalDateTime.parse(entry.getValue());
                long hours = ChronoUnit.HOURS.between(timestamp, now);
                return hours >= 24;
            } catch (Exception ex) {
                return true;
            }
        });

        progressData.put("bridgeMasterDomainTimestamps", domainToIsoTimestamp);
        tracker.setProgressData(progressData);
        tracker.setProgressCurrent(domainToIsoTimestamp.size());
        tracker.setLastCheckedAt(now);

        mongoTemplate.save(tracker);

        if (!tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
            hardModeBadgeService.awardBadge(userId, BADGE_BRIDGE_MASTER);
            log.info("[CollabTracker] Badge threshold met for user={}, badge={}", userId, BADGE_BRIDGE_MASTER);
        }
    }

    private void upsertDistinctBranchProgressAndAward(String userId, String academicBranch) {
        if (academicBranch == null || academicBranch.isBlank()) {
            return;
        }

        HardModeBadge tracker = ensureTrackerExists(userId, BADGE_CROSS_DOMAIN);
        if (tracker == null) {
            return;
        }

        Map<String, Object> progressData = tracker.getProgressData();
        if (progressData == null) {
            progressData = new HashMap<>();
        }

        List<String> distinctBranches = getStringList(progressData.get(CROSS_DOMAIN_DISTINCT_BRANCHES_KEY));
        String normalizedBranch = academicBranch.trim().toLowerCase();
        if (!distinctBranches.contains(normalizedBranch)) {
            distinctBranches.add(normalizedBranch);
        }

        progressData.put(CROSS_DOMAIN_DISTINCT_BRANCHES_KEY, distinctBranches);
        tracker.setProgressData(progressData);
        tracker.setProgressCurrent(distinctBranches.size());
        tracker.setLastCheckedAt(LocalDateTime.now());
        mongoTemplate.save(tracker);

        if (!tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
            hardModeBadgeService.awardBadge(userId, BADGE_CROSS_DOMAIN);
            log.info("[CollabTracker] Badge threshold met for user={}, badge={}", userId, BADGE_CROSS_DOMAIN);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseDomainTimestampMap(Object raw) {
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
    private void upsertDistinctRoomProgressAndAward(String userId, String badgeId, String progressDataKey,
            String roomId) {
        if (roomId == null || roomId.isBlank()) {
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

        List<String> distinctRooms = new ArrayList<>();
        Object rawRooms = progressData.get(progressDataKey);
        if (rawRooms instanceof List<?>) {
            for (Object value : (List<?>) rawRooms) {
                if (value != null) {
                    distinctRooms.add(String.valueOf(value));
                }
            }
        }

        if (!distinctRooms.contains(roomId)) {
            distinctRooms.add(roomId);
            progressData.put(progressDataKey, distinctRooms);
            tracker.setProgressData(progressData);
            tracker.setProgressCurrent(distinctRooms.size());
            tracker.setLastCheckedAt(LocalDateTime.now());
            mongoTemplate.save(tracker);
        }

        if (!tracker.isUnlocked() && tracker.getProgressCurrent() >= tracker.getProgressTotal()) {
            hardModeBadgeService.awardBadge(userId, badgeId);
            log.info("[CollabTracker] Badge threshold met for user={}, badge={}", userId, badgeId);
        }
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
            log.info("[CollabTracker] Badge threshold met for user={}, badge={}", userId, badgeId);
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
}
