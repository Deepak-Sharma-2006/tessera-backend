package com.studencollabfin.server.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;

/**
 * PodCooldown Model
 * 
 * Prevents 'leave/rejoin' spam by tracking cooldown periods for users.
 * Records are automatically deleted after 15 minutes using a TTL index on expiryDate.
 * 
 * Schema applies to all pod types: Team Pods, Collab Pods, and Collab Rooms
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "podCooldowns")
public class PodCooldown {
    @Id
    private String id;
    
    // User attempting to leave/rejoin
    private String userId;
    
    // Pod identifier
    private String podId;
    
    // Cooldown expiration date - TTL index auto-deletes records after 15 minutes
    @Indexed(expireAfterSeconds = 0)
    private LocalDateTime expiryDate;
    
    // Additional metadata
    private String action; // LEAVE, REJOIN, KICK (type of action that triggered cooldown)
    private LocalDateTime createdAt; // When the cooldown was created
}
