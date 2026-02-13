package com.studencollabfin.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequest {
    private String reporterId; // ID of the user submitting the report
    private String reportedUserId; // ID of the user being reported
    private String reason; // "spam", "harassment", "fake", "other"
    private String details; // Optional details
}
