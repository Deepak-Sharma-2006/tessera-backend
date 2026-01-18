package com.studencollabfin.server.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "posts") // All post types will be stored in this single collection
public abstract class Post {

    @Id
    private String id;

    private String authorId;
    private String content; // The main text of the post
    private LocalDateTime createdAt;
}
