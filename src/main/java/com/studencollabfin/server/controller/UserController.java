package com.studencollabfin.server.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studencollabfin.server.config.JwtUtil;
import com.studencollabfin.server.dto.UpdateProfileRequest;
import com.studencollabfin.server.model.Achievement;
import com.studencollabfin.server.model.User;
import com.studencollabfin.server.service.UserService;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:5173")
@SuppressWarnings("null")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    // ✅ EXISTING: Get Profile
    @GetMapping("/{userId}")
    public ResponseEntity<?> getProfile(@PathVariable String userId) {
        try {
            User user = userService.findById(userId);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ✅ EXISTING: Update User by ID (Fallback)
    @PutMapping("/{userId}")
    public ResponseEntity<?> updateUserProfile(@PathVariable String userId, @RequestBody User profileData) {
        try {
            User updatedUser = userService.updateUserProfile(userId, profileData);
            return ResponseEntity.ok(updatedUser);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 🔥 DEBUG VERSION: Update Profile (Main)
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UpdateProfileRequest request) {

        // 1. LOG ENTRY: If you see this in the terminal, the Security Config is OPEN ✅
        System.out.println("🔔 HIT: /api/users/profile endpoint reached!");

        if (userDetails == null) {
            System.out.println("❌ ERROR: UserDetails is null. Auth Filter failed.");
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }

        String email = userDetails.getUsername();
        System.out.println("👤 AUTH USER: " + email);

        try {
            User user = userService.findByEmail(email);
            if (user == null) {
                System.out.println("❌ ERROR: User not found in DB.");
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            System.out
                    .println("📦 PAYLOAD: College=" + request.getCollegeName() + ", Year=" + request.getYearOfStudy());

            // 2. UPDATE FIELDS (Clean Logic)
            if (request.getFullName() != null)
                user.setFullName(request.getFullName());
            if (request.getCollegeName() != null)
                user.setCollegeName(request.getCollegeName());
            if (request.getYearOfStudy() != null)
                user.setYearOfStudy(request.getYearOfStudy());
            if (request.getDepartment() != null)
                user.setDepartment(request.getDepartment());

            // Only update username if actually changed (prevents conflict errors)
            if (request.getUsername() != null && !request.getUsername().isEmpty()
                    && !request.getUsername().equals(user.getUsername())) {
                if (!userService.usernameExists(request.getUsername())) {
                    user.setUsername(request.getUsername());
                }
            }

            // 3. SAVE
            User updatedUser = userService.updateUser(user);
            System.out.println("✅ SUCCESS: Profile updated in DB.");
            return ResponseEntity.ok(updatedUser);

        } catch (Exception e) {
            System.out.println("❌ EXCEPTION: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ✅ EXISTING: Gamification Endpoints
    @GetMapping("/{userId}/xp")
    public ResponseEntity<?> getUserProgress(@PathVariable String userId) {
        try {
            User user = userService.findById(userId);
            return ResponseEntity.ok(Map.of(
                    "currentXP", user.getXp(),
                    "level", user.getLevel(),
                    "nextLevelXP", user.getTotalXP()));
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
}