package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.User;
import com.studencollabfin.server.repository.UserRepository;
import com.studencollabfin.server.service.SkillSimilarityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Global Discovery Mesh Controller
 * Implements the "Global Hub" logic for skill-based matching across all
 * colleges.
 * Uses Jaccard Similarity to rank potential collaborators.
 */
@RestController
@RequestMapping("/api/discovery")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class DiscoveryController {

        private static final Logger logger = LoggerFactory.getLogger(DiscoveryController.class);

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private SkillSimilarityService similarityService;

        /**
         * Get top 5 global skill matches for the authenticated user.
         * Uses Jaccard similarity to rank candidates across ALL colleges.
         * 
         * @param currentUser Authenticated user (injected by Spring Security)
         * @return Top 5 matching users globally (no college filter)
         */
        @GetMapping("/mesh")
        public ResponseEntity<List<User>> getGlobalMatches(@AuthenticationPrincipal User currentUser) {
                // Fetch all users from database
                List<User> allUsers = userRepository.findAll();
                logger.info("📊 Total users in DB: {}", allUsers.size());

                // If not authenticated, return all users except first (unsorted)
                if (currentUser == null) {
                        logger.warn("⚠️ /api/discovery/mesh - currentUser is NULL (not authenticated) - returning all {} users",
                                        allUsers.size());
                        // Return all users (no self-filtering since we don't know who 'self' is)
                        return ResponseEntity.ok(allUsers.stream().limit(5).collect(Collectors.toList()));
                }

                logger.info("✅ /api/discovery/mesh - Authenticated user: {} from {}", currentUser.getId(),
                                currentUser.getCollegeName());

                List<String> mySkills = currentUser.getSkills();
                logger.info("📊 Current user skills: {}", mySkills);

                List<User> topMatches = allUsers.stream()
                                .filter(u -> !u.getId().equals(currentUser.getId())) // Exclude self
                                .peek(u -> logger.debug("Comparing with user: {} from {}", u.getId(),
                                                u.getCollegeName()))
                                .sorted((u1, u2) -> {
                                        double score1 = similarityService.calculateSimilarity(mySkills, u1.getSkills());
                                        double score2 = similarityService.calculateSimilarity(mySkills, u2.getSkills());
                                        logger.debug("Similarity scores: {} -> {}, {} -> {}", u1.getId(), score1,
                                                        u2.getId(), score2);
                                        return Double.compare(score2, score1); // Descending order (highest matches
                                                                               // first)
                                })
                                .limit(5) // Top 5 students globally
                                .collect(Collectors.toList());

                logger.info("✅ Returning {} top matches", topMatches.size());
                return ResponseEntity.ok(topMatches);
        }

        /**
         * Legacy endpoint: Radar data for backward compatibility.
         * Kept for now but marked for deprecation.
         */
        @GetMapping("/radar/{userId}")
        @Deprecated
        public ResponseEntity<List<User>> getRadarData(@PathVariable String userId) {
                @SuppressWarnings("null")
                User currentUser = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                // Delegate to new mesh endpoint logic
                List<String> mySkills = currentUser.getSkills();
                List<User> matches = userRepository.findAll().stream()
                                .filter(u -> !u.getId().equals(userId))
                                .sorted((u1, u2) -> {
                                        double score1 = similarityService.calculateSimilarity(mySkills, u1.getSkills());
                                        double score2 = similarityService.calculateSimilarity(mySkills, u2.getSkills());
                                        return Double.compare(score2, score1);
                                })
                                .limit(8)
                                .collect(Collectors.toList());

                return ResponseEntity.ok(matches);
        }
}
