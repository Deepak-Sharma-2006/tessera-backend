package com.studencollabfin.server.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.repository.CollabPodRepository;
import com.studencollabfin.server.repository.PodMessageRepository;
import com.studencollabfin.server.repository.PostRepository;
import com.studencollabfin.server.repository.UserRepository;

@Service
@SuppressWarnings("null")
public class CollabPodService {

    @Autowired
    private CollabPodRepository collabPodRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PodMessageRepository podMessageRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PostRepository postRepository;

    /**
     * Get all public "LOOKING_FOR" pods (discovery page).
     */
    public List<CollabPod> getPublicPods() {
        return collabPodRepository.findByType(CollabPod.PodType.LOOKING_FOR);
    }

    /**
     * Get all pods where the user is a member (LOOKING_FOR or TEAM).
     */
    public List<CollabPod> getUserPods(String userId) {
        return collabPodRepository.findByMemberIdsContaining(userId);
    }

    public CollabPod createPod(String creatorId, CollabPod pod) {
        userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        pod.setCreatorId(creatorId);
        pod.setCreatedAt(LocalDateTime.now());
        pod.setLastActive(LocalDateTime.now());
        pod.setStatus(CollabPod.PodStatus.ACTIVE);

        if (pod.getMemberIds() == null) {
            pod.setMemberIds(new java.util.ArrayList<>());
        }
        pod.getMemberIds().add(creatorId);

        if (pod.getModeratorIds() == null) {
            pod.setModeratorIds(new java.util.ArrayList<>());
        }
        pod.getModeratorIds().add(creatorId);

        CollabPod savedPod = collabPodRepository.save(pod);

        // Award XP for creating a collab pod
        userService.awardCollabPodCreationXP(creatorId);

        return savedPod;
    }

    public CollabPod joinPod(String podId, String userId) {
        CollabPod pod = collabPodRepository.findById(podId)
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

    public CollabPod scheduleMeeting(String podId, String moderatorId, CollabPod.Meeting meeting) {
        CollabPod pod = collabPodRepository.findById(podId)
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

    public List<CollabPod> searchPodsByTopic(String topic) {
        return collabPodRepository.findByTopicsContaining(topic);
    }

    public void leavePod(String podId, String userId) {
        CollabPod pod = collabPodRepository.findById(podId)
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

    @Transactional
    public void deletePod(String podId, String requesterId) {
        // Verify the requester is the pod owner
        CollabPod pod = collabPodRepository.findById(podId)
                .orElseThrow(() -> new RuntimeException("Pod not found"));

        if (!pod.getCreatorId().equals(requesterId)) {
            throw new RuntimeException("Only the pod owner can delete this pod");
        }

        // 1. Delete all chat messages for this pod
        podMessageRepository.deleteByPodId(podId);

        // 2. Delete the linked "Looking For" post (cascade delete)
        postRepository.deleteByLinkedPodId(podId);

        // 3. Delete the pod itself
        collabPodRepository.deleteById(podId);
    }
}
