package com.studencollabfin.server.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reports")
public class Report {
    @Id
    private String id;
    private String reportedUserId; // User being reported
    private String reporterId; // User making the report
    private String reason; // "spam", "harassment", "fake", "other"
    private String details; // Optional details from reporter

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt = LocalDateTime.now();

    private boolean resolved = false; // If admin has reviewed
    private String adminNotes; // Notes from moderator
}
