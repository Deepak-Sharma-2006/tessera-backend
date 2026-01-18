
package com.studencollabfin.server.repository;

import com.studencollabfin.server.model.Post;
import com.studencollabfin.server.model.TeamFindingPost;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends MongoRepository<Post, String> {
    // This single repository can now find, save, and delete both
    // TeamFindingPost and SocialPost objects.

    // Custom query to find TeamFindingPosts by eventId
    List<TeamFindingPost> findByEventId(String eventId);

    // Delete SocialPost by linkedPodId (cascade delete for pods)
    // Uses MongoDB query syntax to delete documents with matching linkedPodId
    // Returns the number of deleted documents
    // ✅ CRITICAL: delete = true forces a delete operation instead of
    // find-and-remove
    @Query(value = "{ 'linkedPodId': ?0 }", delete = true)
    Long deleteByLinkedPodId(String linkedPodId);
}
