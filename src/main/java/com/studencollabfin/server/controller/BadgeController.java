package com.studencollabfin.server.controller;

import com.studencollabfin.server.service.HardModeBadgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BadgeController - Hard-Mode Badge System Endpoints
 * Manages badge unlock, progress tracking, and retrieval.
 */
@RestController
@RequestMapping("/api/badges")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class BadgeController {

    private final HardModeBadgeService hardModeBadgeService;

    // ✅ Rate limiting: Track last request time per user to prevent rapid repeated
    // calls
    private static final Map<String, Long> LAST_BADGE_REQUEST = new ConcurrentHashMap<>();
    private static final long REQUEST_COOLDOWN_MS = 1000; // 1 second cooldown

    /**
     * GET - Get all hard-mode badges for current user with progress.
     * Auto-initializes badges if they don't exist yet.
     * ✅ Rate-limited to prevent rapid duplicate requests
     */
    @GetMapping("/hard-mode/{userId}")
    public ResponseEntity<?> getUserHardModeBadges(@PathVariable String userId) {
        try {
            // ✅ RATE LIMITING: Check if user made a request recently
            Long lastRequestTime = LAST_BADGE_REQUEST.get(userId);
            long currentTime = System.currentTimeMillis();

            if (lastRequestTime != null && (currentTime - lastRequestTime) < REQUEST_COOLDOWN_MS) {
                // Request too soon - return cached response or 429 Too Many Requests
                return ResponseEntity.status(429).body(Map.of(
                        "error", "Rate limit exceeded. Please wait before retrying.",
                        "retryAfter", REQUEST_COOLDOWN_MS));
            }

            // Update last request time
            LAST_BADGE_REQUEST.put(userId, currentTime);

            // Log only errors or slow requests (>500ms is suspicious)
            long startTime = System.currentTimeMillis();

            List<Map<String, Object>> badges = hardModeBadgeService.getUserHardModeBadges(userId);

            // ✅ Auto-initialize if no badges found
            if (badges == null || badges.isEmpty()) {
                hardModeBadgeService.initializeHardModeBadgesForUser(userId);
                badges = hardModeBadgeService.getUserHardModeBadges(userId);
            }

            long duration = System.currentTimeMillis() - startTime;
            if (duration > 500) {
                System.err.println("⚠️ [BadgeController] Slow badge request: " + duration + "ms for user " + userId);
            }

            return ResponseEntity.ok(Map.of(
                    "badges", badges != null ? badges : java.util.Collections.emptyList(),
                    "totalBadges", badges != null ? badges.size() : 0,
                    "equippedCount",
                    badges != null ? badges.stream().filter(b -> (Boolean) b.getOrDefault("isEquipped", false)).count()
                            : 0));
        } catch (Exception e) {
            System.err.println("❌ [BadgeController] Error fetching badges for user " + userId + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage(), "type", e.getClass().getSimpleName()));
        }
    }

    /**
     * POST - Try to unlock a specific badge (enforces 2-per-day limit).
     */
    @PostMapping("/hard-mode/{userId}/unlock/{badgeId}")
    public ResponseEntity<?> unlockBadge(
            @PathVariable String userId,
            @PathVariable String badgeId) {
        try {
            Map<String, Object> result = hardModeBadgeService.tryUnlockBadge(userId, badgeId);

            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else if ("pending-unlock".equals(result.get("status"))) {
                return ResponseEntity.status(429).body(result); // Too Many Requests
            } else {
                return ResponseEntity.status(400).body(result);
            }
        } catch (Exception e) {
            System.err.println("❌ [BadgeController] Error unlocking badge: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET - Get remaining unlocks for today.
     */
    @GetMapping("/hard-mode/{userId}/remaining-unlocks")
    public ResponseEntity<?> getRemainingUnlocks(@PathVariable String userId) {
        try {
            int remaining = hardModeBadgeService.getRemainUnlocksToday(userId);
            return ResponseEntity.ok(Map.of(
                    "remainingUnlocks", remaining,
                    "maxDaily", 2));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST - Track a reply action for badge progress.
     * Usage: POST /api/badges/hard-mode/{userId}/track-reply
     * Body: { "replyType": "help-needed-first-reply", "metadata": {...} }
     */
    @PostMapping("/hard-mode/{userId}/track-reply")
    public ResponseEntity<?> trackReplyAction(
            @PathVariable String userId,
            @RequestBody Map<String, Object> payload) {
        try {
            String replyType = (String) payload.get("replyType");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) payload.getOrDefault("metadata", Map.of());

            hardModeBadgeService.trackReplyAction(userId, replyType, metadata);

            return ResponseEntity.ok(Map.of("message", "Reply tracked for badge progress"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST - Track a login event.
     * Usage: POST /api/badges/hard-mode/{userId}/track-login
     */
    @PostMapping("/hard-mode/{userId}/track-login")
    public ResponseEntity<?> trackLogin(@PathVariable String userId) {
        try {
            hardModeBadgeService.trackLogin(userId);
            return ResponseEntity.ok(Map.of("message", "Login tracked"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
