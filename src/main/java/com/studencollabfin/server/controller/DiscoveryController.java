package com.studencollabfin.server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.studencollabfin.server.repository.UserRepository;
import com.studencollabfin.server.model.User;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/discovery")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class DiscoveryController {

        @Autowired
        private UserRepository userRepository;

        @GetMapping("/radar/{userId}")
        @SuppressWarnings("null")
        public ResponseEntity<List<Map<String, Object>>> getRadarData(@PathVariable String userId) {
                // 1. Get current user from local DB
                User currentUser = userRepository.findById((String) userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                // 2. Logic: Different college, shared skills, limited to top 8
                List<User> potentialMatches = userRepository.findAll().stream()
                                .filter(u -> !u.getId().equals(userId))
                                .filter(u -> u.getCollegeName() != null
                                                && !u.getCollegeName().equalsIgnoreCase(currentUser.getCollegeName()))
                                .filter(u -> u.getSkills() != null
                                                && u.getSkills().stream().anyMatch(currentUser.getSkills()::contains))
                                .limit(8)
                                .collect(Collectors.toList());

                // 3. Map to DTO with Match Percentage
                List<Map<String, Object>> radarData = potentialMatches.stream().map(match -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("user", match);

                        long sharedCount = match.getSkills().stream()
                                        .filter(currentUser.getSkills()::contains)
                                        .count();

                        // Avoid division by zero if current user has no skills
                        double skillsSize = currentUser.getSkills().isEmpty() ? 1.0 : currentUser.getSkills().size();
                        double score = ((double) sharedCount / skillsSize) * 100;
                        map.put("score", Math.round(score));
                        return map;
                }).collect(Collectors.toList());

                return ResponseEntity.ok(radarData);
        }
}
