package com.studencollabfin.server.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.ArrayList;
import java.util.List;

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

    // ✅ NEW: Pod name (for LOOKING_FOR posts, separate from post title)
    private String podName;
}