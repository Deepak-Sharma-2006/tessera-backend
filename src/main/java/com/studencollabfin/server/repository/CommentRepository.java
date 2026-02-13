package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.Comment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends MongoRepository<Comment, String> {
    // Get comments with specific parent (null for top-level)
    List<Comment> findByPostIdAndParentId(String postId, String parentId);

    // Get all comments for a post
    List<Comment> findByPostId(String postId);

    // Get comments by scope (CAMPUS or GLOBAL)
    List<Comment> findByScopeOrderByCreatedAtDesc(String scope);

    // Get comments by post type
    List<Comment> findByPostTypeOrderByCreatedAtDesc(String postType);

    // Delete comments by post
    void deleteByPostId(String postId);

    // Delete comments by scope
    void deleteByScope(String scope);
}
