package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.Comment;
import com.studencollabfin.server.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/comments")
public class CommentController {
    @Autowired
    private CommentService commentService;

    @GetMapping("/post/{postId}")
    public List<Comment> getCommentsForPost(@PathVariable String postId) {
        return commentService.getCommentsForPost(postId);
    }

    @GetMapping("/replies/{postId}/{parentId}")
    public List<Comment> getReplies(@PathVariable String postId, @PathVariable String parentId) {
        return commentService.getReplies(postId, parentId);
    }

    @PostMapping
    public Comment addComment(@RequestBody Comment comment) {
        return commentService.addComment(comment);
    }

    @GetMapping("/{id}")
    public Optional<Comment> getCommentById(@PathVariable String id) {
        return commentService.getCommentById(id);
    }
}
