package com.studencollabfin.server.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;

@Data
@Document(collection = "posts") // All post types will be stored in this single collection
public abstract class Post {

    @Id
    private String id;

    private String authorId;
    private String content; // The main text of the post

    @Indexed // ✅ NEW: Index for fast querying by creation time (used for expiry handling)
    private LocalDateTime createdAt;
    private String college; // Denormalized college name for campus isolation (e.g., "IIT", "Sinhgad")

    @Indexed // ✅ Domain-locked institutional isolation: Email domain from author's email
    private String institutionDomain; // e.g., "sinhgad.edu", "coep.ac.in"
}
