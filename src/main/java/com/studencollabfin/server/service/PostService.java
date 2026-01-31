package com.studencollabfin.server.service;

import com.studencollabfin.server.dto.CommentRequest;
import com.studencollabfin.server.model.Comment;
import com.studencollabfin.server.model.Post;
import com.studencollabfin.server.model.SocialPost;
import com.studencollabfin.server.model.TeamFindingPost;
import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.repository.PostRepository;
import com.studencollabfin.server.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final EventService eventService;

    public SocialPost toggleLike(String postId, String userId) {
        Post post = getPostById(postId);
        if (post instanceof SocialPost social) {
            if (social.getLikes() == null) {
                social.setLikes(new ArrayList<>());
            }
            if (social.getLikes().contains(userId)) {
                social.getLikes().remove(userId);
            } else {
                social.getLikes().add(userId);
            }
            return postRepository.save(social);
        }
        throw new RuntimeException("Likes only supported for SocialPosts");
    }

    // Poll voting logic
    public Post voteOnPollOption(String postId, String optionId, String userId) {
        Post post = getPostById(postId);
        if (post instanceof com.studencollabfin.server.model.SocialPost) {
            com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
            java.util.List<com.studencollabfin.server.model.PollOption> options = social.getPollOptions();
            if (options != null && options.size() > 0) {
                // Find option by UUID
                com.studencollabfin.server.model.PollOption selectedOption = options.stream()
                        .filter(opt -> optionId.equals(opt.getId()))
                        .findFirst()
                        .orElse(null);

                if (selectedOption != null) {
                    if (selectedOption.getVotes() == null) {
                        selectedOption.setVotes(new java.util.ArrayList<>());
                    }
                    // Prevent duplicate votes
                    boolean alreadyVoted = options.stream()
                            .anyMatch(opt -> opt.getVotes() != null && opt.getVotes().contains(userId));
                    if (!alreadyVoted) {
                        selectedOption.getVotes().add(userId);
                        postRepository.save(social);
                    }
                }
            }
        }
        return post;
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.studencollabfin.server.service.UserService userService;

    public com.studencollabfin.server.model.User getUserById(String userId) {
        return userService.getUserById(userId);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private CollabPodService collabPodService;

    @org.springframework.beans.factory.annotation.Autowired
    private AchievementService achievementService;

    /**
     * Delete a SocialPost with ONE-WAY cascade operations.
     * 
     * IMPORTANT: This is ONE-WAY cascade. Post deletion does NOT cascade to pods.
     * - Posts can be deleted independently
     * - Pod deletion handles cascading to posts (see CollabPodService.deletePod())
     * 
     * Why this design?
     * We only have UI to delete pods/rooms, NOT posts. If we allow post deletion to
     * trigger
     * pod deletion, it creates circular dependencies and potential data loss. Pod
     * deletion
     * manages the cascade chain: Pod → Messages → Linked Post.
     */
    @SuppressWarnings("null")
    public void deletePost(String postId) {
        Post post = getPostById(postId);

        // Simply delete the post - do NOT cascade to pods
        // Pod deletion is always initiated from pod UI, which handles cascading
        // properly
        if (post instanceof SocialPost) {
            SocialPost social = (SocialPost) post;
            if (social.getLinkedPodId() != null) {
                System.out.println("ℹ️ Post " + postId + " is linked to pod " + social.getLinkedPodId() +
                        " but post deletion does NOT cascade to pod (one-way cascade only)");
            }
        }

        try {
            postRepository.deleteById(postId);
            System.out.println("✅ Post " + postId + " deleted");
        } catch (Exception ex) {
            System.err.println("⚠️ Failed to delete post " + postId + ": " + ex.getMessage());
            throw new RuntimeException("Failed to delete post", ex);
        }
    }

    // This method can save any kind of post (SocialPost, TeamFindingPost, etc.)
    public Post createPost(Post post, String authorId) {
        post.setAuthorId(authorId);
        post.setCreatedAt(LocalDateTime.now());

        // ✅ Campus Isolation: Fetch author and set college
        try {
            com.studencollabfin.server.model.User author = userService.getUserById(authorId);
            if (author != null && author.getCollegeName() != null) {
                post.setCollege(author.getCollegeName());
                System.out.println("✅ Post college set to: " + author.getCollegeName());
            }
        } catch (Exception ex) {
            System.err.println("⚠️ Failed to fetch author for college assignment: " + ex.getMessage());
        }

        // If this is a SocialPost, handle both LOOKING_FOR and COLLAB types
        if (post instanceof SocialPost) {
            SocialPost social = (SocialPost) post;

            // Initialize empty lists if null
            if (social.getLikes() == null) {
                social.setLikes(new java.util.ArrayList<>());
            }
            if (social.getCommentIds() == null) {
                social.setCommentIds(new java.util.ArrayList<>());
            }
            if (social.getPollOptions() == null) {
                social.setPollOptions(new java.util.ArrayList<>());
            }
        }

        // Save the post first to get its ID
        Post savedPost = postRepository.save(post);

        // ✅ INCREMENT postsCount on user for Signal Guardian badge tracking
        try {
            com.studencollabfin.server.model.User author = userService.getUserById(authorId);
            if (author != null) {
                author.setPostsCount((author.getPostsCount() == 0 ? 0 : author.getPostsCount()) + 1);
                userService.updateUserProfile(authorId, author);
                System.out.println("✓ Incremented postsCount for user: " + authorId + " (new count: " + author.getPostsCount() + ")");
                
                // ✅ SYNC BADGES: Check if user reached postsCount >= 5 for Signal Guardian
                achievementService.syncUserBadges(author);
            }
        } catch (Exception ex) {
            System.err.println("⚠️ Failed to increment postsCount or sync badges: " + ex.getMessage());
        }

        // Now handle pod creation with the saved post's ID
        if (savedPost instanceof SocialPost) {
            SocialPost social = (SocialPost) savedPost;

            if (social.getType() == com.studencollabfin.server.model.PostType.LOOKING_FOR) {
                try {
                    System.out.println("Creating CollabPod for LOOKING_FOR post: " + social.getId());
                    CollabPod pod = new CollabPod();
                    pod.setName(social.getTitle() != null ? social.getTitle() : "Looking for collaborators");
                    pod.setDescription(social.getContent());
                    pod.setMaxCapacity(6);
                    pod.setTopics(social.getRequiredSkills() != null ? social.getRequiredSkills()
                            : new java.util.ArrayList<>());
                    pod.setType(CollabPod.PodType.LOOKING_FOR);
                    pod.setStatus(CollabPod.PodStatus.ACTIVE);
                    pod.setScope(com.studencollabfin.server.model.PodScope.CAMPUS);
                    pod.setLinkedPostId(social.getId()); // Set bi-directional link
                    System.out.println("📌 Pod linkedPostId set to: " + social.getId());

                    CollabPod createdPod = collabPodService.createPod(authorId, pod);
                    System.out.println("CollabPod successfully created with ID: " + createdPod.getId());

                    // Verify linkedPostId is persisted in database
                    CollabPod verifyPod = collabPodService.getPodById(createdPod.getId());
                    System.out.println("📋 Pod verified - linkedPostId in DB: " + verifyPod.getLinkedPostId());

                    social.setLinkedPodId(createdPod.getId());
                    savedPost = postRepository.save(social); // Save the updated post and return it
                    System.out.println("Post saved with linkedPodId: " + createdPod.getId());
                } catch (Exception ex) {
                    System.err.println("Failed to create CollabPod during post creation: " + ex.getMessage());
                    ex.printStackTrace();
                }
            } else if (social.getType() == com.studencollabfin.server.model.PostType.COLLAB) {
                try {
                    System.out.println("Creating CollabPod for COLLAB post: " + social.getId());
                    CollabPod pod = new CollabPod();
                    pod.setName(social.getTitle() != null ? social.getTitle() : "Collab Room");
                    pod.setDescription(social.getContent());
                    pod.setMaxCapacity(10); // Global rooms can accommodate more
                    pod.setTopics(social.getRequiredSkills() != null ? social.getRequiredSkills()
                            : new java.util.ArrayList<>());
                    pod.setType(CollabPod.PodType.COLLAB); // Use COLLAB pod type for COLLAB rooms (distinct from
                                                           // DISCUSSION)
                    pod.setStatus(CollabPod.PodStatus.ACTIVE);
                    pod.setScope(com.studencollabfin.server.model.PodScope.GLOBAL);
                    pod.setLinkedPostId(social.getId()); // Set bi-directional link
                    System.out.println("Pod created with scope=GLOBAL, type=COLLAB");

                    CollabPod createdPod = collabPodService.createPod(authorId, pod);
                    System.out.println("CollabPod successfully created with ID: " + createdPod.getId());
                    social.setLinkedPodId(createdPod.getId());
                    savedPost = postRepository.save(social); // Save the updated post and return it
                    System.out.println("Post saved with linkedPodId: " + createdPod.getId());
                } catch (Exception ex) {
                    System.err.println("Failed to create CollabPod during post creation: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        } else if (savedPost instanceof TeamFindingPost) {
            TeamFindingPost teamPost = (TeamFindingPost) savedPost;
            try {
                System.out.println("Creating CollabPod for TeamFindingPost: " + teamPost.getId());
                CollabPod pod = new CollabPod();
                // Use title if available, otherwise fall back to content
                pod.setName(teamPost.getTitle() != null ? teamPost.getTitle()
                        : (teamPost.getContent() != null ? teamPost.getContent() : "Looking for collaborators"));
                pod.setDescription(teamPost.getContent());
                pod.setMaxCapacity(6);
                pod.setTopics(teamPost.getRequiredSkills() != null ? teamPost.getRequiredSkills()
                        : new java.util.ArrayList<>());
                pod.setType(CollabPod.PodType.PROJECT_TEAM);
                pod.setStatus(CollabPod.PodStatus.ACTIVE);
                pod.setScope(com.studencollabfin.server.model.PodScope.CAMPUS);
                pod.setLinkedPostId(teamPost.getId()); // Set bi-directional link
                System.out.println("Pod created with scope=CAMPUS, type=PROJECT_TEAM");

                CollabPod createdPod = collabPodService.createPod(authorId, pod);
                System.out.println("CollabPod successfully created with ID: " + createdPod.getId());
                teamPost.setLinkedPodId(createdPod.getId());
                savedPost = postRepository.save(teamPost); // Save the updated post and return it
                System.out.println("TeamFindingPost saved with linkedPodId: " + createdPod.getId());

                // ✅ NEW: Refresh event stats when team post is created
                if (teamPost.getEventId() != null && !teamPost.getEventId().isEmpty()) {
                    eventService.refreshEventStats(teamPost.getEventId());
                }
            } catch (Exception ex) {
                System.err.println("Failed to create CollabPod during team post creation: " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        return savedPost;

    }

    // Fetch TeamFindingPosts by eventId
    public List<TeamFindingPost> getTeamFindingPostsByEventId(String eventId) {
        return postRepository.findByEventId(eventId);
    }

    // ✅ Campus Isolation: Fetch posts from current user's college only
    public List<Post> getAllPosts(String userCollegeName) {
        if (userCollegeName == null || userCollegeName.trim().isEmpty()) {
            System.err.println("⚠️ User college is null/empty, returning empty post list for campus isolation");
            return new java.util.ArrayList<>();
        }
        return postRepository.findByCollege(userCollegeName);
    }

    // Legacy method for backward compatibility (returns all posts without
    // filtering)
    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }

    public Post getPostById(String id) {
        if (id != null) {
            return postRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
        }
        throw new RuntimeException("Post not found with id: null");
    }

    // Add a comment to a SocialPost. Comments are stored in the 'comments'
    // collection
    public Comment addCommentToPost(String postId, CommentRequest req) {
        Post post = getPostById(postId);
        if (!(post instanceof SocialPost)) {
            throw new RuntimeException("Post is not a social post: " + postId);
        }
        SocialPost social = (SocialPost) post;

        // Create Comment model and save to comments collection
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorName(req.getAuthorName());
        comment.setContent(req.getContent());
        comment.setCreatedAt(LocalDateTime.now());
        comment.setParentId(req.getParentId());

        // Determine post type and scope based on SocialPost type
        if (social.getType() == com.studencollabfin.server.model.PostType.DISCUSSION) {
            comment.setPostType("DISCUSSION");
            comment.setScope("GLOBAL");
        } else {
            // ASK_HELP, OFFER_HELP, LOOKING_FOR are campus posts
            comment.setPostType("CAMPUS_POST");
            comment.setScope("CAMPUS");
        }

        // Save to comments collection
        Comment savedComment = commentRepository.save(comment);

        // Update post with comment ID reference
        if (social.getCommentIds() == null) {
            social.setCommentIds(new ArrayList<>());
        }

        if (req.getParentId() == null || req.getParentId().isEmpty()) {
            // Top-level comment - add to post's commentIds
            social.getCommentIds().add(savedComment.getId());
        } else {
            // Reply - update parent comment's replyIds ONLY
            Comment parentComment = commentRepository.findById(req.getParentId()).orElse(null);
            if (parentComment != null) {
                if (parentComment.getReplyIds() == null) {
                    parentComment.setReplyIds(new ArrayList<>());
                }
                parentComment.getReplyIds().add(savedComment.getId());
                commentRepository.save(parentComment);
            }
            // DO NOT add replies to post's commentIds - they're tracked in parent's
            // replyIds
        }

        postRepository.save(social);
        return savedComment;
    }
}