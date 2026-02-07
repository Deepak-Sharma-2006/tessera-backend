package com.studencollabfin.server.service;

import com.studencollabfin.server.dto.ActivityDTO;
import com.studencollabfin.server.model.User;
import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.repository.UserRepository;
import com.studencollabfin.server.repository.CollabPodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * ActivityService: Manages activity logs and broadcasts via WebSocket
 * 
 * Handles:
 * - Activity feed generation for Recent Activity section
 * - Broadcasting updates to dashboard via /topic/campus.activity.{domain}
 * - Filtering activities by type (BUDDY_BEACON, POD_CREATED, POD_JOINED)
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final UserRepository userRepository;
    private final CollabPodRepository collabPodRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Get recent activities for a user's institution domain
     * Limits to latest 5 activities
     * 
     * @param institutionDomain The user's email domain (e.g., "sinhgad.edu")
     * @return List of recent activities
     */
    public List<ActivityDTO> getRecentActivities(String institutionDomain) {
        try {
            List<ActivityDTO> activities = new ArrayList<>();

            System.out.println("📋 [ActivityService] Fetching activities for domain: " + institutionDomain);

            // Fetch all pods in the user's institution
            List<CollabPod> pods = collabPodRepository.findByCollege(extractCollegeFromDomain(institutionDomain));

            // Generate activities from pods
            for (CollabPod pod : pods) {
                // POD_CREATED activity
                if (pod.getCreatedAt() != null) {
                    User creator = userRepository.findById(pod.getOwnerId()).orElse(null);
                    if (creator != null) {
                        ActivityDTO activity = new ActivityDTO();
                        activity.setId(pod.getId() + "-created");
                        activity.setType("POD_CREATED");
                        activity.setTitle("New collaboration pod: " + pod.getName());
                        activity.setIcon("🚀");
                        activity.setParticipantCount(pod.getMemberIds() != null ? pod.getMemberIds().size() : 1);
                        activity.setTimestamp(convertLocalDateTimeToDate(pod.getCreatedAt()));
                        activity.setInitiatorName(creator.getFullName());
                        activity.setPodId(pod.getId());
                        activities.add(activity);
                    }
                }

                // POD_JOINED activity (latest member join)
                if (pod.getMemberIds() != null && pod.getMemberIds().size() > 0) {
                    // For demo, show member count update
                    // In production, track join times separately
                    ActivityDTO activity = new ActivityDTO();
                    activity.setId(pod.getId() + "-members");
                    activity.setType("POD_JOINED");
                    activity.setTitle(pod.getMemberIds().size() + " students joined " + pod.getName());
                    activity.setIcon("👥");
                    activity.setParticipantCount(pod.getMemberIds().size());

                    // Use lastActive if available, otherwise createdAt
                    LocalDateTime timestamp = (pod.getLastActive() != null) ? pod.getLastActive() : pod.getCreatedAt();
                    activity.setTimestamp(convertLocalDateTimeToDate(timestamp));

                    activity.setInitiatorName("Team members");
                    activity.setPodId(pod.getId());
                    activities.add(activity);
                }
            }

            // Sort by timestamp (newest first)
            activities.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));

            // Return only latest 5
            List<ActivityDTO> recentActivities = activities.size() > 5
                    ? activities.subList(0, 5)
                    : activities;

            System.out.println("✅ [ActivityService] Generated " + recentActivities.size() + " recent activities");
            return recentActivities;

        } catch (Exception e) {
            System.err.println("❌ [ActivityService] Error fetching activities: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Broadcast an activity update to all users in a domain
     * Used by other services to notify about new activities
     * 
     * @param institutionDomain The user's email domain
     * @param activity          The activity to broadcast
     */
    public void broadcastActivity(String institutionDomain, ActivityDTO activity) {
        try {
            String topic = "/topic/campus.activity." + institutionDomain;
            System.out.println("📡 [ActivityService] Broadcasting to " + topic);
            messagingTemplate.convertAndSend(topic, activity);
            System.out.println("✅ [ActivityService] Activity broadcast complete");
        } catch (Exception e) {
            System.err.println("❌ [ActivityService] Broadcast failed: " + e.getMessage());
        }
    }

    /**
     * Notify that a new pod was created
     * Called from CollabPodController after pod creation
     */
    public void notifyPodCreated(CollabPod pod, User creator) {
        ActivityDTO activity = new ActivityDTO();
        activity.setType("POD_CREATED");
        activity.setTitle("New pod: " + pod.getName());
        activity.setIcon("🚀");
        activity.setParticipantCount(1);
        activity.setTimestamp(new Date());
        activity.setInitiatorName(creator.getFullName());
        activity.setPodId(pod.getId());

        String domain = extractDomainFromEmail(creator.getEmail());
        broadcastActivity(domain, activity);
    }

    /**
     * Notify that a user joined a pod
     * Called from CollabPodController after user joins
     */
    public void notifyPodJoined(CollabPod pod, User joiner) {
        ActivityDTO activity = new ActivityDTO();
        activity.setType("POD_JOINED");
        activity.setTitle(joiner.getFullName() + " joined " + pod.getName());
        activity.setIcon("👥");
        activity.setParticipantCount(pod.getMemberIds() != null ? pod.getMemberIds().size() : 0);
        activity.setTimestamp(new Date());
        activity.setInitiatorName(joiner.getFullName());
        activity.setPodId(pod.getId());

        String domain = extractDomainFromEmail(joiner.getEmail());
        broadcastActivity(domain, activity);
    }

    /**
     * Extract college from email domain
     * Example: "sinhgad.edu" → "sinhgad"
     */
    private String extractCollegeFromDomain(String domain) {
        if (domain == null || domain.isEmpty())
            return "";
        return domain.split("\\.")[0];
    }

    /**
     * Extract domain from email
     * Example: "sara@sinhgad.edu" → "sinhgad.edu"
     */
    private String extractDomainFromEmail(String email) {
        if (email == null || !email.contains("@"))
            return "";
        return email.substring(email.indexOf("@") + 1).toLowerCase().trim();
    }

    /**
     * Convert LocalDateTime to java.util.Date
     */
    private Date convertLocalDateTimeToDate(LocalDateTime localDateTime) {
        if (localDateTime == null)
            return new Date();
        return java.sql.Timestamp.valueOf(localDateTime);
    }
}
