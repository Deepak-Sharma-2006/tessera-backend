
package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.Post;
import com.studencollabfin.server.model.SocialPost;
import com.studencollabfin.server.model.TeamFindingPost;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends MongoRepository<Post, String> {
    // This single repository can now find, save, and delete both
    // TeamFindingPost and SocialPost objects.

    // Custom query to find TeamFindingPosts by eventId
    List<TeamFindingPost> findByEventId(String eventId);

    // Custom query to find a post by linkedPodId (for cascade delete)
    Optional<SocialPost> findByLinkedPodId(String linkedPodId);

    // Campus isolation: Find all posts by college (SocialPost and TeamFindingPost)
    List<Post> findByCollege(String college);

    // ✅ NEW: Find posts created before a specific time (for expiry detection)
    List<Post> findByCreatedAtBefore(LocalDateTime dateTime);
}
