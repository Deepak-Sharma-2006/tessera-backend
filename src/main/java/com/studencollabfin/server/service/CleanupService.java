package com.studencollabfin.server.service;

import com.studencollabfin.server.model.TeamFindingPost;
import com.studencollabfin.server.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CleanupService {

    private final PostRepository postRepository;

    /**
     * ✅ Scheduled task that runs every hour to delete TeamFindingPost older than 24
     * hours.
     * This ensures that posts automatically expire after their 24-hour lifecycle.
     */
    @Scheduled(fixedDelay = 3600000) // Run every 1 hour (3600000 ms)
    public void deleteExpiredTeamFindingPosts() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);

            // Get all posts and filter for TeamFindingPost instances
            List<TeamFindingPost> expiredPosts = postRepository.findAll().stream()
                    .filter(p -> p instanceof TeamFindingPost)
                    .map(p -> (TeamFindingPost) p)
                    .filter(p -> {
                        LocalDateTime createdAt = p.getCreatedAt();
                        return createdAt != null && createdAt.isBefore(cutoffTime);
                    })
                    .toList();

            // Delete expired posts
            for (TeamFindingPost post : expiredPosts) {
                try {
                    String postId = post.getId();
                    if (postId != null) {
                        postRepository.deleteById(postId);
                        System.out.println("✅ Deleted expired TeamFindingPost: " + postId + " (created: "
                                + post.getCreatedAt() + ")");
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error deleting post " + post.getId() + ": " + e.getMessage());
                }
            }

            if (expiredPosts.size() > 0) {
                System.out.println("🧹 Cleanup completed: Deleted " + expiredPosts.size() + " expired posts.");
            }
        } catch (Exception e) {
            System.err.println("❌ Error in cleanup service: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
