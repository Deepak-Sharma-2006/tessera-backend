package com.studencollabfin.server.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "posts")
public class SocialPost extends Post {

    private String title;
    private List<String> likes = new ArrayList<>();
    private List<Comment> comments = new ArrayList<>();
    private List<PollOption> pollOptions = new ArrayList<>();
    private PostType type = PostType.ASK_HELP;
    // Optional fields for linking to created pods or listing required skills
    private String linkedPodId;
    private List<String> requiredSkills = new ArrayList<>();

    @Data
    public static class Comment {
        private String id = UUID.randomUUID().toString();
        private String authorName;
        private String content;
        private LocalDateTime createdAt = LocalDateTime.now();
        private List<Comment> replies = new ArrayList<>();
    }
}