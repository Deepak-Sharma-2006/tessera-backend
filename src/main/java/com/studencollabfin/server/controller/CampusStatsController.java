package com.studencollabfin.server.controller;

import com.studencollabfin.server.dto.CampusStatsDTO;
import com.studencollabfin.server.dto.CampusActivityDTO;
import com.studencollabfin.server.dto.ActivityDTO;
import com.studencollabfin.server.model.User;
import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.model.CollabPod.PodType;
import com.studencollabfin.server.model.PodScope;
import com.studencollabfin.server.model.Post;
import com.studencollabfin.server.model.SocialPost;
import com.studencollabfin.server.model.TeamFindingPost;
import com.studencollabfin.server.model.BuddyBeacon;
import com.studencollabfin.server.repository.UserRepository;
import com.studencollabfin.server.repository.CollabPodRepository;
import com.studencollabfin.server.repository.PostRepository;
import com.studencollabfin.server.repository.BuddyBeaconRepository;
import com.studencollabfin.server.service.UserService;
import com.studencollabfin.server.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.Comparator;
import java.util.stream.Collectors;

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
    private final PostRepository postRepository;
    private final BuddyBeaconRepository buddyBeaconRepository;
    private final UserService userService;
    private final ActivityService activityService;

    /**
     * Get campus statistics for the authenticated user
     * Includes: Total Students, Open Collaborations (LOOKING_FOR pods), My Teams
     * (TEAM pods where user is member)
     * Also includes latest 20 campus activities
     * 
     * @param authentication Spring Security authentication token
     * @param request        HttpServletRequest for user identification
     * @return CampusStatsDTO with all statistics and activity feed
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

            // Extract institution domain and college from email
            String institutionDomain = extractDomainFromEmail(currentUser.getEmail());
            String college = currentUser.getCollegeName();

            System.out.println("📊 [CampusStats] User: " + currentUser.getFullName());
            System.out.println("   College: " + college);
            System.out.println("   Domain: " + institutionDomain);

            // 1. Total Students in the institution (by college name)
            long totalStudents = userRepository.countByCollegeName(college);
            System.out.println("   Total Students: " + totalStudents);

            // 2. Open Collaborations (LOOKING_FOR pods with CAMPUS scope)
            List<CollabPod> campusPods = collabPodRepository.findByScopeAndCollege(PodScope.CAMPUS, college);
            long openCollaborations = campusPods.stream()
                    .filter(pod -> pod.getType() == PodType.LOOKING_FOR)
                    .count();
            System.out.println("   Open Collaborations: " + openCollaborations);

            // 3. My Teams (TEAM pods where user is member or owner)
            long myTeams = campusPods.stream()
                    .filter(pod -> pod.getType() == PodType.TEAM)
                    .filter(pod -> pod.getMemberIds() != null && pod.getMemberIds().contains(userId))
                    .count();
            System.out.println("   My Teams: " + myTeams);

            // 4. Build Activity Feed (latest 20 items)
            List<CampusActivityDTO> activityFeed = buildActivityFeed(college, institutionDomain);
            System.out.println("   Activity Feed Items: " + activityFeed.size());

            CampusStatsDTO stats = new CampusStatsDTO(totalStudents, openCollaborations, myTeams, activityFeed);
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            System.err.println("❌ [CampusStats] Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Build activity feed from various sources (pods, beacons, polls)
     * Returns latest 20 items sorted by createdAt DESC
     */
    private List<CampusActivityDTO> buildActivityFeed(String college, String institutionDomain) {
        List<CampusActivityDTO> activities = new ArrayList<>();

        try {
            // Get campus pods
            List<CollabPod> pods = collabPodRepository.findByCollege(college);
            for (CollabPod pod : pods) {
                User creator = userRepository.findById(pod.getOwnerId()).orElse(null);
                if (creator != null && pod.getCreatedAt() != null) {
                    CampusActivityDTO activity = new CampusActivityDTO();
                    activity.setType(pod.getType() == PodType.LOOKING_FOR ? "COLLAB_POD" : "TEAM_POD");
                    activity.setUsername(creator.getFullName());
                    activity.setUserId(creator.getId());
                    activity.setTitle(pod.getName());
                    activity.setCreatedAt(Date.from(pod.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()));
                    activities.add(activity);
                }
            }

            // Get polls (SocialPost with type POLL)
            List<Post> posts = postRepository.findByCollege(college);
            for (Post post : posts) {
                if (post instanceof SocialPost) {
                    SocialPost socialPost = (SocialPost) post;
                    if (socialPost.getType() != null && "POLL".equals(socialPost.getType().name())) {
                        User author = userRepository.findById(socialPost.getAuthorId()).orElse(null);
                        if (author != null && socialPost.getCreatedAt() != null) {
                            CampusActivityDTO activity = new CampusActivityDTO();
                            activity.setType("POLL");
                            activity.setUsername(author.getFullName());
                            activity.setUserId(author.getId());
                            activity.setTitle(
                                    socialPost.getTitle() != null ? socialPost.getTitle() : socialPost.getContent());
                            activity.setCreatedAt(
                                    Date.from(socialPost.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()));
                            activities.add(activity);
                        }
                    }
                }
            }

            // Get buddy beacons (filter by college if beacon has creator info)
            List<BuddyBeacon> beacons = buddyBeaconRepository.findAll();
            for (BuddyBeacon beacon : beacons) {
                User creator = userRepository.findById(beacon.getAuthorId()).orElse(null);
                if (creator != null && college.equals(creator.getCollegeName()) && beacon.getCreatedAt() != null) {
                    CampusActivityDTO activity = new CampusActivityDTO();
                    activity.setType("BEACON");
                    activity.setUsername(creator.getFullName());
                    activity.setUserId(creator.getId());
                    activity.setTitle(null); // Beacons don't have titles
                    activity.setCreatedAt(Date.from(beacon.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()));
                    activities.add(activity);
                }
            }

            // Sort by createdAt DESC (newest first)
            activities.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

            // Return latest 20
            return activities.stream().limit(20).collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("❌ [ActivityFeed] Error: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
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
