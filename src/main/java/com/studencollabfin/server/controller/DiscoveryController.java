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
         * Get paginated global skill matches for the authenticated user.
         * 🔄 PAGINATION SUPPORT: Supports page & limit for infinite scrolling.
         * 🎯 SORTING: Consistent by similarity score (ties broken by user ID).
         * 
         * @param page        Current page (1-indexed, default 1)
         * @param limit       Results per page (default 5 for Web compatibility, max 100
         *                    for App)
         * @param currentUser Authenticated user (injected by Spring Security)
         * @return Paginated list of matching users globally (no college filter)
         */
        @GetMapping("/mesh")
        public ResponseEntity<List<User>> getGlobalMatches(
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "5") int limit,
                        @AuthenticationPrincipal User currentUser) {

                // Validate pagination parameters
                if (page < 1)
                        page = 1;
                if (limit < 1)
                        limit = 5;
                if (limit > 100)
                        limit = 100; // Cap at 100 to prevent abuse

                int offset = (page - 1) * limit;
                logger.info("📄 [PAGINATION] page={}, limit={}, offset={}", page, limit, offset);

                // Fetch all users from database
                List<User> allUsers = userRepository.findAll();
                logger.info("📊 Total users in DB: {}", allUsers.size());

                // If not authenticated, return paginated users (unsorted)
                if (currentUser == null) {
                        logger.warn("⚠️ /api/discovery/mesh - currentUser is NULL (not authenticated)");
                        List<User> paginatedUsers = allUsers.stream()
                                        .skip(offset)
                                        .limit(limit)
                                        .collect(Collectors.toList());
                        logger.info("✅ Returning {} unauthenticated candidates (page {} of ~{})",
                                        paginatedUsers.size(), page, (allUsers.size() + limit - 1) / limit);
                        return ResponseEntity.ok(paginatedUsers);
                }

                logger.info("✅ /api/discovery/mesh - Authenticated user: {} from {}", currentUser.getId(),
                                currentUser.getCollegeName());

                List<String> mySkills = currentUser.getSkills();
                logger.info("📊 Current user skills: {}", mySkills);

                // 🔄 CONSISTENT SORTING: By similarity score (descending), then by user ID
                // (ascending)
                // This ensures stable pagination across multiple requests
                List<User> sortedMatches = allUsers.stream()
                                .filter(u -> !u.getId().equals(currentUser.getId())) // Exclude self
                                .sorted((u1, u2) -> {
                                        double score1 = similarityService.calculateSimilarity(mySkills, u1.getSkills());
                                        double score2 = similarityService.calculateSimilarity(mySkills, u2.getSkills());

                                        // Primary: Sort by score (descending - highest match first)
                                        int scoreComparison = Double.compare(score2, score1);
                                        if (scoreComparison != 0)
                                                return scoreComparison;

                                        // Secondary: Sort by ID (ascending) to break ties consistently
                                        return u1.getId().compareTo(u2.getId());
                                })
                                .collect(Collectors.toList());

                logger.info("✅ Sorted {} candidates by similarity score", sortedMatches.size());

                // Apply pagination
                List<User> topMatches = sortedMatches.stream()
                                .skip(offset)
                                .limit(limit)
                                .collect(Collectors.toList());

                logger.info("✅ Returning {} matches (page {} of ~{})",
                                topMatches.size(), page, (sortedMatches.size() + limit - 1) / limit);
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
