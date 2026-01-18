package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.*;
import com.studencollabfin.server.service.BuddyBeaconService;
import com.studencollabfin.server.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.*;

@RestController
@RequestMapping("/api/beacon") // Changed to /api/beacon to match the frontend
public class BuddyBeaconController {

    @Autowired
    private BuddyBeaconService beaconService;

    @Autowired
    private UserService userService;

    /**
     * Helper method to extract real user ID from authentication principal.
     */
    private String getUserIdFromAuth(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("Unauthorized");
        }
        User user = userService.findByEmail(userDetails.getUsername());
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        return user.getId();
    }

    // --- Beacon Post Endpoints ---

    @PostMapping
    public BuddyBeacon createBeaconPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody BuddyBeacon beaconPost) {
        String userId = getUserIdFromAuth(userDetails);
        return beaconService.createBeaconPost(userId, beaconPost);
    }

    /**
     * Global feed: Aggregates BuddyBeacon and TeamFindingPost posts.
     */
    @GetMapping("/feed")
    public List<Map<String, Object>> getAllBeaconPosts(
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserIdFromAuth(userDetails);
        return beaconService.getAllBeaconPosts(userId);
    }

    /**
     * Applied Posts: Returns posts the user has applied to, with status and post
     * details.
     */
    @GetMapping("/applied")
    public List<Map<String, Object>> getAppliedPosts(
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserIdFromAuth(userDetails);
        return beaconService.getAppliedPosts(userId);
    }

    /**
     * My Posts: Returns posts created by the authenticated user, with applicants
     * and their profiles.
     */
    @GetMapping("/my-posts")
    public List<Map<String, Object>> getMyPosts(
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserIdFromAuth(userDetails);
        return beaconService.getMyPosts(userId);
    }

    // --- Application Endpoints ---

    @PostMapping("/{beaconId}/applications")
    public Application applyToBeaconPost(
            @PathVariable String beaconId,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Application application) {
        String userId = getUserIdFromAuth(userDetails);
        return beaconService.applyToBeaconPost(beaconId, userId, application);
    }

    // Accept ApplyRequest DTO with message in body for canonical endpoint
    @PostMapping("/apply/{beaconId}")
    public ResponseEntity<?> applyToBeaconWithMessage(
            @PathVariable String beaconId,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody(required = false) com.studencollabfin.server.dto.ApplyRequest request) {
        String userId = getUserIdFromAuth(userDetails);
        Application application = new Application();
        if (request != null)
            application.setMessage(request.getMessage());
        Application saved = beaconService.applyToBeaconPost(beaconId, userId, application);
        return ResponseEntity.ok(saved);
    }

    // Note: legacy /api/apply/{id} is handled by LegacyApplyController to avoid
    // ambiguous mappings in this controller.

    /**
     * Accept an application (host only).
     */
    @PostMapping("/application/{applicationId}/accept")
    public Application acceptApplication(
            @PathVariable String applicationId,
            @RequestParam String postId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserIdFromAuth(userDetails);
        return beaconService.acceptApplication(postId, applicationId, userId);
    }

    /**
     * Reject an application (host only, with reason and optional note).
     */
    @PostMapping("/application/{applicationId}/reject")
    public Application rejectApplication(
            @PathVariable String applicationId,
            @RequestParam String postId,
            @RequestParam RejectionReason reason,
            @RequestParam(required = false) String note,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserIdFromAuth(userDetails);
        return beaconService.rejectApplication(postId, applicationId, userId, reason, note);
    }

    /**
     * Delete a post (host only, only if EXPIRED or CLOSED).
     */
    @DeleteMapping("/my-posts/{postId}")
    public ResponseEntity<?> deleteMyPost(
            @PathVariable String postId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = getUserIdFromAuth(userDetails);
        beaconService.deleteMyPost(postId, userId);
        return ResponseEntity.noContent().build();
    }
}
