package com.studencollabfin.server.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String username;
    private String fullName;

    // ✅ ADD THESE FIELDS (Lombok @Data will auto-generate getters/setters)
    private String collegeName;
    private String yearOfStudy;
    private String department;
}
