package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.repository.CollabPodRepository;
import com.studencollabfin.server.repository.UserRepository;
import com.studencollabfin.server.service.CollabPodService;
import com.studencollabfin.server.service.AchievementService;
import com.studencollabfin.server.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/pods")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class CollabPodController {

    private final CollabPodRepository collabPodRepository;
    private final CollabPodService collabPodService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AchievementService achievementService;

    public CollabPodController(CollabPodRepository collabPodRepository, CollabPodService collabPodService) {
        this.collabPodRepository = collabPodRepository;
        this.collabPodService = collabPodService;
    }

    /**
     * Endpoint for applying to a pod (team) via BuddyBeacon.
     * Matches frontend POST /beacon/apply/{id}.
     * Request body: { "userId": "..." }
     */
    @PostMapping("/beacon/apply/{id}")
    public ResponseEntity<?> applyToPod(@PathVariable String id, @RequestBody java.util.Map<String, String> payload) {
        String userId = payload.get("userId");
        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.badRequest().body("Missing userId");
        }
        java.util.Optional<CollabPod> podOpt = collabPodRepository.findById(id);
        if (podOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pod not found");
        }
        CollabPod pod = podOpt.get();
        // Add userId to applicants list (create if missing)
        if (pod.getApplicants() == null) {
            pod.setApplicants(new java.util.ArrayList<>());
        }
        if (!pod.getApplicants().contains(userId)) {
            pod.getApplicants().add(userId);
            collabPodRepository.save(pod);

            // Trigger Pod Pioneer badge unlock
            achievementService.onJoinPod(userId);
        }
        return ResponseEntity.ok(pod);
    }

    /**
     * Join a pod and trigger badge unlock logic
     * Request body: { "userId": "..." }
     */
    @PostMapping("/{id}/join")
    public ResponseEntity<?> joinPod(@PathVariable String id, @RequestBody java.util.Map<String, String> payload) {
        String userId = payload.get("userId");
        if (userId == null || userId.isEmpty()) {
            return ResponseEntity.badRequest().body("Missing userId");
        }

        java.util.Optional<CollabPod> podOpt = collabPodRepository.findById(id);
        if (podOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pod not found");
        }

        CollabPod pod = podOpt.get();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Add user to members list
        if (pod.getMemberIds() == null) {
            pod.setMemberIds(new ArrayList<>());
        }
        if (!pod.getMemberIds().contains(userId)) {
            pod.getMemberIds().add(userId);
        }

        // Check for Bridge Builder badge (inter-college collaboration)
        boolean isInterCollegePod = hasMultipleCollegges(pod);
        if (isInterCollegePod && !user.getBadges().contains("Bridge Builder")) {
            if (user.getBadges() == null) {
                user.setBadges(new ArrayList<>());
            }
            user.getBadges().add("Bridge Builder");
            achievementService.unlockAchievement(userId, "Bridge Builder");
        }

        // Unlock Pod Pioneer badge
        achievementService.onJoinPod(userId);

        collabPodRepository.save(pod);
        userRepository.save(user);

        return ResponseEntity.ok(user);
    }

    /**
     * Check if a pod has members from multiple colleges (for Bridge Builder badge)
     */
    private boolean hasMultipleCollegges(CollabPod pod) {
        if (pod.getMemberIds() == null || pod.getMemberIds().isEmpty()) {
            return false;
        }

        java.util.Set<String> colleges = new java.util.HashSet<>();
        for (String memberId : pod.getMemberIds()) {
            java.util.Optional<User> memberOpt = userRepository.findById(memberId);
            if (memberOpt.isPresent()) {
                String collegeName = memberOpt.get().getCollegeName();
                if (collegeName != null) {
                    colleges.add(collegeName);
                }
            }
        }

        return colleges.size() > 1;
    }

    @GetMapping
    public ResponseEntity<List<CollabPod>> getAllPods() {
        try {
            List<CollabPod> pods = collabPodRepository.findAll();
            return ResponseEntity.ok(pods != null ? pods : new java.util.ArrayList<>());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/looking-for")
    public ResponseEntity<List<CollabPod>> getLookingForPods() {
        try {
            List<CollabPod> pods = collabPodRepository.findAll();
            if (pods == null || pods.isEmpty()) {
                return ResponseEntity.ok(new java.util.ArrayList<>());
            }

            // Filter for pods with status LOOKING_FOR_MEMBERS or ACTIVE
            java.util.List<CollabPod> filtered = pods.stream()
                    .filter(pod -> pod.getStatus() != null &&
                            (pod.getStatus().toString().equals("LOOKING_FOR_MEMBERS") ||
                                    pod.getStatus().toString().equals("ACTIVE")))
                    .toList();
            return ResponseEntity.ok(filtered);
        } catch (Exception e) {
            e.printStackTrace(); // This prints the error to the terminal
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/my-teams")
    public ResponseEntity<List<CollabPod>> getMyTeams() {
        try {
            // This endpoint would typically filter by current user
            // For now, returning all pods as a placeholder
            List<CollabPod> pods = collabPodRepository.findAll();
            return ResponseEntity.ok(pods != null ? pods : new java.util.ArrayList<>());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<CollabPod> getPodById(@PathVariable String id) {
        @SuppressWarnings("null")
        ResponseEntity<CollabPod> response = collabPodRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        return response;
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<Message>> getPodMessages(@PathVariable String id) {
        try {
            List<Message> messages = collabPodService.getMessagesForPod(id);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<Message> sendMessage(@PathVariable String id, @RequestBody Message message) {
        try {
            // Set the conversationId (podId) so the message knows which chat it belongs to
            message.setConversationId(id);
            message.setPodId(id);

            // Save to MongoDB using the service
            Message savedMessage = collabPodService.saveMessage(message);
            return ResponseEntity.ok(savedMessage);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * This is the endpoint for creating a new collaboration pod.
     * It receives pod data from the React form and saves it to the database.
     */
    @PostMapping
    public ResponseEntity<CollabPod> createPod(@RequestBody CollabPod newPod) {
        @SuppressWarnings("null")
        CollabPod savedPod = collabPodRepository.save(newPod);
        return new ResponseEntity<>(savedPod, HttpStatus.CREATED);
    }
}
