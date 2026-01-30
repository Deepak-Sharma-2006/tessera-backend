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
    private List<String> commentIds = new ArrayList<>(); // References to comments collection
    private List<PollOption> pollOptions = new ArrayList<>();
    private PostType type = PostType.ASK_HELP;
    private String category = "CAMPUS"; // INTER or CAMPUS to distinguish where polls are shown
    // Optional fields for linking to created pods or listing required skills
    private String linkedPodId;
    private List<String> requiredSkills = new ArrayList<>();
}