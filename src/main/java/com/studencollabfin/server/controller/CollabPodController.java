package com.studencollabfin.server.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.model.PodMessage;
import com.studencollabfin.server.model.User;
import com.studencollabfin.server.repository.CollabPodRepository;
import com.studencollabfin.server.service.CollabPodService;
import com.studencollabfin.server.service.PodMessageService;
import com.studencollabfin.server.service.PostService;
import com.studencollabfin.server.service.UserService;

@RestController
@RequestMapping("/api/pods")
@SuppressWarnings("null")
public class CollabPodController {

    private final CollabPodRepository collabPodRepository;
    private final CollabPodService collabPodService;
    private final PodMessageService podMessageService;
    private final PostService postService;
    private final UserService userService;

    public CollabPodController(CollabPodRepository collabPodRepository, CollabPodService collabPodService,
            PodMessageService podMessageService, PostService postService, UserService userService) {
        this.collabPodRepository = collabPodRepository;
        this.collabPodService = collabPodService;
        this.podMessageService = podMessageService;
        this.postService = postService;
        this.userService = userService;
    }

    // ... (Keep your GET endpoints exactly as they were) ...
    @GetMapping("/looking-for")
    public ResponseEntity<List<CollabPod>> getPublicPods() {
        return ResponseEntity.ok(collabPodService.getPublicPods());
    }

    @GetMapping("/my-teams")
    public ResponseEntity<List<CollabPod>> getUserPods(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId == null || userId.isEmpty())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(collabPodService.getUserPods(userId));
    }

    @GetMapping
    public ResponseEntity<List<CollabPod>> getAllPods() {
        return ResponseEntity.ok(collabPodRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CollabPod> getPodById(@PathVariable String id) {
        return collabPodRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<PodMessage>> getPodMessages(@PathVariable String id) {
        return ResponseEntity.ok(podMessageService.getMessagesByPodId(id));
    }

    // ✅ FIXED: Create Pod (Explicitly sets Creator Name & ID)
    @PostMapping
    public ResponseEntity<?> createPod(@RequestBody CollabPod newPod,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
        }
        try {
            User creator = userService.findByEmail(userDetails.getUsername());
            if (creator == null)
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");

            // 1. Force ID and Name from the Authenticated User
            newPod.setCreatorId(creator.getId());
            newPod.setCreatorName(creator.getFullName()); // ✅ Solves "Placeholder" issue
            newPod.setCreatedAt(LocalDateTime.now());

            // 2. Initialize lists if null
            if (newPod.getMemberIds() == null)
                newPod.setMemberIds(new ArrayList<>());
            if (!newPod.getMemberIds().contains(creator.getId())) {
                newPod.getMemberIds().add(creator.getId());
            }

            // 3. Save directly via Repository (bypassing Service to ensure fields are
            // saved)
            CollabPod savedPod = collabPodRepository.save(newPod);

            System.out.println("✅ Pod Created: " + savedPod.getTitle() + " by " + creator.getFullName() + " ("
                    + creator.getId() + ")");
            return new ResponseEntity<>(savedPod, HttpStatus.CREATED);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // ✅ FIXED: Delete Pod (Cascade Delete + Explicit Ownership Check)
    @DeleteMapping("/{podId}")
    public ResponseEntity<?> deletePod(@PathVariable String podId,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Auth required");

        try {
            User currentUser = userService.findByEmail(userDetails.getUsername());

            // 1. Find the Pod
            CollabPod pod = collabPodRepository.findById(podId).orElse(null);
            if (pod == null)
                return ResponseEntity.notFound().build();

            // 2. LOGGING FOR DEBUGGING
            System.out.println("🗑️ DELETE REQUEST: Pod " + podId);
            System.out.println("   - Requester: " + currentUser.getId());
            System.out.println("   - Owner: " + pod.getCreatorId());

            // 3. Explicit Ownership Check
            if (!pod.getCreatorId().equals(currentUser.getId())) {
                System.out.println("❌ DELETE BLOCKED: ID Mismatch");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not the owner of this pod");
            }

            // 4. CASCADE DELETE: Delete messages first
            try {
                podMessageService.deleteMessagesByPodId(podId);
            } catch (Exception e) {
                System.out.println("⚠️ Message cleanup warning: " + e.getMessage());
            }

            // 5. CASCADE DELETE: Delete associated posts
            try {
                postService.deletePostsByPodId(podId);
            } catch (Exception e) {
                System.out.println("⚠️ Post cleanup warning: " + e.getMessage());
            }

            // 6. Delete the Pod
            collabPodRepository.deleteById(podId);
            System.out.println("✅ DELETE SUCCESS");

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}