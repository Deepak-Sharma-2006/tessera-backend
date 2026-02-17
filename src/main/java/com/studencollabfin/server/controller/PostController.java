package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.Post;
import com.studencollabfin.server.model.SocialPost;
import com.studencollabfin.server.model.TeamFindingPost;
import com.studencollabfin.server.model.XPAction;
import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.service.PostService;
import com.studencollabfin.server.service.UserService;
import com.studencollabfin.server.service.GamificationService;
import com.studencollabfin.server.repository.CollabPodRepository;
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
    private final CollabPodRepository collabPodRepository;

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

    /**
     * ✅ DOMAIN-LOCKED INSTITUTIONAL ISOLATION: Extract email domain
     * Converts "sara@sinhgad.edu" → "sinhgad.edu"
     * 
     * @param email The user's email address
     * @return The domain portion of the email, or empty string if invalid
     */
    private String extractDomainFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "";
        }
        String domain = email.substring(email.indexOf("@") + 1).toLowerCase().trim();
        return domain.isEmpty() ? "" : domain;
    }

    @GetMapping
    public ResponseEntity<List<Object>> getAllPosts(@RequestParam(required = false) String type,
            Authentication authentication, HttpServletRequest request) {
        // ✅ Campus Isolation: Fetch current user and filter posts by college
        String userId = getCurrentUserId(authentication, request);
        List<Post> posts = new java.util.ArrayList<>();

        if (userId != null && !userId.trim().isEmpty()) {
            try {
                com.studencollabfin.server.model.User currentUser = userService.getUserById(userId);
                if (currentUser != null && currentUser.getCollegeName() != null) {
                    posts = postService.getAllPosts(currentUser.getCollegeName());
                    System.out.println("✅ Filtered posts for college: " + currentUser.getCollegeName());
                } else {
                    System.out.println("⚠️ User not found or college is null");
                    posts = new java.util.ArrayList<>();
                }
            } catch (Exception ex) {
                System.err.println("⚠️ Error fetching user or posts: " + ex.getMessage());
                posts = new java.util.ArrayList<>();
            }
        } else {
            System.out.println("⚠️ No userId found, returning empty list for campus isolation");
            posts = new java.util.ArrayList<>();
        }

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
            System.out.println("Category: " + socialPost.getCategory()); // ✅ LOG CATEGORY
            String userId = getCurrentUserId(authentication, request);
            System.out.println("Author ID: " + userId);
            Post createdPost = postService.createPost(socialPost, userId);
            System.out.println("Post created successfully with ID: " + createdPost.getId());
            // ✅ VERIFY category was saved
            if (createdPost instanceof SocialPost) {
                System.out.println("Saved post category: " + ((SocialPost) createdPost).getCategory());
            }

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

        // ✅ RELIST VALIDATION: If linkedPodId is provided, validate it
        if (teamFindingPost.getLinkedPodId() != null && !teamFindingPost.getLinkedPodId().isEmpty()) {
            System.out.println("🔁 [RELIST] Validating linkedPodId: " + teamFindingPost.getLinkedPodId());

            // Verify pod exists
            CollabPod pod = collabPodRepository.findById(teamFindingPost.getLinkedPodId())
                    .orElseThrow(() -> new RuntimeException("Pod not found: " + teamFindingPost.getLinkedPodId()));

            // Verify pod is TEAM type
            if (pod.getType() != CollabPod.PodType.TEAM) {
                throw new RuntimeException("Can only relist TEAM pods");
            }

            // Verify user is pod owner or admin
            if (!userId.equals(pod.getOwnerId()) &&
                    (pod.getAdminIds() == null || !pod.getAdminIds().contains(userId))) {
                throw new RuntimeException("Only pod owner or admin can create relist posts");
            }

            System.out.println("✅ [RELIST] Validation passed - creating relist post for pod " + pod.getId());
        }

        Post createdPost = postService.createPost(teamFindingPost, userId);

        // 📊 GAMIFICATION: Award XP for creating a post
        gamificationService.awardXp(userId, XPAction.CREATE_POST);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdPost);
    }

    // Separate endpoints for Campus Feed posts
    @GetMapping("/campus")
    public ResponseEntity<List<Object>> getCampusPosts(@RequestParam(required = false) String type,
            Authentication authentication, HttpServletRequest request) {
        try {
            // ✅ DOMAIN-LOCKED INSTITUTIONAL ISOLATION: Extract user's email domain
            String userId = getCurrentUserId(authentication, request);
            String institutionDomain = null;

            if (userId != null && !userId.trim().isEmpty()) {
                try {
                    com.studencollabfin.server.model.User currentUser = userService.getUserById(userId);
                    if (currentUser != null && currentUser.getEmail() != null) {
                        institutionDomain = extractDomainFromEmail(currentUser.getEmail());
                        if (institutionDomain != null && !institutionDomain.isEmpty()) {
                            System.out.println("✅ Campus Feed: User domain authenticated: " + institutionDomain);
                        } else {
                            System.err.println(
                                    "⚠️ Campus Feed: Could not extract domain from email: " + currentUser.getEmail());
                            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                    .body(java.util.List.of(java.util.Map.of("error", "User email domain not found")));
                        }
                    } else {
                        System.err.println("⚠️ Campus Feed: User or email not found");
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(java.util.List.of(java.util.Map.of("error", "User not authenticated")));
                    }
                } catch (Exception ex) {
                    System.err.println("⚠️ Campus Feed: Error fetching user: " + ex.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(java.util.List.of(java.util.Map.of("error", "Error fetching user")));
                }
            } else {
                System.err.println("⚠️ Campus Feed: No userId found");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(java.util.List.of(java.util.Map.of("error", "User ID not found")));
            }

            // Fetch posts strictly by institution domain
            List<Post> posts = postService.getCampusPostsByDomain(institutionDomain);
            System.out.println("Total campus posts for domain " + institutionDomain + ": " + posts.size());

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
            System.out.println("Campus posts filtered by type: " + campusPosts.size());

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
    public ResponseEntity<java.util.Map<String, Long>> getCampusPostCounts(Authentication authentication,
            HttpServletRequest request) {
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        java.util.List<com.studencollabfin.server.model.PostType> campusTypes = java.util.Arrays.asList(
                com.studencollabfin.server.model.PostType.ASK_HELP,
                com.studencollabfin.server.model.PostType.OFFER_HELP,
                com.studencollabfin.server.model.PostType.LOOKING_FOR,
                com.studencollabfin.server.model.PostType.POLL);

        // ✅ DOMAIN-LOCKED: Extract user's email domain and fetch posts from same domain
        // only
        String userId = getCurrentUserId(authentication, request);
        String institutionDomain = null;

        if (userId != null && !userId.trim().isEmpty()) {
            try {
                com.studencollabfin.server.model.User currentUser = userService.getUserById(userId);
                if (currentUser != null && currentUser.getEmail() != null) {
                    institutionDomain = extractDomainFromEmail(currentUser.getEmail());
                    if (institutionDomain == null || institutionDomain.isEmpty()) {
                        System.err.println("⚠️ Campus Counts: Could not extract domain from email");
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(java.util.Map.of());
                    }
                } else {
                    System.err.println("⚠️ Campus Counts: User or email not found");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(java.util.Map.of());
                }
            } catch (Exception ex) {
                System.err.println("⚠️ Campus Counts: Error fetching user: " + ex.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(java.util.Map.of());
            }
        } else {
            System.err.println("⚠️ Campus Counts: No userId found");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of());
        }

        // Get posts strictly by institution domain (not all posts)
        List<Post> allPosts = postService.getCampusPostsByDomain(institutionDomain);
        for (com.studencollabfin.server.model.PostType ptype : campusTypes) {
            long count = 0;
            for (Post post : allPosts) {
                // ✅ DOMAIN CHECK: Only count posts that belong to this user's institution
                if (post.getInstitutionDomain() == null || !post.getInstitutionDomain().equals(institutionDomain)) {
                    continue;
                }

                if (post instanceof com.studencollabfin.server.model.SocialPost) {
                    com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                    String category = social.getCategory() != null ? social.getCategory() : "CAMPUS";

                    // For LOOKING_FOR posts, verify pod source is COLLAB_POD (exclude TEAM_PODs)
                    if (social.getType() == ptype && ("CAMPUS".equals(category) || category == null)) {
                        if (ptype == com.studencollabfin.server.model.PostType.LOOKING_FOR) {
                            // Verify linkedPodId points to a COLLAB_POD (not TEAM_POD)
                            String linkedPodId = social.getLinkedPodId();
                            if (linkedPodId != null && !linkedPodId.isEmpty()) {
                                java.util.Optional<CollabPod> podOpt = collabPodRepository.findById(linkedPodId);
                                if (podOpt.isPresent()
                                        && podOpt.get().getPodSource() == CollabPod.PodSource.COLLAB_POD) {
                                    count++;
                                }
                            }
                        } else {
                            // For non-LOOKING_FOR types, count normally
                            count++;
                        }
                    }
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
    public ResponseEntity<Object> getPostById(@PathVariable String id, Authentication authentication,
            HttpServletRequest request) {
        try {
            System.err.println("\n\n███████████████████████████████████████████████████████████████████");
            System.err.println("📍 ===>>> NEW SECURITY FIX ACTIVATED - getPostById endpoint <<<===");
            System.err.println("███████████████████████████████████████████████████████████████████\n");
            Post post = postService.getPostById(id);
            System.out.println(
                    "🔍 POST RETRIEVAL: Fetched post ID: " + id + ", Type: " + post.getClass().getSimpleName());

            // ✅ SECURITY LAYER: Verify user's domain matches post's domain (institutional
            // isolation)
            // EXCEPTION: Allow cross-domain access for INTER category posts (Global Hub)
            String userId = getCurrentUserId(authentication, request);
            System.out.println("🔑 SECURITY: UserId from request: " + userId);

            if (userId != null && !userId.trim().isEmpty()) {
                try {
                    com.studencollabfin.server.model.User currentUser = userService.getUserById(userId);
                    if (currentUser != null && currentUser.getEmail() != null) {
                        String userDomain = extractDomainFromEmail(currentUser.getEmail());
                        String postDomain = post.getInstitutionDomain();

                        // Handle case where postDomain is null (shouldn't happen for properly created
                        // posts)
                        if (postDomain == null || postDomain.trim().isEmpty()) {
                            System.err.println("⚠️ WARNING: Post has no institutionDomain set (ID: " + id + ")");
                            postDomain = "unknown";
                        }

                        System.out.println(
                                "🔒 SECURITY CHECK START: User domain=" + userDomain + ", Post domain=" + postDomain);

                        // ✅ ALLOW GLOBAL HUB: Check if post is in INTER category (Global Hub)
                        String postCategory = null;
                        com.studencollabfin.server.model.PostType postType = null;
                        boolean isGlobalHubPost = false;

                        if (post instanceof com.studencollabfin.server.model.SocialPost) {
                            com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                            postCategory = social.getCategory();
                            postType = social.getType();

                            System.out.println("📋 POST INFO: Category from DB=" + postCategory + ", Type=" + postType);

                            // ✅ If category is null (old posts), check if post type indicates Global Hub
                            if (postCategory == null || postCategory.trim().isEmpty()) {
                                // Global Hub post types: DISCUSSION, POLL, COLLAB
                                if (postType == com.studencollabfin.server.model.PostType.DISCUSSION ||
                                        postType == com.studencollabfin.server.model.PostType.POLL ||
                                        postType == com.studencollabfin.server.model.PostType.COLLAB) {
                                    postCategory = "INTER";
                                    isGlobalHubPost = true;
                                    System.out.println("⚠️ INFERRED: Category was null, but type " + postType
                                            + " indicates GLOBAL HUB");
                                } else {
                                    postCategory = "CAMPUS";
                                    System.out.println("⚠️ DEFAULTED: Category was null, type " + postType
                                            + ", defaulting to CAMPUS");
                                }
                            } else if ("INTER".equals(postCategory.trim())) {
                                isGlobalHubPost = true;
                                System.out.println("✓ CONFIRMED: Category is explicitly INTER (GLOBAL HUB)");
                            } else {
                                System.out.println("ℹ️ INFO: Category is " + postCategory + " (CAMPUS POST)");
                            }
                        } else if (post instanceof com.studencollabfin.server.model.TeamFindingPost) {
                            postCategory = "CAMPUS";
                            System.out.println("📋 POST INFO: TeamFindingPost detected - treating as CAMPUS");
                        } else {
                            postCategory = "CAMPUS";
                            System.out.println("📋 POST INFO: Generic Post detected - treating as CAMPUS");
                        }

                        // SECURITY CHECK: Cross-domain access allowed if post is in INTER category OR
                        // is a Global Hub type
                        System.out.println(
                                "🔐 DECISION FACTORS: userDomain=" + userDomain + ", postDomain=" + postDomain +
                                        ", postCategory=" + postCategory + ", isGlobalHubPost=" + isGlobalHubPost);

                        // Allow access if:
                        // 1. Domains match (same college), OR
                        // 2. Post is INTER category (Global Hub), OR
                        // 3. Post type indicates Global Hub (DISCUSSION/POLL/COLLAB from INTER)
                        boolean domainMatches = userDomain.equals(postDomain);
                        boolean isInterCategory = "INTER".equals(postCategory);

                        System.out.println("🔐 ACCESS DECISION: domainMatches=" + domainMatches +
                                ", isInterCategory=" + isInterCategory + ", isGlobalHubPost=" + isGlobalHubPost);
                        System.out.println("🔐 CONDITION: (!domainMatches=" + !domainMatches + " AND !isInterCategory="
                                + !isInterCategory +
                                " AND !isGlobalHubPost=" + !isGlobalHubPost + ") = "
                                + (!domainMatches && !isInterCategory && !isGlobalHubPost));

                        if (!domainMatches && !isInterCategory && !isGlobalHubPost) {
                            System.err.println("❌ ACCESS DENIED: Different domains AND not a Global Hub post");
                            System.err.println("   User: " + userDomain + " | Post: " + postDomain + " | Category: "
                                    + postCategory);
                            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                    .body(java.util.Map.of(
                                            "error", "Access denied to this private college pod",
                                            "userDomain", userDomain,
                                            "postDomain", postDomain,
                                            "postCategory", postCategory,
                                            "postType", postType != null ? postType.name() : "null",
                                            "reason", "Cross-domain access only allowed for Global Hub posts"));
                        }

                        if (domainMatches) {
                            System.out.println("✅ ACCESS ALLOWED: Same college domain");
                        } else if (isInterCategory || isGlobalHubPost) {
                            System.out.println("✅ ACCESS ALLOWED: Global Hub post (cross-domain access permitted)");
                        }
                    } else {
                        System.err.println("⚠️ SECURITY: User or email not found for userId=" + userId);
                    }
                } catch (Exception ex) {
                    System.err.println("❌ SECURITY: Exception during domain verification: " + ex.getMessage());
                    ex.printStackTrace();
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(java.util.Map.of("error", "Error verifying post access", "details", ex.getMessage()));
                }
            } else {
                System.out.println("⚠️ SECURITY: No userId found - unauthenticated access attempted");
            }

            java.util.Map<String, Object> richPost = new java.util.HashMap<>();
            richPost.put("id", post.getId());
            richPost.put("authorId", post.getAuthorId());
            richPost.put("createdAt", post.getCreatedAt() != null ? post.getCreatedAt().toString() : "");
            richPost.put("institutionDomain", post.getInstitutionDomain());
            if (post instanceof com.studencollabfin.server.model.SocialPost) {
                com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
                richPost.put("title", social.getTitle() != null ? social.getTitle() : "");
                richPost.put("content", social.getContent());
                richPost.put("type", social.getType() != null ? social.getType().name() : "");
                richPost.put("postType", social.getType() != null ? social.getType().name() : "");
                // ✅ Category with fallback for backward compatibility with old posts
                String category = social.getCategory() != null && !social.getCategory().isEmpty() ? social.getCategory()
                        : "CAMPUS";
                richPost.put("category", category);
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
    // ✅ Campus Isolation: Filter team finding posts by current user's college
    @GetMapping("/event/{eventId}")
    public ResponseEntity<List<Object>> getTeamFindingPostsByEventId(@PathVariable String eventId,
            Authentication authentication, HttpServletRequest request) {
        // Get current user's college
        String userId = getCurrentUserId(authentication, request);
        String userCollege = null;

        if (userId != null && !userId.trim().isEmpty()) {
            try {
                com.studencollabfin.server.model.User currentUser = userService.getUserById(userId);
                if (currentUser != null && currentUser.getCollegeName() != null) {
                    userCollege = currentUser.getCollegeName();
                }
            } catch (Exception ex) {
                System.err.println("⚠️ Error fetching current user: " + ex.getMessage());
            }
        }

        // Fetch all posts for the event
        List<TeamFindingPost> allPosts = postService.getTeamFindingPostsByEventId(eventId);

        // ✅ Filter by college (only show posts from same college)
        final String finalUserCollege = userCollege;
        List<TeamFindingPost> posts = allPosts.stream()
                .filter(p -> finalUserCollege != null && finalUserCollege.equals(p.getCollege()))
                .toList();

        System.out.println("✅ Filtered team posts for college: " + userCollege + ", count: " + posts.size());

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
                // ✅ Include category in response with fallback for backward compatibility
                String category = social.getCategory() != null && !social.getCategory().isEmpty() ? social.getCategory()
                        : "CAMPUS";
                richPost.put("category", category);
                richPost.put("institutionDomain", post.getInstitutionDomain()); // ✅ Include domain for debugging
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