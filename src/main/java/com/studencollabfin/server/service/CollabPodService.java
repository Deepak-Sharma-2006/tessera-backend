package com.studencollabfin.server.service;

import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.repository.CollabPodRepository;
import com.studencollabfin.server.repository.MessageRepository;
import com.studencollabfin.server.repository.PostRepository;
import com.studencollabfin.server.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CollabPodService {

    @Autowired
    private CollabPodRepository collabPodRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PodMessageService podMessageService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @SuppressWarnings("null")
    public CollabPod createPod(String creatorId, CollabPod pod) {
        System.out.println("CollabPodService.createPod called with creatorId: " + creatorId);
        var userOpt = userRepository.findById((String) creatorId);
        var user = userOpt.orElseThrow(() -> new RuntimeException("User not found"));

        pod.setCreatorId(creatorId);
        pod.setCreatedAt(LocalDateTime.now());
        pod.setLastActive(LocalDateTime.now());
        pod.setStatus(CollabPod.PodStatus.ACTIVE);

        // ✅ Campus Isolation: Set college based on pod scope
        if (pod.getScope() == com.studencollabfin.server.model.PodScope.CAMPUS) {
            // Campus pods: Store user's college for isolation
            if (user.getCollegeName() != null) {
                pod.setCollege(user.getCollegeName());
                System.out.println("✅ CAMPUS pod college set to: " + user.getCollegeName());
            }
        } else if (pod.getScope() == com.studencollabfin.server.model.PodScope.GLOBAL) {
            // Global pods: Mark with "GLOBAL" so they're visible to all
            pod.setCollege("GLOBAL");
            System.out.println("✅ GLOBAL pod college set to: GLOBAL");
        }

        if (pod.getMemberIds() == null) {
            pod.setMemberIds(new java.util.ArrayList<>());
        }
        // Ensure creator ID is added exactly once (prevent duplicates)
        if (!pod.getMemberIds().contains(creatorId)) {
            pod.getMemberIds().add(creatorId);
        }

        if (pod.getModeratorIds() == null) {
            pod.setModeratorIds(new java.util.ArrayList<>());
        }
        pod.getModeratorIds().add(creatorId);

        System.out.println("Saving pod to database: " + pod.getName() + " with scope: " + pod.getScope() + " and type: "
                + pod.getType());
        CollabPod savedPod = collabPodRepository.save(pod);
        System.out.println("Pod saved successfully with ID: " + savedPod.getId());

        // Award XP for creating a collab pod
        userService.awardCollabPodCreationXP(creatorId);

        return savedPod;
    }

    /**
     * Get a pod by ID
     */
    public CollabPod getPodById(String podId) {
        return collabPodRepository.findById(podId)
                .orElseThrow(() -> new RuntimeException("CollabPod not found: " + podId));
    }

    @SuppressWarnings("null")
    public CollabPod joinPod(String podId, String userId) {
        CollabPod pod = collabPodRepository.findById((String) podId)
                .orElseThrow(() -> new RuntimeException("CollabPod not found"));

        if (pod.getStatus() == CollabPod.PodStatus.FULL ||
                pod.getMemberIds().size() >= pod.getMaxCapacity()) {
            pod.setStatus(CollabPod.PodStatus.FULL);
            collabPodRepository.save(pod);
            throw new RuntimeException("CollabPod is full");
        }

        if (!pod.getMemberIds().contains(userId)) {
            pod.getMemberIds().add(userId);
            pod.setLastActive(LocalDateTime.now());
        }

        return collabPodRepository.save(pod);
    }

    @SuppressWarnings("null")
    public CollabPod scheduleMeeting(String podId, String moderatorId, CollabPod.Meeting meeting) {
        CollabPod pod = collabPodRepository.findById((String) podId)
                .orElseThrow(() -> new RuntimeException("CollabPod not found"));

        if (!pod.getModeratorIds().contains(moderatorId)) {
            throw new RuntimeException("Only moderators can schedule meetings");
        }

        if (pod.getMeetings() == null) {
            pod.setMeetings(new java.util.ArrayList<>());
        }

        meeting.setStatus(CollabPod.MeetingStatus.SCHEDULED);
        pod.getMeetings().add(meeting);
        pod.setLastActive(LocalDateTime.now());

        return collabPodRepository.save(pod);
    }

    public List<CollabPod> getUserPods(String userId) {
        List<CollabPod> createdPods = collabPodRepository.findByCreatorId(userId);
        List<CollabPod> joinedPods = collabPodRepository.findByMemberIdsContaining(userId);
        createdPods.addAll(joinedPods);
        return createdPods;
    }

    public List<CollabPod> searchPodsByTopic(String topic) {
        return collabPodRepository.findByTopicsContaining(topic);
    }

    @SuppressWarnings("null")
    public void leavePod(String podId, String userId) {
        CollabPod pod = collabPodRepository.findById((String) podId)
                .orElseThrow(() -> new RuntimeException("CollabPod not found"));

        if (pod.getCreatorId().equals(userId)) {
            throw new RuntimeException("Pod creator cannot leave. Transfer ownership or close the pod.");
        }

        pod.getMemberIds().remove(userId);
        pod.getModeratorIds().remove(userId);
        pod.setLastActive(LocalDateTime.now());

        if (pod.getStatus() == CollabPod.PodStatus.FULL &&
                pod.getMemberIds().size() < pod.getMaxCapacity()) {
            pod.setStatus(CollabPod.PodStatus.ACTIVE);
        }

        collabPodRepository.save(pod);
    }

    public List<Message> getMessagesForPod(String podId) {
        // The podId is used as the conversationId for messages
        return messageRepository.findByConversationIdOrderBySentAtAsc(podId);
    }

    public Message saveMessage(Message message) {
        try {
            // Ensure message has an ID
            if (message.getId() == null || message.getId().isEmpty()) {
                message.setId(new java.util.UUID(java.util.UUID.randomUUID().getMostSignificantBits(),
                        java.util.UUID.randomUUID().getLeastSignificantBits()).toString());
            }

            // CRITICAL FIX: Ensure senderId is always set
            // This is used by frontend to determine message alignment (left vs right)
            if (message.getSenderId() == null || message.getSenderId().isEmpty()) {
                throw new IllegalArgumentException("senderId is required for messages");
            }

            // Ensure conversationId is set from podId if not already set
            if (message.getConversationId() == null && message.getPodId() != null) {
                message.setConversationId(message.getPodId());
            }

            // Set sentAt if not provided
            if (message.getSentAt() == null) {
                message.setSentAt(new java.util.Date());
            }

            // If content is provided instead of text, use content as text
            if (message.getText() == null && message.getContent() != null) {
                message.setText(message.getContent());
            }

            // CRITICAL FIX: Ensure attachment fields are preserved
            // Frontend sends attachmentUrl and attachmentType - these MUST be saved to
            // database
            if (message.getAttachmentUrl() != null) {
                System.out.println("✓ Message has attachment URL: " + message.getAttachmentUrl());
                System.out.println("  - Type: " + message.getAttachmentType());
                System.out.println("  - FileName: " + message.getFileName());
            }

            // If attachmentType is not set, default to NONE
            if (message.getAttachmentType() == null || message.getAttachmentType().isEmpty()) {
                message.setAttachmentType("NONE");
            }

            // Default to unread status
            message.setRead(false);

            // Set messageType and scope for campus pods
            message.setMessageType(Message.MessageType.CHAT);
            message.setScope("CAMPUS");

            // CRITICAL: Save to messages collection with ALL fields intact
            Message savedMessage = messageRepository.save(message);
            System.out.println("✓ Message saved to messages collection: " + savedMessage.getId() + " for pod: "
                    + savedMessage.getPodId());
            System.out.println("  - Sender ID: " + savedMessage.getSenderId());
            System.out.println("  - Attachment URL saved: "
                    + (savedMessage.getAttachmentUrl() != null ? savedMessage.getAttachmentUrl() : "NONE"));
            System.out.println("  - Attachment Type saved: " + savedMessage.getAttachmentType());

            return savedMessage;
        } catch (Exception e) {
            System.err.println("✗ Error saving message: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to save message", e);
        }
    }

    /**
     * Delete a pod with cascade operations (ONE-WAY ONLY).
     * Flow: Pod is deleted → data (messages) inside is deleted → post linked to it
     * is deleted.
     * 
     * IMPORTANT: This is ONE-WAY cascade.
     * - Pod deletion cascades to: messages → linked post
     * - Post deletion does NOT cascade back to pod (see PostService)
     * 
     * This prevents circular delete and ensures ghost data doesn't appear in post
     * tabs.
     */
    @SuppressWarnings("null")
    public void deletePod(String podId) {
        Optional<CollabPod> podOpt = collabPodRepository.findById(podId);
        if (podOpt.isEmpty()) {
            System.out.println("Pod not found: " + podId);
            throw new RuntimeException("CollabPod not found");
        }

        CollabPod pod = podOpt.get();
        System.out.println("🗑️ Starting cascade delete for pod: " + podId);
        System.out.println("   Pod name: " + pod.getName());
        System.out.println("   Linked post ID: " + pod.getLinkedPostId());

        // Step 1: Delete all messages/data inside this pod
        try {
            System.out.println("📝 Deleting messages for pod: " + podId);
            podMessageService.deleteMessagesByPodId(podId);
            System.out.println("✅ Messages deleted for pod: " + podId);
        } catch (Exception ex) {
            System.err.println("⚠️ Failed to delete messages for pod " + podId + ": " + ex.getMessage());
            ex.printStackTrace();
        }

        // Step 2: Delete the linked post (either GLOBAL COLLAB or CAMPUS LOOKING_FOR)
        // This is the ONLY direction of cascade - posts do NOT trigger pod deletion
        if (pod.getLinkedPostId() != null && !pod.getLinkedPostId().isEmpty()) {
            try {
                System.out.println("📮 Attempting to delete linked post: " + pod.getLinkedPostId());

                // First verify the post exists
                Optional<?> postOpt = postRepository.findById(pod.getLinkedPostId());
                if (postOpt.isEmpty()) {
                    System.out.println("⚠️ Linked post not found: " + pod.getLinkedPostId());
                } else {
                    System.out.println("📍 Post found: " + postOpt.get().getClass().getSimpleName());
                    postRepository.deleteById(pod.getLinkedPostId());

                    // Verify deletion
                    Optional<?> postAfterDelete = postRepository.findById(pod.getLinkedPostId());
                    if (postAfterDelete.isEmpty()) {
                        System.out.println(
                                "✅ Cascade Delete: Linked post " + pod.getLinkedPostId() + " deleted for pod " + podId);
                    } else {
                        System.err.println("❌ ERROR: Post " + pod.getLinkedPostId() + " still exists after delete!");
                    }
                }
            } catch (Exception ex) {
                System.err.println("⚠️ Failed to delete linked post: " + ex.getMessage());
                ex.printStackTrace();
            }
        } else {
            System.out.println("⚠️ No linked post ID found for pod: " + podId);
        }

        // Step 3: Delete the pod itself
        try {
            System.out.println("🗑️ Deleting pod from database: " + podId);
            collabPodRepository.deleteById(podId);

            // Verify pod deletion
            Optional<CollabPod> podAfterDelete = collabPodRepository.findById(podId);
            if (podAfterDelete.isEmpty()) {
                System.out.println("✅ Pod " + podId + " and all its data deleted successfully");
            } else {
                System.err.println("❌ ERROR: Pod " + podId + " still exists after delete!");
            }
        } catch (Exception ex) {
            System.err.println("⚠️ Failed to delete pod " + podId + ": " + ex.getMessage());
            ex.printStackTrace();
            throw new RuntimeException("Failed to delete pod", ex);
        }
    }
}
