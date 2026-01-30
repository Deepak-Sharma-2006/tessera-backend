package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.Comment;
import com.studencollabfin.server.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/comments")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class CommentController {
    @Autowired
    private CommentService commentService;

    /**
     * Get top-level comments for a post
     */
    @GetMapping("/post/{postId}")
    public List<Comment> getCommentsForPost(@PathVariable String postId) {
        return commentService.getCommentsForPost(postId);
    }

    /**
     * Get all comments for a post (including nested)
     */
    @GetMapping("/post/{postId}/all")
    public List<Comment> getAllCommentsForPost(@PathVariable String postId) {
        return commentService.getAllCommentsForPost(postId);
    }

    /**
     * Get replies to a specific comment
     */
    @GetMapping("/replies/{postId}/{parentId}")
    public List<Comment> getReplies(@PathVariable String postId, @PathVariable String parentId) {
        return commentService.getReplies(postId, parentId);
    }

    /**
     * Get a specific comment by ID
     */
    @GetMapping("/{id}")
    public Optional<Comment> getCommentById(@PathVariable String id) {
        return commentService.getCommentById(id);
    }

    /**
     * Add a comment (primarily for REST - WebSocket is preferred for real-time)
     */
    @PostMapping
    public Comment addComment(@RequestBody Comment comment) {
        return commentService.addComment(comment);
    }

    /**
     * Delete a comment and its replies
     */
    @DeleteMapping("/{id}")
    public void deleteComment(@PathVariable String id) {
        commentService.deleteComment(id);
    }

    /**
     * Get comments by scope (CAMPUS or GLOBAL)
     */
    @GetMapping("/scope/{scope}")
    public List<Comment> getCommentsByScope(@PathVariable String scope) {
        return commentService.getCommentsByScope(scope);
    }

    /**
     * Get comments by post type (CAMPUS_POST or DISCUSSION)
     */
    @GetMapping("/type/{postType}")
    public List<Comment> getCommentsByPostType(@PathVariable String postType) {
        return commentService.getCommentsByPostType(postType);
    }
}
