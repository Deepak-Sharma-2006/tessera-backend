package com.studencollabfin.server.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a Hard-Mode badge tracking record for a user.
 * Used to track progress toward badge unlock criteria.
 */
@Data
@AllArgsConstructor
@Document(collection = "hardModeBadges")
public class HardModeBadge {
    @Id
    private String id;

    private String userId;
    private String badgeId; // e.g., "discussion-architect"
    private String badgeName; // e.g., "Discussion Architect"
    private String tier; // LEGENDARY, EPIC, RARE, COMMON
    private String visualStyle; // e.g., "gold-glow", "purple-shimmer"

    private int progressCurrent = 0;
    private int progressTotal = 0;

    private boolean isUnlocked = false; // Badge criteria met
    private boolean isEquipped = false; // Badge currently active (can be false if daily limit reached)
    private LocalDateTime unlockedAt; // When the badge was first unlocked
    private LocalDateTime equippedAt; // When the badge was equipped

    // Store progress tracking info
    private Map<String, Object> progressData; // Dynamic data depending on badge type

    // For maintenance tracking
    private LocalDateTime lastCheckedAt;
    private boolean needsMaintenance = false; // Flag for badges requiring active maintenance

    public HardModeBadge() {
    }
}
