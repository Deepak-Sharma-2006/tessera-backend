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

    public List<Comment> getCommentsForPost(String postId) {
        return commentRepository.findByPostIdAndParentId(postId, null);
    }

    public List<Comment> getReplies(String postId, String parentId) {
        return commentRepository.findByPostIdAndParentId(postId, parentId);
    }

    public Comment addComment(Comment comment) {
        comment.setCreatedAt(LocalDateTime.now());
        return commentRepository.save(comment);
    }

    @SuppressWarnings("null")
    public Optional<Comment> getCommentById(String id) {
        return commentRepository.findById((String) id);
    }
}
