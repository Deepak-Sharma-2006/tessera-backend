package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.Comment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends MongoRepository<Comment, String> {
    List<Comment> findByPostIdAndParentId(String postId, String parentId);

    List<Comment> findByPostId(String postId);
}
