package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.Post;
import com.studencollabfin.server.model.SocialPost;
import com.studencollabfin.server.model.TeamFindingPost;
import com.studencollabfin.server.model.XPAction;
import com.studencollabfin.server.service.PostService;
import com.studencollabfin.server.service.UserService;
import com.studencollabfin.server.service.GamificationService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import com.studencollabfin.server.dto.CommentRequest;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class PostController {

    private final PostService postService;
    private final GamificationService gamificationService;
    private final MongoTemplate mongoTemplate;
    private final UserService userService;

    @PutMapping("/{postId}/like")
    public ResponseEntity<SocialPost> toggleLike(@PathVariable String postId, Authentication authentication,
            HttpServletRequest request) {
        String userId = getCurrentUserId(authentication, request);
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(postService.toggleLike(postId, userId));
    }

    // Poll voting endpoint
    @PutMapping("/{postId}/vote/{optionId}")
    public ResponseEntity<Post> voteOnPollOption(@PathVariable String postId, @PathVariable String optionId,
            Authentication authentication, HttpServletRequest request) {
        String userId = getCurrentUserId(authentication, request);
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Post updatedPost = postService.voteOnPollOption(postId, optionId, userId);
        return ResponseEntity.ok(updatedPost);
    }

    // Helper method to add author details to richPost
    private void addAuthorDetailsToPost(java.util.Map<String, Object> richPost, String authorId) {
        try {
            com.studencollabfin.server.model.User user = userService.getUserById(authorId);
            if (user != null) {
                richPost.put("authorName", user.getFullName() != null ? user.getFullName() : "Unknown User");
                richPost.put("authorCollege", user.getCollegeName() != null ? user.getCollegeName() : "");
                richPost.put("authorYear", user.getYearOfStudy() != null ? user.getYearOfStudy() : "");
            } else {
                richPost.put("authorName", "Unknown User");
                richPost.put("authorCollege", "");
                richPost.put("authorYear", "");
            }
        } catch (Exception e) {
            // If user fetch fails, use defaults
            richPost.put("authorName", "Unknown User");
            richPost.put("authorCollege", "");
            richPost.put("authorYear", "");
        }
    }

    // Extracts user ID from Authentication or X-User-Id header
    private String getCurrentUserId(Authentication authentication, HttpServletRequest request) {
        // Try Authentication object first (Spring Security)
        if (authentication != null && authentication.getPrincipal() != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            }
            return principal.toString();
        }

        // Fall back to X-User-Id header
        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.trim().isEmpty()) {
            return userId;
        }

        // Default fallback
        return null;
    }

    @GetMapping
    public ResponseEntity<List<Object>> getAllPosts(@RequestParam(required = false) String type) {
        List<Post> posts = postService.getAllPosts();
        // If a type filter was provided, attempt to filter by PostType enum
        if (type != null && !type.isBlank()) {
            try {
                com.studencollabfin.server.model.PostType ptype = com.studencollabfin.server.model.PostType
                        .valueOf(type);
                java.util.List<Post> filtered = new java.util.ArrayList<>();
                for (Post post : posts) {
                    if (post instanceof com.studencollabfin.server.model.SocialPost) {
                        com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                        if (social.getType() == ptype)
                            filtered.add(post);
                    } else if (ptype == com.studencollabfin.server.model.PostType.LOOKING_FOR
                            && post instanceof com.studencollabfin.server.model.TeamFindingPost) {
                        filtered.add(post);
                    }
                }
                posts = filtered;
            } catch (IllegalArgumentException ex) {
                // unknown type - ignore and return all
            }
        }
        List<Object> richPosts = new java.util.ArrayList<>();
        for (Post post : posts) {
            java.util.Map<String, Object> richPost = new java.util.HashMap<>();
            richPost.put("id", post.getId());
            richPost.put("authorId", post.getAuthorId());
            richPost.put("createdAt", post.getCreatedAt() != null ? post.getCreatedAt().toString() : "");

            // Add author details (name, college, year)
            addAuthorDetailsToPost(richPost, post.getAuthorId());

            if (post instanceof com.studencollabfin.server.model.SocialPost) {
                com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                richPost.put("title", social.getTitle() != null ? social.getTitle() : "");
                richPost.put("content", social.getContent());
                richPost.put("type", social.getType() != null ? social.getType().name() : "");
                richPost.put("postType", social.getType() != null ? social.getType().name() : "");
                richPost.put("likes", social.getLikes() != null ? social.getLikes() : new java.util.ArrayList<>());
                richPost.put("commentIds",
                        social.getCommentIds() != null ? social.getCommentIds() : new java.util.ArrayList<>());
                richPost.put("pollOptions",
                        social.getPollOptions() != null ? social.getPollOptions() : new java.util.ArrayList<>());
                richPost.put("requiredSkills",
                        social.getRequiredSkills() != null ? social.getRequiredSkills() : new java.util.ArrayList<>());
                richPost.put("linkedPodId", social.getLinkedPodId() != null ? social.getLinkedPodId() : "");
            } else if (post instanceof com.studencollabfin.server.model.TeamFindingPost) {
                com.studencollabfin.server.model.TeamFindingPost team = (com.studencollabfin.server.model.TeamFindingPost) post;
                richPost.put("title", team.getContent());
                richPost.put("content", team.getContent());
                richPost.put("type", "LOOKING_FOR");
                richPost.put("postType", "LOOKING_FOR");
                richPost.put("requiredSkills",
                        team.getRequiredSkills() != null ? team.getRequiredSkills() : new java.util.ArrayList<>());
                richPost.put("maxTeamSize", team.getMaxTeamSize());
                richPost.put("currentTeamMembers", team.getCurrentTeamMembers() != null ? team.getCurrentTeamMembers()
                        : new java.util.ArrayList<>());
            } else {
                richPost.put("title", post.getContent());
                richPost.put("content", post.getContent());
                richPost.put("type", "GENERAL");
                richPost.put("postType", "GENERAL");
            }
            richPosts.add(richPost);
        }
        return ResponseEntity.ok(richPosts);
    }

    @PostMapping("/social")
    public ResponseEntity<?> createSocialPost(@RequestBody SocialPost socialPost, Authentication authentication,
            HttpServletRequest request) {
        try {
            System.out.println("Incoming SocialPost: " + socialPost.getTitle());
            System.out.println("Type: " + socialPost.getType());
            System.out.println("Content: " + socialPost.getContent());
            String userId = getCurrentUserId(authentication, request);
            System.out.println("Author ID: " + userId);
            Post createdPost = postService.createPost(socialPost, userId);
            System.out.println("Post created successfully with ID: " + createdPost.getId());

            // 📊 GAMIFICATION: Award XP for creating a post
            gamificationService.awardXp(userId, XPAction.CREATE_POST);

            return ResponseEntity.status(HttpStatus.CREATED).body(createdPost);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error creating post: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/team-finding")
    public ResponseEntity<Post> createTeamFindingPost(@RequestBody TeamFindingPost teamFindingPost,
            Authentication authentication, HttpServletRequest request) {
        String userId = getCurrentUserId(authentication, request);

        // Log for debugging
        System.out.println("[DEBUG] Creating team post - userId: " + userId);
        System.out.println("[DEBUG] Authentication: " + authentication);
        String headerUserId = request.getHeader("X-User-Id");
        System.out.println("[DEBUG] X-User-Id header: " + headerUserId);

        // Fallback: if userId is still null, generate a temporary one
        if (userId == null || userId.trim().isEmpty()) {
            userId = "user-" + System.currentTimeMillis();
            System.out.println("[DEBUG] Using fallback userId: " + userId);
        }

        Post createdPost = postService.createPost(teamFindingPost, userId);

        // 📊 GAMIFICATION: Award XP for creating a post
        gamificationService.awardXp(userId, XPAction.CREATE_POST);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdPost);
    }

    // Separate endpoints for Campus Feed posts
    @GetMapping("/campus")
    public ResponseEntity<List<Object>> getCampusPosts(@RequestParam(required = false) String type) {
        try {
            List<Post> posts = postService.getAllPosts();
            System.out.println("Total posts from DB: " + posts.size());

            java.util.List<com.studencollabfin.server.model.PostType> campusTypes = java.util.Arrays.asList(
                    com.studencollabfin.server.model.PostType.ASK_HELP,
                    com.studencollabfin.server.model.PostType.OFFER_HELP,
                    com.studencollabfin.server.model.PostType.LOOKING_FOR,
                    com.studencollabfin.server.model.PostType.POLL);

            // Filter for campus posts only - by type and category
            java.util.List<Post> campusPosts = new java.util.ArrayList<>();
            for (Post post : posts) {
                if (post instanceof com.studencollabfin.server.model.SocialPost) {
                    com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                    // Include posts with CAMPUS category or no category set (backward
                    // compatibility)
                    String category = social.getCategory() != null ? social.getCategory() : "CAMPUS";
                    if (campusTypes.contains(social.getType()) && ("CAMPUS".equals(category) || category == null)) {
                        campusPosts.add(post);
                    }
                } else if (post instanceof com.studencollabfin.server.model.TeamFindingPost) {
                    campusPosts.add(post);
                }
            }
            System.out.println("Campus posts filtered: " + campusPosts.size());

            // Apply type filter if provided
            if (type != null && !type.isBlank()) {
                try {
                    com.studencollabfin.server.model.PostType ptype = com.studencollabfin.server.model.PostType
                            .valueOf(type);
                    java.util.List<Post> filtered = new java.util.ArrayList<>();
                    for (Post post : campusPosts) {
                        if (post instanceof com.studencollabfin.server.model.SocialPost) {
                            com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                            if (social.getType() == ptype)
                                filtered.add(post);
                        }
                    }
                    campusPosts = filtered;
                } catch (IllegalArgumentException ex) {
                    // unknown type - ignore
                }
            }

            return ResponseEntity.ok(convertToRichPosts(campusPosts));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(new java.util.ArrayList<>());
        }
    }

    // Separate endpoints for Inter Feed posts
    @GetMapping("/inter")
    public ResponseEntity<List<Object>> getInterPosts(@RequestParam(required = false) String type) {
        try {
            List<Post> posts = postService.getAllPosts();
            System.out.println("Total posts from DB: " + posts.size());

            java.util.List<com.studencollabfin.server.model.PostType> interTypes = java.util.Arrays.asList(
                    com.studencollabfin.server.model.PostType.DISCUSSION,
                    com.studencollabfin.server.model.PostType.COLLAB,
                    com.studencollabfin.server.model.PostType.POLL);

            // Filter for inter posts only - by type and category
            java.util.List<Post> interPosts = new java.util.ArrayList<>();
            for (Post post : posts) {
                if (post instanceof com.studencollabfin.server.model.SocialPost) {
                    com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                    // Only include posts with INTER category
                    String category = social.getCategory() != null ? social.getCategory() : "CAMPUS";
                    if (interTypes.contains(social.getType()) && "INTER".equals(category)) {
                        interPosts.add(post);
                    }
                }
            }
            System.out.println("Inter posts filtered: " + interPosts.size());

            // Apply type filter if provided
            if (type != null && !type.isBlank()) {
                try {
                    com.studencollabfin.server.model.PostType ptype = com.studencollabfin.server.model.PostType
                            .valueOf(type);
                    java.util.List<Post> filtered = new java.util.ArrayList<>();
                    for (Post post : interPosts) {
                        if (post instanceof com.studencollabfin.server.model.SocialPost) {
                            com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                            if (social.getType() == ptype)
                                filtered.add(post);
                        }
                    }
                    interPosts = filtered;
                } catch (IllegalArgumentException ex) {
                    // unknown type - ignore
                }
            }

            return ResponseEntity.ok(convertToRichPosts(interPosts));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(new java.util.ArrayList<>());
        }
    }

    @PostMapping("/{postId}/comment")
    public ResponseEntity<Object> addCommentToPost(@PathVariable String postId, @RequestBody CommentRequest req) {
        com.studencollabfin.server.model.Comment saved = postService.addCommentToPost(postId, req);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/counts")
    public ResponseEntity<java.util.Map<String, Long>> getPostCounts() {
        java.util.Map<String, Long> counts = new java.util.HashMap<>();

        // Aggregate SocialPost documents by their `type` field
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("_class").is("com.studencollabfin.server.model.SocialPost")),
                Aggregation.group("type").count().as("count"));

        AggregationResults<org.bson.Document> results = mongoTemplate.aggregate(agg, "posts", org.bson.Document.class);
        for (org.bson.Document doc : results.getMappedResults()) {
            String type = doc.getString("_id");
            Integer c = doc.getInteger("count");
            counts.put(type != null ? type : "UNKNOWN", c != null ? c.longValue() : 0L);
        }

        // Count TeamFindingPost documents separately
        long teamFindingCount = mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(
                Criteria.where("_class").is("com.studencollabfin.server.model.TeamFindingPost")), "posts");
        counts.put("team-finding", teamFindingCount);

        return ResponseEntity.ok(counts);
    }

    @GetMapping("/campus/counts")
    public ResponseEntity<java.util.Map<String, Long>> getCampusPostCounts() {
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        java.util.List<com.studencollabfin.server.model.PostType> campusTypes = java.util.Arrays.asList(
                com.studencollabfin.server.model.PostType.ASK_HELP,
                com.studencollabfin.server.model.PostType.OFFER_HELP,
                com.studencollabfin.server.model.PostType.LOOKING_FOR,
                com.studencollabfin.server.model.PostType.POLL);

        // Get all posts and count by type for campus - filter by CAMPUS category
        List<Post> allPosts = postService.getAllPosts();
        for (com.studencollabfin.server.model.PostType ptype : campusTypes) {
            long count = 0;
            for (Post post : allPosts) {
                if (post instanceof com.studencollabfin.server.model.SocialPost) {
                    com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                    String category = social.getCategory() != null ? social.getCategory() : "CAMPUS";
                    if (social.getType() == ptype && ("CAMPUS".equals(category) || category == null))
                        count++;
                }
            }
            counts.put(ptype.name(), count);
        }
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/inter/counts")
    public ResponseEntity<java.util.Map<String, Long>> getInterPostCounts() {
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        java.util.List<com.studencollabfin.server.model.PostType> interTypes = java.util.Arrays.asList(
                com.studencollabfin.server.model.PostType.DISCUSSION,
                com.studencollabfin.server.model.PostType.COLLAB,
                com.studencollabfin.server.model.PostType.POLL);

        // Get all posts and count by type for inter - filter by INTER category
        List<Post> allPosts = postService.getAllPosts();
        for (com.studencollabfin.server.model.PostType ptype : interTypes) {
            long count = 0;
            for (Post post : allPosts) {
                if (post instanceof com.studencollabfin.server.model.SocialPost) {
                    com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                    String category = social.getCategory() != null ? social.getCategory() : "CAMPUS";
                    if (social.getType() == ptype && "INTER".equals(category))
                        count++;
                }
            }
            counts.put(ptype.name(), count);
        }
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getPostById(@PathVariable String id) {
        try {
            Post post = postService.getPostById(id);
            java.util.Map<String, Object> richPost = new java.util.HashMap<>();
            richPost.put("id", post.getId());
            richPost.put("authorId", post.getAuthorId());
            richPost.put("createdAt", post.getCreatedAt() != null ? post.getCreatedAt().toString() : "");
            if (post instanceof com.studencollabfin.server.model.SocialPost) {
                com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                richPost.put("title", social.getTitle() != null ? social.getTitle() : "");
                richPost.put("content", social.getContent());
                richPost.put("type", social.getType() != null ? social.getType().name() : "");
                richPost.put("postType", social.getType() != null ? social.getType().name() : "");
                richPost.put("likes", social.getLikes() != null ? social.getLikes() : new java.util.ArrayList<>());
                richPost.put("commentIds",
                        social.getCommentIds() != null ? social.getCommentIds() : new java.util.ArrayList<>());
                richPost.put("pollOptions",
                        social.getPollOptions() != null ? social.getPollOptions() : new java.util.ArrayList<>());
                richPost.put("requiredSkills",
                        social.getRequiredSkills() != null ? social.getRequiredSkills() : new java.util.ArrayList<>());
            } else if (post instanceof com.studencollabfin.server.model.TeamFindingPost) {
                com.studencollabfin.server.model.TeamFindingPost team = (com.studencollabfin.server.model.TeamFindingPost) post;
                richPost.put("title", team.getContent());
                richPost.put("content", team.getContent());
                richPost.put("type", "LOOKING_FOR");
                richPost.put("postType", "LOOKING_FOR");
                richPost.put("requiredSkills",
                        team.getRequiredSkills() != null ? team.getRequiredSkills() : new java.util.ArrayList<>());
                richPost.put("maxTeamSize", team.getMaxTeamSize());
                richPost.put("currentTeamMembers", team.getCurrentTeamMembers() != null ? team.getCurrentTeamMembers()
                        : new java.util.ArrayList<>());
            } else {
                richPost.put("title", post.getContent());
                richPost.put("content", post.getContent());
                richPost.put("type", "GENERAL");
                richPost.put("postType", "GENERAL");
            }
            return ResponseEntity.ok(richPost);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Post not found");
        }
    }

    // Fetch TeamFindingPosts by eventId
    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<Object>> getTeamFindingPostsByEventId(@PathVariable String eventId) {
        List<TeamFindingPost> posts = postService.getTeamFindingPostsByEventId(eventId);
        List<Object> richPosts = new java.util.ArrayList<>();
        for (TeamFindingPost post : posts) {
            // Fetch author details (simulate, replace with actual user fetch)
            com.studencollabfin.server.model.User author = null;
            try {
                author = postService.getUserById(post.getAuthorId());
            } catch (Exception e) {
                // fallback if user not found
            }
            java.util.Map<String, Object> authorObj = new java.util.HashMap<>();
            if (author != null) {
                authorObj.put("id", author.getId());
                authorObj.put("name", author.getFullName());
                authorObj.put("collegeName", author.getCollegeName());
                authorObj.put("year", author.getYearOfStudy());
                authorObj.put("tags",
                        author.getExcitingTags() != null ? author.getExcitingTags() : new java.util.ArrayList<>());
            } else {
                authorObj.put("id", post.getAuthorId());
                authorObj.put("name", "Unknown");
                authorObj.put("collegeName", "Unknown");
                authorObj.put("year", "");
                authorObj.put("tags", new java.util.ArrayList<>());
            }
            java.util.Map<String, Object> richPost = new java.util.HashMap<>();
            richPost.put("id", post.getId());
            richPost.put("author", authorObj);
            richPost.put("eventName", post.getEventId()); // You may want to fetch event name
            richPost.put("title", post.getContent());
            richPost.put("description", post.getContent());
            richPost.put("requiredSkills",
                    post.getRequiredSkills() != null ? post.getRequiredSkills() : new java.util.ArrayList<>());
            richPost.put("maxTeamSize", post.getMaxTeamSize());
            richPost.put("currentTeamMembers",
                    post.getCurrentTeamMembers() != null ? post.getCurrentTeamMembers() : new java.util.ArrayList<>());
            richPost.put("createdAt", post.getCreatedAt() != null ? post.getCreatedAt().toString() : "");
            richPost.put("expiresAt", post.getCreatedAt() != null ? post.getCreatedAt().plusHours(48).toString() : "");
            richPost.put("applicants", new java.util.ArrayList<>()); // Add applicants if available
            richPosts.add(richPost);
        }
        return ResponseEntity.ok(richPosts);
    }

    // Helper method to convert posts to rich format
    private List<Object> convertToRichPosts(List<Post> posts) {
        List<Object> richPosts = new java.util.ArrayList<>();
        for (Post post : posts) {
            java.util.Map<String, Object> richPost = new java.util.HashMap<>();
            richPost.put("id", post.getId());
            richPost.put("authorId", post.getAuthorId());
            richPost.put("createdAt", post.getCreatedAt() != null ? post.getCreatedAt().toString() : "");

            // Add author details (name, college, year)
            addAuthorDetailsToPost(richPost, post.getAuthorId());

            if (post instanceof com.studencollabfin.server.model.SocialPost) {
                com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                richPost.put("title", social.getTitle() != null ? social.getTitle() : "");
                richPost.put("content", social.getContent());
                richPost.put("type", social.getType() != null ? social.getType().name() : "");
                richPost.put("postType", social.getType() != null ? social.getType().name() : "");
                richPost.put("likes", social.getLikes() != null ? social.getLikes() : new java.util.ArrayList<>());
                richPost.put("commentIds",
                        social.getCommentIds() != null ? social.getCommentIds() : new java.util.ArrayList<>());
                richPost.put("pollOptions",
                        social.getPollOptions() != null ? social.getPollOptions() : new java.util.ArrayList<>());
                richPost.put("requiredSkills",
                        social.getRequiredSkills() != null ? social.getRequiredSkills() : new java.util.ArrayList<>());
                richPost.put("linkedPodId", social.getLinkedPodId() != null ? social.getLinkedPodId() : "");
            } else if (post instanceof com.studencollabfin.server.model.TeamFindingPost) {
                com.studencollabfin.server.model.TeamFindingPost team = (com.studencollabfin.server.model.TeamFindingPost) post;
                richPost.put("title", team.getContent());
                richPost.put("content", team.getContent());
                richPost.put("type", "LOOKING_FOR");
                richPost.put("postType", "LOOKING_FOR");
                richPost.put("requiredSkills",
                        team.getRequiredSkills() != null ? team.getRequiredSkills() : new java.util.ArrayList<>());
                richPost.put("maxTeamSize", team.getMaxTeamSize());
                richPost.put("currentTeamMembers", team.getCurrentTeamMembers() != null ? team.getCurrentTeamMembers()
                        : new java.util.ArrayList<>());
            } else {
                richPost.put("title", post.getContent());
                richPost.put("content", post.getContent());
                richPost.put("type", "GENERAL");
                richPost.put("postType", "GENERAL");
            }
            richPosts.add(richPost);
        }
        return richPosts;
    }
}