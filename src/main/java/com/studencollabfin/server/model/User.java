package com.studencollabfin.server.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.ArrayList;

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
    private int endorsementsCount = 0; // Tracks skill endorsements for Skill Sage badge
    private int level = 1; // Current level in Synergy
    private int xp = 0; // Current XP towards next level
    private int totalXP = 100; // XP needed for next level
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
}
