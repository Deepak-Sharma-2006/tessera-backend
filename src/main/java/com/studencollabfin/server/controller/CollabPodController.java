package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.model.PodScope;
import com.studencollabfin.server.model.XPAction;
import com.studencollabfin.server.repository.CollabPodRepository;
import com.studencollabfin.server.service.CollabPodService;
import com.studencollabfin.server.service.GamificationService;
import com.studencollabfin.server.service.UserService;
import com.studencollabfin.server.exception.PermissionDeniedException;
import com.studencollabfin.server.exception.CooldownException;
import com.studencollabfin.server.exception.BannedFromPodException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pods")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class CollabPodController {

    private final CollabPodRepository collabPodRepository;
    private final CollabPodService collabPodService;
    private final GamificationService gamificationService;
    private final UserService userService;

    public CollabPodController(CollabPodRepository collabPodRepository, CollabPodService collabPodService,
            GamificationService gamificationService, UserService userService) {
        this.collabPodRepository = collabPodRepository;
        this.collabPodService = collabPodService;
        this.gamificationService = gamificationService;
        this.userService = userService;
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
        @SuppressWarnings("null")
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
        }
        return ResponseEntity.ok(pod);
    }

    @GetMapping
    public ResponseEntity<List<CollabPod>> getPods(@RequestParam(required = false) String scope) {
        try {
            List<CollabPod> pods;

            if (scope != null && !scope.isEmpty()) {
                try {
                    PodScope podScope = PodScope.valueOf(scope.toUpperCase());
                    pods = collabPodRepository.findByScope(podScope);
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body(null);
                }
            } else {
                pods = collabPodRepository.findAll();
            }

            return ResponseEntity.ok(pods != null ? pods : new java.util.ArrayList<>());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Strict endpoint for CAMPUS scope only, filtered by current user's college.
     * ✅ Campus Isolation: Returns only CAMPUS pods from the user's college.
     * Returns only pods with scope=CAMPUS, type=LOOKING_FOR, and college matching
     * user's college.
     * Used by CollabPodsPage "Looking For" tab.
     */
    @GetMapping("/campus")
    public ResponseEntity<List<CollabPod>> getCampusPods(Authentication authentication,
            HttpServletRequest request) {
        try {
            // Get current user's college
            String userId = getCurrentUserId(authentication, request);
            String userCollege = null;

            if (userId != null && !userId.trim().isEmpty()) {
                try {
                    com.studencollabfin.server.model.User currentUser = userService.getUserById(userId);
                    if (currentUser != null && currentUser.getCollegeName() != null) {
                        userCollege = currentUser.getCollegeName();
                    }
                } catch (Exception ex) {
                    System.err.println("⚠️ Error fetching current user: " + ex.getMessage());
                }
            }

            if (userCollege == null || userCollege.trim().isEmpty()) {
                System.out.println("⚠️ User college is null/empty, returning empty campus pods list");
                return ResponseEntity.ok(new java.util.ArrayList<>());
            }

            // ✅ Campus Isolation: Filter by CAMPUS scope AND user's college
            List<CollabPod> campusPods = collabPodRepository.findByScopeAndCollege(PodScope.CAMPUS, userCollege);

            // Filter for only LOOKING_FOR type pods
            java.util.List<CollabPod> lookingForPods = (campusPods != null ? campusPods
                    : new java.util.ArrayList<CollabPod>())
                    .stream()
                    .filter(pod -> pod.getType() != null && pod.getType() == CollabPod.PodType.LOOKING_FOR)
                    .toList();

            System.out.println("✅ Campus pods filtered for college: " + userCollege + ", count: "
                    + lookingForPods.size());

            return ResponseEntity.ok(lookingForPods);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Strict endpoint for GLOBAL scope only.
     * Returns only pods with scope=GLOBAL.
     * Used by CollabRooms to prevent Campus pods from appearing in Global rooms
     * view.
     */
    @GetMapping("/global")
    public ResponseEntity<List<CollabPod>> getGlobalPods() {
        try {
            List<CollabPod> pods = collabPodRepository.findByScope(PodScope.GLOBAL);
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

            // Handle null or empty database
            // Filter for pods with status LOOKING_FOR_MEMBERS or ACTIVE
            java.util.List<CollabPod> filtered = (pods != null ? pods : new java.util.ArrayList<CollabPod>())
                    .stream()
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
    public ResponseEntity<List<CollabPod>> getMyTeams(@RequestParam(required = false) String scope) {
        try {
            // TODO: Implement My Teams functionality in the future
            // For now, return empty list
            return ResponseEntity.ok(new java.util.ArrayList<>());
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

    /**
     * Join a pod endpoint - adds user to pod members list
     */
    @PostMapping("/{id}/join")
    public ResponseEntity<?> joinPod(@PathVariable String id,
            @RequestBody(required = false) java.util.Map<String, String> payload) {
        try {
            @SuppressWarnings("null")
            java.util.Optional<CollabPod> podOpt = collabPodRepository.findById(id);
            if (podOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("error", "Pod not found"));
            }

            CollabPod pod = podOpt.get();

            // For now, we'll accept any user joining
            // In production, extract userId from authentication
            String userId = payload != null ? payload.get("userId") : null;
            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "userId is required"));
            }

            // Add userId to members list (create if missing)
            if (pod.getMemberIds() == null) {
                pod.setMemberIds(new java.util.ArrayList<>());
            }

            if (!pod.getMemberIds().contains(userId)) {
                pod.getMemberIds().add(userId);
                collabPodRepository.save(pod);

                // 📊 GAMIFICATION: Award XP for joining a pod
                gamificationService.awardXp(userId, XPAction.JOIN_POD);
            }

            return ResponseEntity.ok(java.util.Map.of("message", "Successfully joined pod", "pod", pod));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ STAGE 3: Kick a member from a pod with hierarchy enforcement
     * POST /pods/{id}/kick
     * 
     * Request body:
     * {
     * "actorId": "user123", // User performing the kick
     * "targetId": "user456", // User being kicked
     * "reason": "Spam" // Reason: Spam, Harassment, Other
     * }
     * 
     * Response: Updated CollabPod or error
     */
    @PostMapping("/{id}/kick")
    public ResponseEntity<?> kickMember(@PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        try {
            String actorId = payload.get("actorId");
            String targetId = payload.get("targetId");
            String reason = payload.get("reason");

            if (actorId == null || targetId == null || reason == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "actorId, targetId, and reason are required"));
            }

            CollabPod updatedPod = collabPodService.kickMember(id, actorId, targetId, reason);
            return ResponseEntity.ok(updatedPod);

        } catch (PermissionDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ STAGE 3: Leave a pod (creates 15-minute cooldown)
     * POST /pods/{id}/leave
     * 
     * Request body:
     * {
     * "userId": "user123"
     * }
     * 
     * Response: Success message or error
     */
    @PostMapping("/{id}/leave")
    public ResponseEntity<?> leavePod(@PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        try {
            String userId = payload.get("userId");

            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "userId is required"));
            }

            collabPodService.leavePod(id, userId);
            return ResponseEntity.ok(Map.of("message", "Successfully left the pod"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Transfer ownership of a pod to another member/admin
     * 
     * Prevents headless groups by requiring ownership transfer before owner leaves
     */
    @PostMapping("/{id}/transfer-ownership")
    public ResponseEntity<?> transferOwnership(@PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        try {
            String currentOwnerId = payload.get("currentOwnerId");
            String newOwnerId = payload.get("newOwnerId");

            if (currentOwnerId == null || currentOwnerId.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "currentOwnerId is required"));
            }
            if (newOwnerId == null || newOwnerId.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "newOwnerId is required"));
            }

            CollabPod updatedPod = collabPodService.transferOwnership(id, currentOwnerId, newOwnerId);
            return ResponseEntity.ok(updatedPod);

        } catch (com.studencollabfin.server.exception.PermissionDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ STAGE 3: Enhanced join endpoint that checks cooldown and ban
     * 
     * Replaces the old simple join endpoint
     * Now validates:
     * - User is not banned
     * - User doesn't have active cooldown
     * - Pod is not full
     */
    @PostMapping("/{id}/join-enhanced")
    public ResponseEntity<?> joinPodEnhanced(@PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        try {
            String userId = payload.get("userId");

            if (userId == null || userId.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "userId is required"));
            }

            CollabPod updatedPod = collabPodService.joinPod(id, userId);
            return ResponseEntity.ok(updatedPod);

        } catch (BannedFromPodException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (CooldownException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "error", e.getMessage(),
                            "minutesRemaining", e.getMinutesRemaining()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ STAGE 4: Promote a Member to Admin
     * POST /pods/{id}/promote-to-admin
     * 
     * Only Owner can promote members
     * Moves user from memberIds to adminIds
     * Creates SYSTEM message for audit trail
     */
    @PostMapping("/{id}/promote-to-admin")
    public ResponseEntity<?> promoteToAdmin(@PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        try {
            String actorId = payload.get("actorId");
            String targetId = payload.get("targetId");

            if (actorId == null || targetId == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "actorId and targetId are required"));
            }

            CollabPod updatedPod = collabPodService.promoteToAdmin(id, actorId, targetId);
            return ResponseEntity.ok(updatedPod);

        } catch (PermissionDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * ✅ STAGE 4: Demote an Admin to Member
     * POST /pods/{id}/demote-to-member
     * 
     * Only Owner can demote admins
     * Moves user from adminIds to memberIds
     * Creates SYSTEM message for audit trail
     */
    @PostMapping("/{id}/demote-to-member")
    public ResponseEntity<?> demoteToMember(@PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        try {
            String actorId = payload.get("actorId");
            String targetId = payload.get("targetId");

            if (actorId == null || targetId == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "actorId and targetId are required"));
            }

            CollabPod updatedPod = collabPodService.demoteToMember(id, actorId, targetId);
            return ResponseEntity.ok(updatedPod);

        } catch (PermissionDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete a pod with cascade operations (ONE-WAY CASCADE).
     * 
     * Flow: Pod deletion → Messages deleted → Linked post deleted
     * 
     * Returns metadata including which post type tabs need to refresh on client.
     * This ensures users don't see ghost posts after a pod is deleted.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePod(@PathVariable String id) {
        try {
            // Fetch pod BEFORE deletion to determine scope and return refresh info
            @SuppressWarnings("null")
            java.util.Optional<CollabPod> podOpt = collabPodRepository.findById(id);

            String scope = "UNKNOWN";
            if (podOpt.isPresent()) {
                CollabPod pod = podOpt.get();
                scope = pod.getScope() != null ? pod.getScope().toString() : "UNKNOWN";
            }

            // Perform cascade delete
            collabPodService.deletePod(id);

            // Return response with refresh metadata
            // Frontend should use this to refresh post tabs appropriately
            return ResponseEntity.ok().body(java.util.Map.of(
                    "message", "Pod deleted successfully",
                    "deletedPodId", id,
                    "scope", scope,
                    "shouldRefreshPostTabs", true));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Helper method to extract current user ID
    private String getCurrentUserId(Authentication authentication, HttpServletRequest request) {
        if (authentication != null && authentication.getPrincipal() != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            }
            return principal.toString();
        }

        // Fall back to X-User-Id header
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.trim().isEmpty()) {
            return userId;
        }

        return null;
    }
}
