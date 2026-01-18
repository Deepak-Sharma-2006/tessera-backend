package com.studencollabfin.server.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "applications")
public class Application {
    @Id
    private String id;
    private String beaconId; // The ID of the BuddyBeacon/TeamFindingPost being applied to
    private String applicantId;
    private List<String> applicantSkills;
    private String message;

    public enum Status {
        PENDING,
        ACCEPTED,
        REJECTED
    }

    private Status status;
    private LocalDateTime createdAt;

    // Rejection logic
    private RejectionReason rejectionReason;
    private String rejectionNote;
}
