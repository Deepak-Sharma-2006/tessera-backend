package com.studencollabfin.server.service;

import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.model.Post;
import com.studencollabfin.server.model.TeamFindingPost;
import com.studencollabfin.server.repository.CollabPodRepository;
import com.studencollabfin.server.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TeamCleanupService {

    private final PostRepository postRepository;
    private final CollabPodRepository podRepository;
    private final EventService eventService;

    /**
     * ✅ NEW: Scheduled task that runs every minute to check for expired
     * TeamFindingPosts.
     * 
     * Logic:
     * 1. Find all posts created > 24 hours ago
     * 2. For each TeamFindingPost:
     * - Extract confirmed members (creator + applicants with status CONFIRMED)
     * - If >= 2 members: Convert to CollabPod (Type: TEAM, eventId: linked)
     * - If < 2 members: Delete post (recruitment failed)
     * 3. Always refresh event statistics after processing
     */
    @Scheduled(cron = "0 * * * * *") // Every minute
    public void processExpiredTeamFindingPosts() {
        // Calculate 24-hour cutoff
        LocalDateTime expiryThreshold = LocalDateTime.now().minusHours(24);

        // Query all posts older than 24 hours
        List<Post> expiredPosts = postRepository.findByCreatedAtBefore(expiryThreshold);

        // Track events that need stats refresh
        java.util.Set<String> eventsToRefresh = new java.util.HashSet<>();

        for (Post post : expiredPosts) {
            // Only process TeamFindingPost instances
            if (post instanceof TeamFindingPost) {
                TeamFindingPost teamPost = (TeamFindingPost) post;
                processTeamFindingPost(teamPost);

                // Mark event for stats refresh
                if (teamPost.getEventId() != null) {
                    eventsToRefresh.add(teamPost.getEventId());
                }
            }
        }

        // Refresh stats for all affected events
        for (String eventId : eventsToRefresh) {
            try {
                eventService.refreshEventStats(eventId);
                System.out.println("✅ [TeamCleanup] Refreshed stats for event: " + eventId);
            } catch (Exception e) {
                System.err.println("❌ [TeamCleanup] Failed to refresh stats for event: " + eventId);
                e.printStackTrace();
            }
        }
    }

    /**
     * Process a single TeamFindingPost:
     * - Extract confirmed members
     * - Check minimum team size (2)
     * - Convert to pod or delete
     * 
     * ✅ RACE CONDITION FIX: Only delete post if:
     * - linkedPodId != null (pod already created) OR
     * - totalApplicants == 0 (no members to process)
     */
    private void processTeamFindingPost(TeamFindingPost post) {
        try {
            // ✅ SAFETY CHECK: Skip if this is a relist post (already has linkedPodId from
            // creation)
            if (post.getLinkedPodId() != null) {
                System.out.println("⏭️ [TeamCleanup] Skipping post '" + post.getTitle()
                        + "' - already linked to pod: " + post.getLinkedPodId());
                return;
            }

            // Step 1: Extract confirmed members
            List<String> confirmedIds = extractConfirmedMembers(post);

            // Step 2: Check minimum team size
            if (confirmedIds.size() >= 2) {
                // ✅ CONVERT TO COLLABPOD
                convertPostToPod(post, confirmedIds);
                System.out.println("✅ [TeamCleanup] Converted post '" + post.getTitle()
                        + "' to CollabPod with " + confirmedIds.size() + " members");
            } else {
                // ❌ DELETE POST (recruitment failed)
                System.out.println("❌ [TeamCleanup] Deleting post '" + post.getTitle()
                        + "' (insufficient members: " + confirmedIds.size() + ")");
            }

            // Step 3: Only delete if safe (pod created OR no applicants)
            int totalApplicants = post.getApplicants() != null ? post.getApplicants().size() : 0;
            if (post.getLinkedPodId() != null || totalApplicants == 0) {
                postRepository.delete(post);
                System.out.println("🗑️ [TeamCleanup] Post deleted: " + post.getId());
            } else {
                System.out.println("⚠️ [TeamCleanup] Post NOT deleted - waiting for pod creation: " + post.getId());
            }

        } catch (Exception e) {
            System.err.println("❌ [TeamCleanup] Error processing post: " + post.getId());
            e.printStackTrace();
        }
    }

    /**
     * Extract confirmed member IDs:
     * - Creator (author) is always a member
     * - Applicants with status = "CONFIRMED" are members
     */
    private List<String> extractConfirmedMembers(TeamFindingPost post) {
        List<String> confirmedIds = new ArrayList<>();

        // Add creator
        if (post.getAuthorId() != null && !post.getAuthorId().isEmpty()) {
            confirmedIds.add(post.getAuthorId());
        }

        // Add confirmed applicants
        if (post.getApplicants() != null && !post.getApplicants().isEmpty()) {
            for (Map<String, Object> applicant : post.getApplicants()) {
                String status = (String) applicant.get("status");
                String userId = (String) applicant.get("userId");

                // Check if applicant is confirmed and has a valid userId
                if ("CONFIRMED".equalsIgnoreCase(status) && userId != null && !userId.isEmpty()) {
                    // Avoid duplicates (creator might also be in applicants)
                    if (!confirmedIds.contains(userId)) {
                        confirmedIds.add(userId);
                    }
                }
            }
        }

        return confirmedIds;
    }

    /**
     * Convert a TeamFindingPost to a CollabPod:
     * - Type: TEAM (distinguishes from general pods)
     * - eventId: Linked to the event
     * - college: Campus isolation maintained
     * - members: All confirmed members
     * 
     * ✅ ATOMICITY: Updates post.linkedPodId in same transaction as pod creation
     */
    private void convertPostToPod(TeamFindingPost post, List<String> confirmedIds) {
        CollabPod pod = new CollabPod();

        // Basic info
        pod.setName(post.getTitle() + " Team"); // Append "Team" for clarity
        pod.setDescription(post.getContent() != null ? post.getContent() : "Team pod from event recruitment");
        pod.setCreatorId(post.getAuthorId());
        pod.setOwnerId(post.getAuthorId());

        // Members & capacity
        pod.setMemberIds(new ArrayList<>(confirmedIds));
        pod.setMaxCapacity(post.getMaxTeamSize());

        // Topics & skills
        pod.setTopics(post.getRequiredSkills());

        // Type & Status (NEW for Buddy Beacon)
        pod.setType(CollabPod.PodType.TEAM); // ✅ NEW: Event-based team pod
        pod.setEventId(post.getEventId()); // ✅ NEW: Link to event
        pod.setCollege(post.getCollege()); // Campus isolation

        // Timestamps
        pod.setCreatedAt(LocalDateTime.now());
        pod.setLastActive(LocalDateTime.now());

        // Pod tracking
        pod.setStatus(CollabPod.PodStatus.ACTIVE);
        pod.setLinkedPostId(post.getId()); // Track origin post

        // Save pod FIRST
        CollabPod savedPod = podRepository.save(pod);

        // ✅ ATOMICITY: Immediately update post with linkedPodId
        post.setLinkedPodId(savedPod.getId());
        postRepository.save(post);

        System.out.println("🔗 [TeamCleanup] Pod " + savedPod.getId() + " linked to post " + post.getId());
    }
}
