package com.studencollabfin.server.dto;

import lombok.Data;
import java.util.List;

@Data
public class ProfileUpdateRequest {
    private String fullName;
    private String collegeName;
    private String yearOfStudy;
    private String department;
    private String goals;
    private List<String> skills;
    private List<String> excitingTags;
    private List<String> rolesOpenTo;
}
