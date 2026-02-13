package com.studencollabfin.server.controller;

import com.studencollabfin.server.dto.CampusStatsDTO;
import com.studencollabfin.server.dto.ActivityDTO;
import com.studencollabfin.server.model.User;
import com.studencollabfin.server.repository.UserRepository;
import com.studencollabfin.server.repository.CollabPodRepository;
import com.studencollabfin.server.service.UserService;
import com.studencollabfin.server.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * CampusStatsController: Provides real-time institutional statistics for Campus
 * Overview dashboard
 * 
 * Endpoints:
 * - GET /api/campus/stats: Returns total students, owned pods, and joined pods
 * for authenticated user
 */
@RestController
@RequestMapping("/api/campus")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class CampusStatsController {

    private final UserRepository userRepository;
    private final CollabPodRepository collabPodRepository;
    private final UserService userService;
    private final ActivityService activityService;

    /**
     * Get campus statistics for the authenticated user
     * Includes: Total Students in domain, My Teams (owned pods), Collaborations
     * (joined pods)
     * 
     * @param authentication Spring Security authentication token
     * @param request        HttpServletRequest for user identification
     * @return CampusStatsDTO with all statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<CampusStatsDTO> getCampusStats(Authentication authentication, HttpServletRequest request) {
        try {
            // Get current user ID
            String userId = getCurrentUserId(authentication, request);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Fetch user to get institution domain
            User currentUser = userService.getUserById(userId);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Extract institution domain from email
            String institutionDomain = extractDomainFromEmail(currentUser.getEmail());

            System.out.println("📊 [CampusStats] User: " + currentUser.getFullName());
            System.out.println("   Domain: " + institutionDomain);

            // Count total students in the institution domain by filtering all users
            List<User> allUsers = userRepository.findAll();
            long totalStudents = allUsers.stream()
                    .filter(u -> u.getEmail() != null && extractDomainFromEmail(u.getEmail()).equals(institutionDomain))
                    .count();
            System.out.println("   Total Students: " + totalStudents);

            // Count pods where user is owner (My Teams)
            long myTeams = collabPodRepository.countByOwnerId(userId);
            System.out.println("   My Teams (Owned): " + myTeams);

            // Count pods where user is member but not owner (Collaborations)
            long collaborations = collabPodRepository.countByMemberIdsContainsAndOwnerIdNot(userId, userId);
            System.out.println("   Collaborations (Joined): " + collaborations);

            CampusStatsDTO stats = new CampusStatsDTO(totalStudents, myTeams, collaborations);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            System.err.println("❌ [CampusStats] Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get recent activities for the authenticated user's campus
     * 
     * @param authentication Spring Security authentication token
     * @param request        HttpServletRequest for user identification
     * @return List of recent activities
     */
    @GetMapping("/activities")
    public ResponseEntity<List<ActivityDTO>> getRecentActivities(Authentication authentication,
            HttpServletRequest request) {
        try {
            // Get current user ID
            String userId = getCurrentUserId(authentication, request);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Fetch user to get institution domain
            User currentUser = userService.getUserById(userId);
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Extract institution domain from email
            String institutionDomain = extractDomainFromEmail(currentUser.getEmail());

            System.out.println("📋 [RecentActivities] Fetching for domain: " + institutionDomain);

            // Get recent activities from service
            List<ActivityDTO> activities = activityService.getRecentActivities(institutionDomain);

            return ResponseEntity.ok(activities);

        } catch (Exception e) {
            System.err.println("❌ [RecentActivities] Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Extract email domain from user's email address
     * Example: "sara@sinhgad.edu" → "sinhgad.edu"
     */
    private String extractDomainFromEmail(String email) {
        if (email == null || !email.contains("@"))
            return "";
        String domain = email.substring(email.indexOf("@") + 1).toLowerCase().trim();
        return domain;
    }

    /**
     * Helper method to get current user ID from authentication
     * Checks both Spring Security context and X-User-Id header
     */
    private String getCurrentUserId(Authentication authentication, HttpServletRequest request) {
        // Try Spring Security principal first
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();

            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            }

            // If principal is a string, use it directly
            if (principal instanceof String) {
                return (String) principal;
            }

            // Try getName() method
            if (authentication.getName() != null) {
                return authentication.getName();
            }
        }

        // Fallback to X-User-Id header
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.trim().isEmpty()) {
            return userId;
        }

        return null;
    }
}
