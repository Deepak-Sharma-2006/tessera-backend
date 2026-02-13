package com.studencollabfin.server.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {
    @Id
    private String id; // Maps to MongoDB _id
    private String fullName; // e.g., "Taksh"
    private String collegeName; // e.g., "SINHGAD"
    private String yearOfStudy; // e.g., "3rd Year"
    private String department; // e.g., "Electronics"
    private List<String> skills; // e.g., ["UI/UX Design"]
    private String goals; // e.g., "sleep"
    private List<String> excitingTags; // e.g., ["Social Impact"]
    private List<String> rolesOpenTo; // e.g., ["Full Stack Developer"]
    private List<String> badges = new ArrayList<>(); // Achievement badges earned
    private List<String> displayedBadges = new ArrayList<>(); // Badges selected to display on public profile (max 3)
    private List<String> featuredBadges = new ArrayList<>(); // Badges featured in the public profile showcase (max 2)
    private int endorsementsCount = 0; // Tracks skill endorsements for Skill Sage badge
    private int postsCount = 0; // Tracks posts created for Signal Guardian badge
    private int level = 0; // Current level in Synergy (starts at 0)
    private int xp = 0; // Current XP towards next level
    private int totalXp = 0; // Total XP earned across all levels
    private double xpMultiplier = 1.0; // Prestige multiplier (increases with achievements)
    private String role = "STUDENT"; // "STUDENT" or "COLLEGE_HEAD"
    private boolean isDev = false; // Developer mode flag

    // OAuth & Authentication fields
    private String oauthId; // LinkedIn OAuth ID
    private String email;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password; // Never returned in responses

    // Additional profile fields
    private String profilePicUrl; // Profile picture from LinkedIn
    private String linkedinUrl;
    private String githubUrl;
    private String portfolioUrl;
    private boolean profileCompleted = false;

    // Meritocracy & Report System
    private int reportCount = 0; // Track number of times user has been reported
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime penaltyExpiry; // When the Spam Alert badge expires
    private boolean isBanned = false; // Ban status if reportCount >= 3

    // Hard-Mode Badge System
    private int totalReplies = 0; // Total replies across all posts
    private int weeklyReplies = 0; // Replies in current week (resets every 7 days)
    private int loginStreak = 0; // Consecutive days logged in
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate lastLoginDate; // Last date user logged in
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate lastUnlockDate; // Last date a hard-mode badge was unlocked
    private int dailyUnlocksCount = 0; // How many badges unlocked today (max 2)
    private List<String> hardModeBadgesEarned = new ArrayList<>(); // Hard-mode badges that have been earned
    private List<String> hardModeBadgesLocked = new ArrayList<>(); // Hard-mode badges awaiting unlock (blocked by daily
                                                                   // limit)
    private Map<String, Integer> statsMap = new HashMap<>(); // Tracks: helpNeededReplies, pinnedResources,
                                                             // correctPolls, etc.

    // Date fields - stored as LocalDateTime and serialized as ISO-8601
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt; // When the account was created

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime joinedDate; // Alias for createdAt for profile display
}
