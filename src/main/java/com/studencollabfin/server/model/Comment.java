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
    private String postId; // The post this comment belongs to
    private String parentId; // null if top-level comment, else parent comment id
    private String authorId;
    private String authorName;
    private String content;
    private LocalDateTime createdAt;
    private List<String> replyIds; // List of child comment ids (for fast lookup)
}
