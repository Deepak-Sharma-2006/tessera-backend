package com.studencollabfin.server.dto;

import lombok.Data;

@Data
public class AuthenticationResponse {
    private String token;
    private String userId;
    private String email;
    private String fullName;
    private boolean profileCompleted;

    public AuthenticationResponse(String token, String userId, String email, String fullName, boolean profileCompleted) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.profileCompleted = profileCompleted;
    }
}

