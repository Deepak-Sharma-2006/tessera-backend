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
import java.util.Set;
import java.util.HashSet;
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

        /**
         * 🎯 Smart Batching for App: Infinite Recycling with Exclude List
         * Implements the "Waiting Line" (FIFO) experience for Discovery Deck.
         * Users appear in order of joining, and the deck recycles once everyone has
         * been seen.
         * 
         * POST endpoint to handle large ID lists in request body.
         * 
         * @param excludeIds List of user IDs to exclude from results
         * @param limit      Number of results to return (default 10, max 100)
         * @return List of users sorted by joinedDate (ASC) for FIFO queue behavior
         */
        @PostMapping("/mesh/batch")
        public ResponseEntity<List<User>> getDiscoveryBatch(
                        @RequestBody List<String> excludeIds,
                        @RequestParam(defaultValue = "10") int limit) {

                // Validate limit parameter
                if (limit < 1)
                        limit = 10;
                if (limit > 100)
                        limit = 100; // Cap at 100 to prevent abuse

                logger.info("🎯 [DISCOVER_BATCH] Received {} exclude IDs, limit={}", excludeIds.size(), limit);

                // Convert to Set for O(1) lookup
                Set<String> excludeSet = new HashSet<>(excludeIds);
                logger.info("📋 [DISCOVER_BATCH] Exclude Set size: {}", excludeSet.size());

                // Fetch all users and filter
                List<User> allUsers = userRepository.findAll();
                logger.info("📊 [DISCOVER_BATCH] Total users in DB: {}", allUsers.size());

                // 🔄 FIFO QUEUE LOGIC:
                // - Filter: _id MUST NOT be in excludeIds
                // - Sort: Strict by joinedDate ASC (oldest members first), then by ID ASC as
                // tie-breaker
                // - Limit: Respect the limit param
                List<User> batchCandidates = allUsers.stream()
                                .filter(u -> !excludeSet.contains(u.getId())) // Exclude users in the list
                                .sorted((u1, u2) -> {
                                        // Sort by joinedDate (ASC) - oldest users first
                                        if (u1.getJoinedDate() == null || u2.getJoinedDate() == null) {
                                                // Handle null dates by comparing IDs
                                                return u1.getId().compareTo(u2.getId());
                                        }

                                        // Primary: Compare joinedDate
                                        int dateComparison = u1.getJoinedDate().compareTo(u2.getJoinedDate());
                                        if (dateComparison != 0) {
                                                return dateComparison; // Different dates? Use date
                                        }

                                        // Tie-Breaker: Same date? Use ID as deterministic tie-breaker
                                        return u1.getId().compareTo(u2.getId());
                                })
                                .limit(limit)
                                .collect(Collectors.toList());

                // 🔍 DEBUG: Log the sorted order to verify stability
                logger.info("✅ [DISCOVER_BATCH] Final Sort Order (Deterministic):");
                for (int i = 0; i < batchCandidates.size(); i++) {
                        User user = batchCandidates.get(i);
                        logger.info("   [{}] ID: {} | Name: {} | JoinedDate: {}",
                                        i, user.getId(), user.getFullName(), user.getJoinedDate());
                }

                logger.info("✅ [DISCOVER_BATCH] Returning {} candidates (filtered from {} total)",
                                batchCandidates.size(), allUsers.size());

                return ResponseEntity.ok(batchCandidates);
        }
}
