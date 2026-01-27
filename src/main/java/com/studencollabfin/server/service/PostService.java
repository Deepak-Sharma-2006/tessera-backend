package com.studencollabfin.server.service;

import com.studencollabfin.server.dto.CommentRequest;
import com.studencollabfin.server.model.Post;
import com.studencollabfin.server.model.SocialPost;
import com.studencollabfin.server.model.TeamFindingPost;
import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {
    // Toggle like for a SocialPost
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

    private final PostRepository postRepository;
    private final CollabPodService collabPodService;

    // This method can save any kind of post (SocialPost, TeamFindingPost, etc.)
    public Post createPost(Post post, String authorId) {
        post.setAuthorId(authorId);
        post.setCreatedAt(LocalDateTime.now());

        // If this is a SocialPost with LOOKING_FOR type, create the pod first
        if (post instanceof SocialPost) {
            SocialPost social = (SocialPost) post;

            // Initialize empty lists if null
            if (social.getLikes() == null) {
                social.setLikes(new java.util.ArrayList<>());
            }
            if (social.getComments() == null) {
                social.setComments(new java.util.ArrayList<>());
            }
            if (social.getPollOptions() == null) {
                social.setPollOptions(new java.util.ArrayList<>());
            }

            if (social.getType() == com.studencollabfin.server.model.PostType.LOOKING_FOR) {
                try {
                    CollabPod pod = new CollabPod();
                    pod.setName(social.getTitle() != null ? social.getTitle() : "Looking for collaborators");
                    pod.setDescription(social.getContent());
                    pod.setMaxCapacity(6);
                    pod.setTopics(social.getRequiredSkills() != null ? social.getRequiredSkills()
                            : new java.util.ArrayList<>());
                    pod.setType(CollabPod.PodType.PROJECT_TEAM);
                    pod.setStatus(CollabPod.PodStatus.ACTIVE);

                    CollabPod createdPod = collabPodService.createPod(authorId, pod);
                    social.setLinkedPodId(createdPod.getId());
                } catch (Exception ex) {
                    System.err.println("Failed to create CollabPod during post creation: " + ex.getMessage());
                }
            }
        }

        return postRepository.save(post);
    }

    // Fetch TeamFindingPosts by eventId
    public List<TeamFindingPost> getTeamFindingPostsByEventId(String eventId) {
        return postRepository.findByEventId(eventId);
    }

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

    // Add a comment to a SocialPost. If parentId is provided, append as a reply
    // recursively.
    public SocialPost.Comment addCommentToPost(String postId, CommentRequest req) {
        Post post = getPostById(postId);
        if (!(post instanceof SocialPost)) {
            throw new RuntimeException("Post is not a social post: " + postId);
        }
        SocialPost social = (SocialPost) post;
        SocialPost.Comment comment = new SocialPost.Comment();
        comment.setAuthorName(req.getAuthorName());
        comment.setContent(req.getContent());
        comment.setCreatedAt(LocalDateTime.now());
        if (social.getComments() == null) {
            social.setComments(new ArrayList<>());
        }
        if (req.getParentId() == null || req.getParentId().isEmpty()) {
            social.getComments().add(comment);
        } else {
            boolean appended = appendToParent(social.getComments(), req.getParentId(), comment);
            if (!appended) {
                // If parent not found, append to root
                social.getComments().add(comment);
            }
        }
        postRepository.save(social);
        return comment;
    }

    private boolean appendToParent(List<SocialPost.Comment> list, String parentId, SocialPost.Comment toAdd) {
        for (SocialPost.Comment c : list) {
            if (c.getId().equals(parentId)) {
                if (c.getReplies() == null)
                    c.setReplies(new ArrayList<>());
                c.getReplies().add(toAdd);
                return true;
            }
            if (c.getReplies() != null && !c.getReplies().isEmpty()) {
                boolean ok = appendToParent(c.getReplies(), parentId, toAdd);
                if (ok)
                    return true;
            }
        }
        return false;
    }
}