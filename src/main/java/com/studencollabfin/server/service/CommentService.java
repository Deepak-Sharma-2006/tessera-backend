package com.studencollabfin.server.service;

import com.studencollabfin.server.model.Comment;
import com.studencollabfin.server.repository.CommentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CommentService {
    @Autowired
    private CommentRepository commentRepository;

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
        comment.setCreatedAt(LocalDateTime.now());
        return commentRepository.save(comment);
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
