package com.studencollabfin.server.controller;

import java.util.List;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studencollabfin.server.dto.CommentRequest;
import com.studencollabfin.server.model.Post;
import com.studencollabfin.server.model.SocialPost;
import com.studencollabfin.server.model.TeamFindingPost;
import com.studencollabfin.server.service.PostService;
import com.studencollabfin.server.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PostController {
    // Poll voting endpoint
    @PutMapping("/{postId}/vote/{optionId}")
    public ResponseEntity<Post> voteOnPollOption(@PathVariable String postId, @PathVariable int optionId) {
        // Simulate current user ID (replace with real user ID in production)
        String userId = getCurrentUserId();
        Post updatedPost = postService.voteOnPollOption(postId, optionId, userId);
        return ResponseEntity.ok(updatedPost);
    }

    private final PostService postService;
    private final MongoTemplate mongoTemplate;
    private final UserService userService;

    // A placeholder for getting the current user's ID
    @org.springframework.beans.factory.annotation.Autowired
    private jakarta.servlet.http.HttpServletRequest request;

    private String getCurrentUserId() {
        // Try to get userId from X-User-Id header first
        String userIdFromHeader = request.getHeader("X-User-Id");
        if (userIdFromHeader != null && !userIdFromHeader.isEmpty()) {
            return userIdFromHeader;
        }
        // Fallback to placeholder if not provided
        return "placeholder-user-id";
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
            if (post instanceof com.studencollabfin.server.model.SocialPost) {
                com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                richPost.put("title", social.getTitle() != null ? social.getTitle() : "");
                richPost.put("content", social.getContent());
                richPost.put("postType", social.getType() != null ? social.getType().name() : "");
                richPost.put("likes", social.getLikes() != null ? social.getLikes() : new java.util.ArrayList<>());
                richPost.put("comments",
                        social.getComments() != null ? social.getComments() : new java.util.ArrayList<>());
                richPost.put("pollOptions",
                        social.getPollOptions() != null ? social.getPollOptions() : new java.util.ArrayList<>());
                // Ensure linkedPodId is included in the response
                richPost.put("linkedPodId", social.getLinkedPodId());
            } else if (post instanceof com.studencollabfin.server.model.TeamFindingPost) {
                com.studencollabfin.server.model.TeamFindingPost team = (com.studencollabfin.server.model.TeamFindingPost) post;
                richPost.put("title", team.getContent());
                richPost.put("content", team.getContent());
                richPost.put("postType", "team-finding");
                richPost.put("requiredSkills",
                        team.getRequiredSkills() != null ? team.getRequiredSkills() : new java.util.ArrayList<>());
                richPost.put("maxTeamSize", team.getMaxTeamSize());
                richPost.put("currentTeamMembers", team.getCurrentTeamMembers() != null ? team.getCurrentTeamMembers()
                        : new java.util.ArrayList<>());
            } else {
                richPost.put("title", post.getContent());
                richPost.put("content", post.getContent());
                richPost.put("postType", "post");
            }
            richPosts.add(richPost);
        }
        return ResponseEntity.ok(richPosts);
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
                richPost.put("postType", social.getType() != null ? social.getType().name() : "");
                richPost.put("likes", social.getLikes() != null ? social.getLikes() : new java.util.ArrayList<>());
                richPost.put("comments",
                        social.getComments() != null ? social.getComments() : new java.util.ArrayList<>());
                richPost.put("pollOptions",
                        social.getPollOptions() != null ? social.getPollOptions() : new java.util.ArrayList<>());
                // Ensure linkedPodId is included in the response
                richPost.put("linkedPodId", social.getLinkedPodId());
            } else if (post instanceof com.studencollabfin.server.model.TeamFindingPost) {
                com.studencollabfin.server.model.TeamFindingPost team = (com.studencollabfin.server.model.TeamFindingPost) post;
                richPost.put("title", team.getContent());
                richPost.put("content", team.getContent());
                richPost.put("postType", "team-finding");
                richPost.put("requiredSkills",
                        team.getRequiredSkills() != null ? team.getRequiredSkills() : new java.util.ArrayList<>());
                richPost.put("maxTeamSize", team.getMaxTeamSize());
                richPost.put("currentTeamMembers", team.getCurrentTeamMembers() != null ? team.getCurrentTeamMembers()
                        : new java.util.ArrayList<>());
            } else {
                richPost.put("title", post.getContent());
                richPost.put("content", post.getContent());
                richPost.put("postType", "post");
            }
            return ResponseEntity.ok(richPost);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Post not found");
        }
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

    @PostMapping("/social")
    public ResponseEntity<?> createSocialPost(@RequestBody SocialPost socialPost,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
        }
        try {
            System.out.println(
                    "Creating post of type: " + (socialPost.getType() != null ? socialPost.getType().name() : "null"));

            // Extract the authenticated user from JWT
            com.studencollabfin.server.model.User user = userService.findByEmail(userDetails.getUsername());
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            Post createdPost = postService.createPost(socialPost, user.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(createdPost);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/team-finding")
    public ResponseEntity<?> createTeamFindingPost(@RequestBody TeamFindingPost teamFindingPost,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
        }
        try {
            // Extract the authenticated user from JWT
            com.studencollabfin.server.model.User user = userService.findByEmail(userDetails.getUsername());
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }
            Post createdPost = postService.createPost(teamFindingPost, user.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(createdPost);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/{postId}/comment")
    public ResponseEntity<Object> addCommentToPost(@PathVariable String postId, @RequestBody CommentRequest req) {
        com.studencollabfin.server.model.SocialPost.Comment saved = postService.addCommentToPost(postId, req);
        return ResponseEntity.ok(saved);
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
}