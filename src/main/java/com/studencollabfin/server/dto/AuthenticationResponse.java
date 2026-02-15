package com.studencollabfin.server.dto;

import lombok.Data;
import java.util.List;

@Data
public class AuthenticationResponse {
    private String token;
    private String userId;
    private String email;
    private String fullName;
    private boolean profileCompleted;
    private String role; // ✅ NEW: Role field for admin detection

    // ✅ CRITICAL FIX: Include collegeName and badges in auth response
    private String collegeName;
    private List<String> badges;

    public AuthenticationResponse(String token, String userId, String email, String fullName,
            boolean profileCompleted) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.profileCompleted = profileCompleted;
        this.role = "STUDENT"; // Default role
    }

    public AuthenticationResponse(String token, String userId, String email, String fullName, boolean profileCompleted,
            String collegeName, List<String> badges) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.profileCompleted = profileCompleted;
        this.collegeName = collegeName;
        this.badges = badges;
        this.role = "STUDENT"; // Default role
    }

    // ✅ NEW: Constructor with role for admin users
    public AuthenticationResponse(String token, String userId, String email, String fullName, boolean profileCompleted,
            String collegeName, List<String> badges, String role) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.profileCompleted = profileCompleted;
        this.collegeName = collegeName;
        this.badges = badges;
        this.role = role;
    }
}