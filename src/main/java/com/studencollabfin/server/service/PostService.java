package com.studencollabfin.server.service;

import com.studencollabfin.server.dto.CommentRequest;
import com.studencollabfin.server.model.Post;
import com.studencollabfin.server.model.SocialPost;
import com.studencollabfin.server.model.TeamFindingPost;
import com.studencollabfin.server.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import com.studencollabfin.server.model.CollabPod;
import com.studencollabfin.server.repository.CollabPodRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PostService {
    // Poll voting logic
    public Post voteOnPollOption(String postId, int optionId, String userId) {
        Post post = getPostById(postId);
        if (post instanceof com.studencollabfin.server.model.SocialPost) {
            com.studencollabfin.server.model.SocialPost social = (com.studencollabfin.server.model.SocialPost) post;
            java.util.List<com.studencollabfin.server.model.PollOption> options = social.getPollOptions();
            if (options != null && optionId >= 0 && optionId < options.size()) {
                com.studencollabfin.server.model.PollOption option = options.get(optionId);
                if (option.getVotes() == null) {
                    option.setVotes(new java.util.ArrayList<>());
                }
                // Prevent duplicate votes
                boolean alreadyVoted = options.stream()
                        .anyMatch(opt -> opt.getVotes() != null && opt.getVotes().contains(userId));
                if (!alreadyVoted) {
                    option.getVotes().add(userId);
                    postRepository.save(social);
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
    private final CollabPodRepository collabPodRepository;

    // This method can save any kind of post (SocialPost, TeamFindingPost, etc.)
    public Post createPost(Post post, String authorId) {
        post.setAuthorId(authorId);
        post.setCreatedAt(LocalDateTime.now());

        // If this is a SocialPost with LOOKING_FOR type, create the pod first
        if (post instanceof SocialPost) {
            SocialPost social = (SocialPost) post;
            if (social.getType() == com.studencollabfin.server.model.PostType.LOOKING_FOR) {
                try {
                    CollabPod pod = new CollabPod();
                    pod.setTitle(social.getTitle() != null ? social.getTitle() : "Looking for collaborators");
                    pod.setDescription(social.getContent());
                    pod.setMaxCapacity(6);
                    pod.setTopics(social.getRequiredSkills() != null ? social.getRequiredSkills()
                            : new java.util.ArrayList<>());
                    pod.setType(CollabPod.PodType.LOOKING_FOR);
                    pod.setStatus(CollabPod.PodStatus.ACTIVE);
                    pod.setCreatorId(authorId);
                    pod.setCreatedAt(LocalDateTime.now());
                    pod.setLastActive(LocalDateTime.now());
                    if (pod.getMemberIds() == null)
                        pod.setMemberIds(new java.util.ArrayList<>());
                    pod.getMemberIds().add(authorId);
                    if (pod.getModeratorIds() == null)
                        pod.setModeratorIds(new java.util.ArrayList<>());
                    pod.getModeratorIds().add(authorId);

                    CollabPod savedPod = collabPodRepository.save(pod); // save immediately to get ID
                    social.setLinkedPodId(savedPod.getId());

                    // Award XP for creating a collab pod
                    try {
                        userService.awardCollabPodCreationXP(authorId);
                    } catch (Exception ex) {
                        /* ignore */ }
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
        return postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + id));
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

    // ✅ CASCADE DELETE: Delete all posts linked to a Pod
    public void deletePostsByPodId(String podId) {
        try {
            Long deletedCount = postRepository.deleteByLinkedPodId(podId);
            System.out.println("✅ Cascade Delete: " + (deletedCount != null ? deletedCount : 0)
                    + " posts with linkedPodId=" + podId + " deleted");
        } catch (Exception e) {
            System.err.println("⚠️ Failed to delete posts for podId " + podId + ": " + e.getMessage());
        }
    }
}