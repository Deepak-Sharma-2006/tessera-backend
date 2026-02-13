package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.*;
import com.studencollabfin.server.service.BuddyBeaconService;
import com.studencollabfin.server.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/beacon")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class BuddyBeaconController {

    @Autowired
    private BuddyBeaconService beaconService;

    @Autowired
    private UserService userService;

    // Get the currently logged-in user's ID from JWT authentication context or
    // request header
    private String getCurrentUserId(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }

        // Fallback: Extract from request header (X-User-Id)
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String userIdHeader = request.getHeader("X-User-Id");
                if (userIdHeader != null && !userIdHeader.isEmpty()) {
                    return userIdHeader;
                }
            }
        } catch (Exception e) {
            // Continue to null return
        }

        return null;
    }

    // --- Beacon Post Endpoints ---

    @PostMapping
    public BuddyBeacon createBeaconPost(@RequestBody BuddyBeacon beaconPost, Authentication authentication) {
        String userId = getCurrentUserId(authentication);
        return beaconService.createBeaconPost(userId, beaconPost);
    }

    /**
     * Campus Feed: Aggregates BuddyBeacon and TeamFindingPost posts for current
     * user's college.
     * ✅ Campus Isolation: Only returns posts from the current user's college.
     */
    @GetMapping("/feed")
    public List<Map<String, Object>> getAllBeaconPosts(Authentication authentication) {
        String userId = getCurrentUserId(authentication);
        String userCollege = null;

        // Fetch current user's college
        if (userId != null && !userId.trim().isEmpty()) {
            try {
                User currentUser = userService.getUserById(userId);
                if (currentUser != null && currentUser.getCollegeName() != null) {
                    userCollege = currentUser.getCollegeName();
                    System.out.println("✅ Fetching Buddy Beacon feed for college: " + userCollege);
                }
            } catch (Exception ex) {
                System.err.println("⚠️ Error fetching current user: " + ex.getMessage());
            }
        }

        if (userCollege == null || userCollege.trim().isEmpty()) {
            System.out.println("⚠️ User college is null/empty, returning empty Buddy Beacon feed");
            return new ArrayList<>();
        }

        return beaconService.getAllBeaconPosts(userId, userCollege);
    }

    /**
     * Applied Posts: Returns posts the user has applied to, with status and post
     * details.
     */
    @GetMapping("/applied-posts")
    public List<Map<String, Object>> getAppliedPosts(Authentication authentication) {
        String userId = getCurrentUserId(authentication);
        return beaconService.getAppliedPosts(userId);
    }

    /**
     * My Posts: Returns posts created by the authenticated user, with applicants
     * and their profiles.
     */
    @GetMapping("/my-posts")
    public List<Map<String, Object>> getMyPosts(Authentication authentication) {
        String userId = getCurrentUserId(authentication);
        try {
            return beaconService.getMyPosts(userId);
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // --- Application Endpoints ---

    @PostMapping("/{beaconId}/applications")
    public Application applyToBeaconPost(@PathVariable String beaconId, @RequestBody Application application,
            Authentication authentication) {
        String userId = getCurrentUserId(authentication);
        return beaconService.applyToBeaconPost(beaconId, userId, application);
    }

    // Accept ApplyRequest DTO with message in body for canonical endpoint
    @PostMapping("/apply/{beaconId}")
    public ResponseEntity<?> applyToBeaconWithMessage(@PathVariable String beaconId,
            @RequestBody(required = false) com.studencollabfin.server.dto.ApplyRequest request,
            Authentication authentication) {
        String userId = getCurrentUserId(authentication);
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
    public Application acceptApplication(@PathVariable String applicationId, @RequestParam String postId,
            Authentication authentication) {
        String userId = getCurrentUserId(authentication);
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
            Authentication authentication) {
        String userId = getCurrentUserId(authentication);
        return beaconService.rejectApplication(postId, applicationId, userId, reason, note);
    }

    /**
     * Delete a post (host only, only if EXPIRED or CLOSED).
     */
    @DeleteMapping("/my-posts/{postId}")
    public ResponseEntity<?> deleteMyPost(@PathVariable String postId, Authentication authentication) {
        String userId = getCurrentUserId(authentication);
        beaconService.deleteMyPost(postId, userId);
        return ResponseEntity.noContent().build();
    }
}
