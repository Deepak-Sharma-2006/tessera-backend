package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.User;
import com.studencollabfin.server.model.Achievement;
import com.studencollabfin.server.model.XPAction;
import com.studencollabfin.server.service.UserService;
import com.studencollabfin.server.service.AchievementService;
import com.studencollabfin.server.service.GamificationService;
import com.studencollabfin.server.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private AchievementService achievementService;

    @Autowired
    private GamificationService gamificationService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/{userId}")
    public ResponseEntity<?> getProfile(@PathVariable String userId) {
        try {
            User user = userService.findById(userId);
            
            // ✅ CRITICAL: Sync all badges based on current user attributes
            User syncedUser = achievementService.syncUserBadges(user);
            
            return ResponseEntity.ok(syncedUser);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{userId}")
    public ResponseEntity<?> updateUserProfile(@PathVariable String userId, @RequestBody User profileData) {
        try {
            User updatedUser = userService.updateUserProfile(userId, profileData);

            // ✅ CRITICAL: Sync all badges after profile update
            User syncedUser = achievementService.syncUserBadges(updatedUser);

            return ResponseEntity.ok(syncedUser);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{userId}/xp")
    public ResponseEntity<?> getUserProgress(@PathVariable String userId) {
        try {
            User user = userService.findById(userId);
            return ResponseEntity.ok(Map.of(
                    "currentXP", user.getXp(),
                    "level", user.getLevel(),
                    "nextLevelXP", 100 - user.getXp()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{userId}/achievements")
    public ResponseEntity<?> getUserAchievements(@PathVariable String userId) {
        try {
            List<Achievement> achievements = userService.getUserAchievements(userId);
            return ResponseEntity.ok(achievements);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{userId}/grant-catalyst")
    public ResponseEntity<?> grantCatalyst(@PathVariable String userId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        try {
            // Get current user (must be developer)
            User currentUser = null;
            if (currentUserId != null && !currentUserId.isEmpty()) {
                currentUser = userRepository.findById(currentUserId).orElse(null);
            }

            // Only a Developer can promote others
            if (currentUser == null || !currentUser.isDev()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Access Denied: Only developers can grant badges");
            }

            // Get target user and promote to COLLEGE_HEAD
            User targetUser = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
            targetUser.setRole("COLLEGE_HEAD");

            // Add badge to user's list for frontend visibility
            if (targetUser.getBadges() == null) {
                targetUser.setBadges(new ArrayList<>());
            }
            if (!targetUser.getBadges().contains("Campus Catalyst")) {
                targetUser.getBadges().add("Campus Catalyst");
            }

            userRepository.save(targetUser);
            achievementService.unlockAchievement(userId, "Campus Catalyst");

            return ResponseEntity.ok(Map.of(
                    "message", "User promoted to Campus Catalyst",
                    "user", targetUser));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/activate-dev")
    public ResponseEntity<?> activateDev(@RequestHeader(value = "Authorization") String authHeader) {
        try {
            // Extract user ID from token or get from context
            // For now, we'll use a simpler approach: get from authenticated request
            // In a real app, you'd extract the user ID from the JWT token

            // This endpoint allows users to activate developer mode via the secret 5-click
            // pattern
            // We need to get the current user - typically from JWT token in Authorization
            // header
            // For this implementation, we'll return a message that this needs proper
            // authentication

            // Note: In production, you should extract the user ID from the JWT token
            // For now, we'll provide a placeholder that requires the frontend to pass user
            // context

            return ResponseEntity.badRequest().body("Use X-User-Id header with your user ID");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/{userId}/activate-dev")
    public ResponseEntity<?> activateDevForUser(@PathVariable String userId) {
        try {
            // Get user and set isDev to true
            User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
            user.setDev(true);

            // Add Founding Dev badge if not already present
            if (user.getBadges() == null) {
                user.setBadges(new ArrayList<>());
            }
            if (!user.getBadges().contains("Founding Dev")) {
                user.getBadges().add("Founding Dev");
            }

            userRepository.save(user);
            achievementService.unlockAchievement(userId, "Founding Dev");

            return ResponseEntity.ok(Map.of(
                    "message", "Developer mode activated",
                    "user", user));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    // ============ NEW PROFILE & ENDORSEMENT ENDPOINTS ============

    // Update Profile - Dynamic Synergy Profile Updates
    @PutMapping("/{userId}/profile")
    public ResponseEntity<User> updateProfile(@PathVariable String userId, @RequestBody User updates) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Update all profile fields dynamically from the request
            if (updates.getFullName() != null)
                user.setFullName(updates.getFullName());
            if (updates.getCollegeName() != null)
                user.setCollegeName(updates.getCollegeName());
            if (updates.getDepartment() != null)
                user.setDepartment(updates.getDepartment());
            if (updates.getYearOfStudy() != null)
                user.setYearOfStudy(updates.getYearOfStudy());
            if (updates.getGoals() != null)
                user.setGoals(updates.getGoals());
            if (updates.getSkills() != null)
                user.setSkills(updates.getSkills());
            if (updates.getExcitingTags() != null)
                user.setExcitingTags(updates.getExcitingTags());
            if (updates.getRolesOpenTo() != null)
                user.setRolesOpenTo(updates.getRolesOpenTo());

            User updatedUser = userRepository.save(user);

            // Check if profile is now complete for Profile Pioneer achievement
            if (isProfileComplete(user)) {
                achievementService.unlockAchievement(userId, "Profile Pioneer");
            }

            return ResponseEntity.ok(updatedUser);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Endorse User - Skill Endorsement System
    @PostMapping("/{userId}/endorse")
    public ResponseEntity<User> endorseUser(@PathVariable String userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Increment endorsement count
            user.setEndorsementsCount(user.getEndorsementsCount() + 1);

            // 📊 GAMIFICATION: Award XP for receiving endorsement
            gamificationService.awardXp(userId, XPAction.RECEIVE_ENDORSEMENT);

            user = userRepository.save(user);
            
            // ✅ SYNC BADGES: Check if endorsement count triggered any badges
            User syncedUser = achievementService.syncUserBadges(user);
            
            return ResponseEntity.ok(syncedUser);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ============ BADGE SYNCHRONIZATION ============

    /**
     * ✅ SYNC-BADGES ENDPOINT
     * Calls AchievementService.syncUserBadges to update all badges based on user attributes
     */
    @PostMapping("/{userId}/sync-badges")
    public ResponseEntity<?> syncBadges(@PathVariable String userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // ✅ ATTRIBUTE-DRIVEN SYNC: All badge logic consolidated in AchievementService
            User syncedUser = achievementService.syncUserBadges(user);

            return ResponseEntity.ok(syncedUser);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    // ============ BADGE DISPLAY MANAGEMENT ============

    @PostMapping("/{userId}/displayed-badges")
    public ResponseEntity<?> updateDisplayedBadges(@PathVariable String userId,
            @RequestBody Map<String, List<String>> request) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<String> badgesToDisplay = request.get("badges");

            // Validate: user can only display badges they have earned
            if (badgesToDisplay != null) {
                for (String badge : badgesToDisplay) {
                    if (!user.getBadges().contains(badge)) {
                        return ResponseEntity.badRequest()
                                .body("Cannot display badge you haven't earned: " + badge);
                    }
                }

                // Limit to 3 badges max (except special badges like Signal Guardian, Mod Badge,
                // Penalty badges)
                // For now, allow any 3 earned badges
                if (badgesToDisplay.size() > 3) {
                    return ResponseEntity.badRequest()
                            .body("You can display a maximum of 3 badges");
                }

                user.setDisplayedBadges(badgesToDisplay);
            }

            User updatedUser = userRepository.save(user);
            return ResponseEntity.ok(updatedUser);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    // ============ HELPER METHODS ============

    private boolean isProfileComplete(User user) {
        return user.getFullName() != null && !user.getFullName().isEmpty() &&
                user.getCollegeName() != null && !user.getCollegeName().isEmpty() &&
                user.getYearOfStudy() != null && !user.getYearOfStudy().isEmpty() &&
                user.getDepartment() != null && !user.getDepartment().isEmpty() &&
                user.getSkills() != null && !user.getSkills().isEmpty() &&
                user.getRolesOpenTo() != null && !user.getRolesOpenTo().isEmpty() &&
                user.getGoals() != null && !user.getGoals().isEmpty();
    }
}
