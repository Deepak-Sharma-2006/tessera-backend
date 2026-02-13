package com.studencollabfin.server.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "comments")
public class Comment {
    @Id
    private String id;

    // Post reference and categorization
    private String postId; // The post this comment belongs to
    private String postType; // CAMPUS_POST (ask help, offer help) or DISCUSSION
    private String scope; // CAMPUS or GLOBAL

    // Comment hierarchy
    private String parentId; // null if top-level comment, else parent comment id
    private List<String> replyIds; // List of child comment ids (for fast lookup)

    // Author information
    private String authorId;
    private String authorName;

    // Content
    private String content;
    private LocalDateTime createdAt;
}
