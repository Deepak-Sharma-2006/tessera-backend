package com.studencollabfin.server.service;

import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.model.PodCooldown;
import com.studencollabfin.server.model.User;
import com.studencollabfin.server.model.Inbox;
import com.studencollabfin.server.repository.CollabPodRepository;
import com.studencollabfin.server.repository.MessageRepository;
import com.studencollabfin.server.repository.PostRepository;
import com.studencollabfin.server.repository.UserRepository;
import com.studencollabfin.server.repository.PodCooldownRepository;
import com.studencollabfin.server.repository.InboxRepository;
import com.studencollabfin.server.exception.PermissionDeniedException;
import com.studencollabfin.server.exception.CooldownException;
import com.studencollabfin.server.exception.BannedFromPodException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
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

    @Autowired
    private PodCooldownRepository podCooldownRepository;

    @Autowired
    private InboxRepository inboxRepository;

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
    @Transactional
    @SuppressWarnings("null")
    public void deletePod(String podId) {
        System.out.println("🗑️ Starting cascade delete for pod: " + podId);

        // Step 1: Fetch Pod Details
        Optional<CollabPod> podOpt = collabPodRepository.findById(podId);
        if (podOpt.isEmpty()) {
            System.out.println("❌ Pod not found: " + podId);
            throw new RuntimeException("CollabPod not found");
        }

        CollabPod pod = podOpt.get();
        System.out.println("   Pod name: " + pod.getName());
        System.out.println("   Source post ID: " + pod.getLinkedPostId());

        try {
            // Step 2: Delete all messages where podId matches
            System.out.println("📝 Deleting messages for pod: " + podId);
            podMessageService.deleteMessagesByPodId(podId);
            System.out.println("✅ Messages deleted for pod: " + podId);

            // Step 3: Delete all cooldowns where podId matches
            System.out.println("⏱️ Deleting cooldowns for pod: " + podId);
            List<PodCooldown> cooldowns = podCooldownRepository.findByPodId(podId);
            if (!cooldowns.isEmpty()) {
                System.out.println("   Found " + cooldowns.size() + " cooldown(s) to delete");
                podCooldownRepository.deleteAll(cooldowns);
                System.out.println("✅ Cooldowns deleted for pod: " + podId);
            } else {
                System.out.println("   No cooldowns found for pod: " + podId);
            }

            // Step 4: Delete the source post
            if (pod.getLinkedPostId() != null && !pod.getLinkedPostId().isEmpty()) {
                System.out.println("📮 Deleting source post: " + pod.getLinkedPostId());

                try {
                    // Verify the post exists
                    Optional<?> postOpt = postRepository.findById(pod.getLinkedPostId());
                    if (postOpt.isEmpty()) {
                        System.out.println("⚠️ Source post not found: " + pod.getLinkedPostId());
                    } else {
                        String postType = postOpt.get().getClass().getSimpleName();
                        System.out.println("   Post type: " + postType);
                        postRepository.deleteById(pod.getLinkedPostId());

                        // Verify deletion
                        Optional<?> postAfterDelete = postRepository.findById(pod.getLinkedPostId());
                        if (postAfterDelete.isEmpty()) {
                            System.out.println("✅ Source post " + pod.getLinkedPostId() + " deleted");
                        } else {
                            System.err.println("❌ ERROR: Source post " + pod.getLinkedPostId() + " still exists!");
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("⚠️ Failed to delete source post: " + ex.getMessage());
                    ex.printStackTrace();
                    throw ex;
                }
            } else {
                System.out.println("⚠️ No source post ID found for pod: " + podId);
            }

            // Step 5: Delete the pod itself
            System.out.println("🗑️ Deleting pod from database: " + podId);
            collabPodRepository.deleteById(podId);

            // Verify pod deletion
            Optional<CollabPod> podAfterDelete = collabPodRepository.findById(podId);
            if (podAfterDelete.isEmpty()) {
                System.out.println("✅ Pod " + podId + " and all its data deleted successfully");
            } else {
                System.err.println("❌ ERROR: Pod " + podId + " still exists after delete!");
                throw new RuntimeException("Pod deletion failed - pod still exists");
            }

        } catch (Exception ex) {
            System.err.println("❌ Cascade delete failed for pod " + podId + ": " + ex.getMessage());
            ex.printStackTrace();
            // @Transactional will handle rollback automatically
            throw new RuntimeException("Cascade delete failed for pod " + podId, ex);
        }
    }

    /**
     * Kick a member from a pod with hierarchy checks and audit logging.
     * 
     * Hierarchy Rules:
     * - Owner can kick Admins or Members
     * - Admin can kick Members only
     * - Members cannot kick anyone
     * 
     * @param podId    The pod ID
     * @param actorId  The user performing the action (kicker)
     * @param targetId The user to be kicked
     * @param reason   Reason for kicking (optional, for audit trail)
     * @return Updated CollabPod
     * @throws PermissionDeniedException if hierarchy is violated
     */
    public CollabPod kickMember(String podId, String actorId, String targetId, String reason) {
        System.out.println("🚪 KICK: User " + actorId + " attempting to kick " + targetId + " from pod " + podId);

        // Step 1: Fetch pod and validate it exists
        CollabPod pod = collabPodRepository.findById(podId)
                .orElseThrow(() -> new RuntimeException("CollabPod not found: " + podId));

        // Step 2: Prevent self-kick
        if (actorId.equals(targetId)) {
            throw new PermissionDeniedException("You cannot kick yourself");
        }

        // Step 3: Determine actor's role and check hierarchy permissions
        String actorRole;
        if (pod.getOwnerId().equals(actorId)) {
            actorRole = "OWNER";
        } else if (pod.getAdminIds().contains(actorId)) {
            actorRole = "ADMIN";
        } else {
            throw new PermissionDeniedException("Only owner or admin can kick members");
        }

        // Step 4: Determine target's role
        String targetRole;
        if (pod.getOwnerId().equals(targetId)) {
            targetRole = "OWNER";
        } else if (pod.getAdminIds().contains(targetId)) {
            targetRole = "ADMIN";
        } else if (pod.getMemberIds().contains(targetId)) {
            targetRole = "MEMBER";
        } else {
            throw new RuntimeException("User is not in this pod");
        }

        // Step 5: Check hierarchy (OWNER > ADMIN > MEMBER)
        if (actorRole.equals("ADMIN") && (targetRole.equals("ADMIN") || targetRole.equals("OWNER"))) {
            throw new PermissionDeniedException(
                    "Admin cannot kick another admin or the owner. Only owner can kick admins.");
        }

        if (actorRole.equals("MEMBER")) {
            throw new PermissionDeniedException("Members cannot kick anyone");
        }

        // Step 6: Cannot kick the owner
        if (targetRole.equals("OWNER")) {
            throw new PermissionDeniedException("Cannot kick the pod owner");
        }

        // Step 7: Move target from memberIds/adminIds to bannedIds
        System.out.println("  ✓ Hierarchy check passed: " + actorRole + " can kick " + targetRole);
        pod.getMemberIds().remove(targetId);
        pod.getAdminIds().remove(targetId);

        if (pod.getBannedIds() == null) {
            pod.setBannedIds(new java.util.ArrayList<>());
        }
        if (!pod.getBannedIds().contains(targetId)) {
            pod.getBannedIds().add(targetId);
        }

        pod.setLastActive(LocalDateTime.now());
        CollabPod updatedPod = collabPodRepository.save(pod);
        System.out.println("  ✓ User " + targetId + " moved to bannedIds");

        // Step 8: Create SYSTEM message for audit trail
        try {
            String actorName = getUserName(actorId);
            String targetName = getUserName(targetId);
            String reasonText = (reason != null && !reason.isEmpty()) ? " - " + reason : "";

            Message systemMsg = new Message();
            systemMsg.setMessageType(Message.MessageType.SYSTEM);
            systemMsg.setPodId(podId);
            systemMsg.setConversationId(podId);
            systemMsg.setText("Admin " + actorName + " kicked " + targetName + reasonText);
            systemMsg.setSentAt(new Date());
            systemMsg.setRead(false);
            systemMsg.setScope("CAMPUS");

            Message savedMsg = messageRepository.save(systemMsg);
            System.out.println("  ✓ System message logged: " + savedMsg.getId());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to log system message: " + e.getMessage());
            e.printStackTrace();
        }

        // Step 9: Create Inbox notification for the banned user (NEW - STAGE 3)
        try {
            System.out.println("  ℹ️ Creating inbox notification for banned user: " + targetId);
            Inbox inboxNotification = new Inbox();
            inboxNotification.setUserId(targetId);
            inboxNotification.setType(Inbox.NotificationType.POD_BAN);
            inboxNotification.setTitle("You were removed from " + pod.getName());
            inboxNotification
                    .setMessage("Reason: " + (reason != null && !reason.isEmpty() ? reason : "No reason provided"));
            inboxNotification.setSeverity(Inbox.NotificationSeverity.HIGH);
            inboxNotification.setPodId(podId);
            inboxNotification.setPodName(pod.getName());
            inboxNotification.setReason(reason);
            inboxNotification.setRead(false);

            inboxRepository.save(inboxNotification);
            System.out.println("  ✓ Inbox notification created for banned user");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to create inbox notification: " + e.getMessage());
            e.printStackTrace();
        }

        return updatedPod;
    }

    /**
     * User leaves a pod and is placed on cooldown to prevent rejoin spam.
     * 
     * Actions:
     * - Remove user from memberIds
     * - Create PodCooldown record (auto-expires in 15 minutes via TTL)
     * - Log SYSTEM message to audit trail
     * 
     * @param podId  The pod ID
     * @param userId The user leaving the pod
     * @throws RuntimeException if pod not found or user is owner
     */
    public void leavePod(String podId, String userId) {
        System.out.println("👋 LEAVE: User " + userId + " leaving pod " + podId);

        // Step 1: Fetch pod
        CollabPod pod = collabPodRepository.findById(podId)
                .orElseThrow(() -> new RuntimeException("CollabPod not found: " + podId));

        // Step 2: Prevent owner from leaving (they must transfer or close)
        if (pod.getOwnerId().equals(userId)) {
            throw new RuntimeException("Pod owner cannot leave. Transfer ownership or close the pod.");
        }

        // Step 3: Remove user from memberIds and adminIds
        pod.getMemberIds().remove(userId);
        pod.getAdminIds().remove(userId);
        pod.setLastActive(LocalDateTime.now());

        // Step 4: Update pod status if needed
        if (pod.getStatus() == CollabPod.PodStatus.FULL &&
                pod.getMemberIds().size() < pod.getMaxCapacity()) {
            pod.setStatus(CollabPod.PodStatus.ACTIVE);
            System.out.println("  ✓ Pod status changed from FULL to ACTIVE");
        }

        CollabPod updatedPod = collabPodRepository.save(pod);
        System.out.println("  ✓ User " + userId + " removed from memberIds");

        // Step 5: Create cooldown record (15 minutes)
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiryDate = now.plusMinutes(15);

            PodCooldown cooldown = new PodCooldown();
            cooldown.setUserId(userId);
            cooldown.setPodId(podId);
            cooldown.setAction("LEAVE");
            cooldown.setCreatedAt(now);
            cooldown.setExpiryDate(expiryDate);

            PodCooldown savedCooldown = podCooldownRepository.save(cooldown);
            System.out.println("  ✓ Cooldown created: " + savedCooldown.getId() + " (expires at " + expiryDate + ")");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to create cooldown: " + e.getMessage());
            e.printStackTrace();
        }

        // Step 6: Log SYSTEM message
        try {
            String userName = getUserName(userId);
            Message systemMsg = new Message();
            systemMsg.setMessageType(Message.MessageType.SYSTEM);
            systemMsg.setPodId(podId);
            systemMsg.setConversationId(podId);
            systemMsg.setText(userName + " left the pod.");
            systemMsg.setSentAt(new Date());
            systemMsg.setRead(false);
            systemMsg.setScope("CAMPUS");

            Message savedMsg = messageRepository.save(systemMsg);
            System.out.println("  ✓ System message logged: " + savedMsg.getId());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to log system message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * User joins a pod with cooldown and ban checks.
     * 
     * Checks:
     * - Not on cooldown (from previous leave)
     * - Not banned from the pod
     * - Pod not full
     * 
     * @param podId  The pod ID
     * @param userId The user joining
     * @return Updated CollabPod
     * @throws CooldownException      if user is on cooldown
     * @throws BannedFromPodException if user is banned
     * @throws RuntimeException       if pod is full or not found
     */
    public CollabPod joinPod(String podId, String userId) {
        System.out.println("✋ JOIN: User " + userId + " attempting to join pod " + podId);

        // Step 1: Fetch pod
        CollabPod pod = collabPodRepository.findById(podId)
                .orElseThrow(() -> new RuntimeException("CollabPod not found: " + podId));

        // Step 2: Check if user is banned
        if (pod.getBannedIds() != null && pod.getBannedIds().contains(userId)) {
            System.out.println("  ✗ User is banned from this pod");
            throw new BannedFromPodException("You are banned from this pod and cannot rejoin");
        }

        // Step 3: Check cooldown status
        Optional<PodCooldown> cooldownOpt = podCooldownRepository.findByUserIdAndPodId(userId, podId);
        if (cooldownOpt.isPresent()) {
            PodCooldown cooldown = cooldownOpt.get();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiryDate = cooldown.getExpiryDate();

            if (now.isBefore(expiryDate)) {
                // User is still on cooldown
                long minutesRemaining = ChronoUnit.MINUTES.between(now, expiryDate);
                System.out.println("  ✗ User is on cooldown for " + minutesRemaining + " more minutes");
                throw new CooldownException(
                        "You cannot rejoin for another " + minutesRemaining + " minute(s). Try again later.",
                        (int) minutesRemaining);
            } else {
                // Cooldown has expired, delete the record
                podCooldownRepository.delete(cooldown);
                System.out.println("  ✓ Cooldown expired, record deleted");
            }
        }

        // Step 4: Check if user is already a member
        if (pod.getMemberIds() != null && pod.getMemberIds().contains(userId)) {
            System.out.println("  ℹ️ User is already a member");
            return pod;
        }

        // Step 5: Check pod capacity
        if (pod.getStatus() == CollabPod.PodStatus.FULL ||
                (pod.getMaxCapacity() > 0 && pod.getMemberIds().size() >= pod.getMaxCapacity())) {
            pod.setStatus(CollabPod.PodStatus.FULL);
            collabPodRepository.save(pod);
            System.out.println("  ✗ Pod is full");
            throw new RuntimeException("CollabPod is full");
        }

        // Step 6: Add user to memberIds
        if (pod.getMemberIds() == null) {
            pod.setMemberIds(new java.util.ArrayList<>());
        }
        pod.getMemberIds().add(userId);
        pod.setLastActive(LocalDateTime.now());

        CollabPod updatedPod = collabPodRepository.save(pod);
        System.out.println(
                "  ✓ User " + userId + " added to memberIds (total members: " + pod.getMemberIds().size() + ")");

        // Step 7: Log SYSTEM message
        try {
            String userName = getUserName(userId);
            Message systemMsg = new Message();
            systemMsg.setMessageType(Message.MessageType.SYSTEM);
            systemMsg.setPodId(podId);
            systemMsg.setConversationId(podId);
            systemMsg.setText(userName + " joined the pod.");
            systemMsg.setSentAt(new Date());
            systemMsg.setRead(false);
            systemMsg.setScope("CAMPUS");

            Message savedMsg = messageRepository.save(systemMsg);
            System.out.println("  ✓ System message logged: " + savedMsg.getId());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to log system message: " + e.getMessage());
            e.printStackTrace();
        }

        return updatedPod;
    }

    /**
     * ✅ STAGE 4: Promote a Member to Admin
     * 
     * Permission: Only Owner can promote
     * Action: Move targetId from memberIds to adminIds
     * Audit: Create SYSTEM message "Owner promoted [User] to Admin"
     * 
     * @param podId    The pod ID
     * @param actorId  The user performing the promotion (must be Owner)
     * @param targetId The user being promoted
     * @return Updated CollabPod
     * @throws PermissionDeniedException If actor is not the Owner
     * @throws RuntimeException          If pod/user not found
     */
    public CollabPod promoteToAdmin(String podId, String actorId, String targetId) {
        System.out.println(
                "🚀 CollabPodService.promoteToAdmin: pod=" + podId + ", actor=" + actorId + ", target=" + targetId);

        // Fetch the pod
        @SuppressWarnings("null")
        java.util.Optional<CollabPod> podOpt = collabPodRepository.findById(podId);
        if (podOpt.isEmpty()) {
            throw new RuntimeException("Pod not found: " + podId);
        }
        CollabPod pod = podOpt.get();

        // ✅ Permission check: Only Owner can promote
        if (pod.getOwnerId() == null || !pod.getOwnerId().equals(actorId)) {
            System.out.println("❌ Permission denied: Only the Pod Owner can promote members");
            throw new PermissionDeniedException("Only the Pod Owner can promote members");
        }

        // Check if target is already an Admin
        if (pod.getAdminIds() != null && pod.getAdminIds().contains(targetId)) {
            System.out.println("⚠️ User is already an Admin");
            return pod;
        }

        // ✅ Move from memberIds to adminIds
        if (pod.getMemberIds() != null && pod.getMemberIds().contains(targetId)) {
            pod.getMemberIds().remove(targetId);
            System.out.println("✅ Removed " + targetId + " from memberIds");
        }

        if (pod.getAdminIds() == null) {
            pod.setAdminIds(new java.util.ArrayList<>());
        }
        if (!pod.getAdminIds().contains(targetId)) {
            pod.getAdminIds().add(targetId);
            System.out.println("✅ Added " + targetId + " to adminIds");
        }

        // Save updated pod
        CollabPod updatedPod = collabPodRepository.save(pod);
        System.out.println("✅ Pod saved with promoted admin");

        // ✅ Create SYSTEM message for audit trail
        try {
            String actorName = getUserName(actorId);
            String targetName = getUserName(targetId);
            String messageText = actorName + " promoted " + targetName + " to Admin";

            Message systemMessage = new Message();
            systemMessage.setPodId(podId);
            systemMessage.setConversationId(podId);
            systemMessage.setText(messageText);
            systemMessage.setContent(messageText);
            systemMessage.setMessageType(Message.MessageType.SYSTEM);
            systemMessage.setAuthorName(actorName);
            systemMessage.setSenderId(actorId);
            systemMessage.setSenderName(actorName);
            systemMessage.setSentAt(LocalDateTime.now());
            systemMessage.setTimestamp(LocalDateTime.now());

            messageRepository.save(systemMessage);
            System.out.println("✅ SYSTEM message saved: " + messageText);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to create SYSTEM message: " + e.getMessage());
        }

        return updatedPod;
    }

    /**
     * ✅ STAGE 4: Demote an Admin to Member
     * 
     * Permission: Only Owner can demote
     * Action: Move targetId from adminIds back to memberIds
     * Audit: Create SYSTEM message "Owner demoted [User] to Member"
     * 
     * @param podId    The pod ID
     * @param actorId  The user performing the demotion (must be Owner)
     * @param targetId The admin being demoted
     * @return Updated CollabPod
     * @throws PermissionDeniedException If actor is not the Owner
     * @throws RuntimeException          If pod/user not found
     */
    public CollabPod demoteToMember(String podId, String actorId, String targetId) {
        System.out.println(
                "📉 CollabPodService.demoteToMember: pod=" + podId + ", actor=" + actorId + ", target=" + targetId);

        // Fetch the pod
        @SuppressWarnings("null")
        java.util.Optional<CollabPod> podOpt = collabPodRepository.findById(podId);
        if (podOpt.isEmpty()) {
            throw new RuntimeException("Pod not found: " + podId);
        }
        CollabPod pod = podOpt.get();

        // ✅ Permission check: Only Owner can demote
        if (pod.getOwnerId() == null || !pod.getOwnerId().equals(actorId)) {
            System.out.println("❌ Permission denied: Only the Pod Owner can demote admins");
            throw new PermissionDeniedException("Only the Pod Owner can demote admins");
        }

        // Check if target is actually an Admin
        if (pod.getAdminIds() == null || !pod.getAdminIds().contains(targetId)) {
            System.out.println("⚠️ User is not an Admin");
            return pod;
        }

        // ✅ Move from adminIds to memberIds
        pod.getAdminIds().remove(targetId);
        System.out.println("✅ Removed " + targetId + " from adminIds");

        if (pod.getMemberIds() == null) {
            pod.setMemberIds(new java.util.ArrayList<>());
        }
        if (!pod.getMemberIds().contains(targetId)) {
            pod.getMemberIds().add(targetId);
            System.out.println("✅ Added " + targetId + " to memberIds");
        }

        // Save updated pod
        CollabPod updatedPod = collabPodRepository.save(pod);
        System.out.println("✅ Pod saved with demoted member");

        // ✅ Create SYSTEM message for audit trail
        try {
            String actorName = getUserName(actorId);
            String targetName = getUserName(targetId);
            String messageText = actorName + " demoted " + targetName + " to Member";

            Message systemMessage = new Message();
            systemMessage.setPodId(podId);
            systemMessage.setConversationId(podId);
            systemMessage.setText(messageText);
            systemMessage.setContent(messageText);
            systemMessage.setMessageType(Message.MessageType.SYSTEM);
            systemMessage.setAuthorName(actorName);
            systemMessage.setSenderId(actorId);
            systemMessage.setSenderName(actorName);
            systemMessage.setSentAt(LocalDateTime.now());
            systemMessage.setTimestamp(LocalDateTime.now());

            messageRepository.save(systemMessage);
            System.out.println("✅ SYSTEM message saved: " + messageText);
        } catch (Exception e) {
            System.err.println("⚠️ Failed to create SYSTEM message: " + e.getMessage());
        }

        return updatedPod;
    }

    /**
     * Helper method to get user's display name from user ID
     */
    private String getUserName(String userId) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            return userOpt.map(User::getFullName).orElse("User");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to fetch user name: " + e.getMessage());
            return "User";
        }
    }
}
