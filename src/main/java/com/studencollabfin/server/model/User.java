package com.studencollabfin.server.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Data
@Document(collection = "users")
public class User {
    @Id
    private String id;
    private String oauthId; // To store the unique ID from LinkedIn
    private String email;
    private String username; // Unique username for the user
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String fullName;
    private String collegeName;
    private String yearOfStudy;
    private String department;
    private String profilePicUrl; // To store the picture URL from LinkedIn
    private List<String> skills;
    private List<String> rolesOpenTo;
    private String goals;
    private List<String> excitingTags;
    private String linkedinUrl;
    private String githubUrl;
    private String portfolioUrl;
    private boolean profileCompleted;
    private List<String> badges;
    private int level;
    private int xp;
    private int totalXP;
}
