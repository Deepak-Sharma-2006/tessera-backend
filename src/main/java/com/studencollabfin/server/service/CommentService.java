package com.studencollabfin.server.service;

import com.studencollabfin.server.gamification.event.ReplyCreatedEvent;
import com.studencollabfin.server.model.Comment;
import com.studencollabfin.server.model.Post;
import com.studencollabfin.server.model.PostType;
import com.studencollabfin.server.model.SocialPost;
import com.studencollabfin.server.repository.CommentRepository;
import com.studencollabfin.server.repository.PostRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CommentService {
    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private AchievementService achievementService;
    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * Get top-level comments for a post (no parent)
     * These are the root-level comments only
     */
    public List<Comment> getCommentsForPost(String postId) {
        return commentRepository.findByPostIdAndParentId(postId, null);
    }

    /**
     * Get replies to a specific comment
     */
    public List<Comment> getReplies(String postId, String parentId) {
        return commentRepository.findByPostIdAndParentId(postId, parentId);
    }

    /**
     * Get all comments for a post (including nested replies)
     */
    public List<Comment> getAllCommentsForPost(String postId) {
        return commentRepository.findByPostId(postId);
    }

    /**
     * Add a comment - with proper categorization
     */
    public Comment addComment(Comment comment) {
        LocalDateTime now = LocalDateTime.now();
        Post parentPost = getParentPost(comment.getPostId());
        long existingReplyCount = comment.getPostId() != null && !comment.getPostId().isBlank()
                ? commentRepository.countByPostId(comment.getPostId())
                : 0L;

        String parentPostAuthorId = parentPost != null ? parentPost.getAuthorId() : null;
        boolean isSelfReplyToOwnPost = parentPostAuthorId != null
                && comment.getAuthorId() != null
                && parentPostAuthorId.equals(comment.getAuthorId());

        boolean isFirstReplyToPost = existingReplyCount == 0;
        if (isSelfReplyToOwnPost && isFirstReplyToPost) {
            isFirstReplyToPost = false;
            log.info("[CommentService] First-reply badge check blocked for self-reply. postId={}, authorId={}",
                    comment.getPostId(), comment.getAuthorId());
        } else {
            log.info("[CommentService] First-reply badge check evaluated. postId={}, existingReplies={}, isFirst={}",
                    comment.getPostId(), existingReplyCount, isFirstReplyToPost);
        }

        long replyLatencySeconds = calculateReplyLatencySecondsUtc(parentPost, now);

        comment.setCreatedAt(now);
        Comment savedComment = commentRepository.save(comment);
        String postId = comment.getPostId();

        int parentPostTotalReplyCount = (int) (existingReplyCount + 1);

        String parentPostType = getParentPostType(parentPost);
        String parentPostScope = normalizeParentPostScope(comment.getScope());
        try {
            log.info("BREADCRUMB 1: Comment saved. Preparing to publish ReplyCreatedEvent.");
            eventPublisher.publishEvent(new ReplyCreatedEvent(
                    comment.getAuthorId(),
                    postId,
                    savedComment.getId(),
                    savedComment.getContent(),
                    parentPostType,
                    parentPostScope,
                    parentPostAuthorId,
                    parentPostTotalReplyCount,
                    isFirstReplyToPost,
                    replyLatencySeconds,
                    parentPost != null ? parentPost.getCreatedAt() : null,
                    now));
            log.info("BREADCRUMB 2: ReplyCreatedEvent published successfully for PostID: {}", postId);
        } catch (Exception e) {
            log.error("FAILED to publish event: ", e);
            throw e;
        }

        // ✅ TRACK REPLY CONTEXT: Dispatch one contextual hard-mode reply event
        if (comment.getAuthorId() != null) {
            try {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("postType", comment.getPostType());
                metadata.put("scope", comment.getScope());

                boolean isMidnight = isMidnightReply(now);
                boolean isFast = isFastReply(parentPost, now);
                boolean isHelp = isHelpPost(parentPost, comment);

                metadata.put("midnight", isMidnight);
                metadata.put("fast", isFast);
                metadata.put("help", isHelp);

                achievementService.checkHardMode(comment.getAuthorId(), "reply", metadata);

                System.out.println(
                        "[CommentService] ✅ Reply tracked for badge progress - user: " + comment.getAuthorId());
            } catch (Exception e) {
                System.err.println("[CommentService] ⚠️ Error tracking reply: " + e.getMessage());
            }
        }

        return savedComment;
    }

    private boolean isMidnightReply(LocalDateTime now) {
        LocalTime istTime = now
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                .toLocalTime();
        return !istTime.isBefore(LocalTime.of(2, 0)) && istTime.isBefore(LocalTime.of(4, 0));
    }

    private Post getParentPost(String postId) {
        if (postId == null || postId.isEmpty()) {
            return null;
        }
        return postRepository.findById(postId).orElse(null);
    }

    private boolean isFastReply(Post post, LocalDateTime now) {
        if (post == null || post.getCreatedAt() == null) {
            return false;
        }

        Duration delta = Duration.between(post.getCreatedAt(), now);
        return !delta.isNegative() && delta.toMinutes() < 5;
    }

    private long calculateReplyLatencySecondsUtc(Post parentPost, LocalDateTime replyCreatedAt) {
        if (parentPost == null || parentPost.getCreatedAt() == null || replyCreatedAt == null) {
            return 0L;
        }

        long postEpochSeconds = parentPost.getCreatedAt().toEpochSecond(ZoneOffset.UTC);
        long replyEpochSeconds = replyCreatedAt.toEpochSecond(ZoneOffset.UTC);
        long deltaSeconds = replyEpochSeconds - postEpochSeconds;

        return Math.max(deltaSeconds, 0L);
    }

    private boolean isHelpPost(Post post, Comment comment) {
        if (post instanceof SocialPost socialPost && socialPost.getType() == PostType.ASK_HELP) {
            return true;
        }

        String postType = comment.getPostType();
        return postType != null && postType.toLowerCase().contains("help");
    }

    private String getParentPostType(Post post) {
        if (post instanceof SocialPost socialPost) {
            if (socialPost.getType() == PostType.COLLAB) {
                return "COLLAB_ROOM";
            }
            return socialPost.getType() != null ? socialPost.getType().name() : "UNKNOWN";
        }
        return "UNKNOWN";
    }

    private String normalizeParentPostScope(String rawScope) {
        if (rawScope == null || rawScope.isBlank()) {
            return "UNKNOWN";
        }

        if ("GLOBAL".equalsIgnoreCase(rawScope) || "GLOBAL_HUB".equalsIgnoreCase(rawScope)) {
            return "GLOBAL_HUB";
        }

        return rawScope;
    }

    /**
     * Get a comment by ID
     */
    @SuppressWarnings("null")
    public Optional<Comment> getCommentById(String id) {
        return commentRepository.findById((String) id);
    }

    /**
     * Delete a comment and its replies (cascade delete)
     */
    @SuppressWarnings("null")
    public void deleteComment(String commentId) {
        Optional<Comment> commentOpt = commentRepository.findById(commentId);
        if (commentOpt.isEmpty()) {
            return;
        }

        Comment comment = commentOpt.get();

        // Delete all replies first (recursively)
        if (comment.getReplyIds() != null && !comment.getReplyIds().isEmpty()) {
            for (String replyId : comment.getReplyIds()) {
                deleteComment(replyId);
            }
        }

        // Delete the comment itself
        commentRepository.deleteById(commentId);
    }

    /**
     * Get comments filtered by scope (CAMPUS or GLOBAL)
     */
    public List<Comment> getCommentsByScope(String scope) {
        return commentRepository.findByScopeOrderByCreatedAtDesc(scope);
    }

    /**
     * Get comments filtered by post type
     */
    public List<Comment> getCommentsByPostType(String postType) {
        return commentRepository.findByPostTypeOrderByCreatedAtDesc(postType);
    }
}
