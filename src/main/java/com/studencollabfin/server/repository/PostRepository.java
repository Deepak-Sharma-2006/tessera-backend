
package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.Post;
import com.studencollabfin.server.model.TeamFindingPost;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends MongoRepository<Post, String> {
    // This single repository can now find, save, and delete both
    // TeamFindingPost and SocialPost objects.

    // Custom query to find TeamFindingPosts by eventId
    List<TeamFindingPost> findByEventId(String eventId);
}
