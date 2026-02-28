package com.studencollabfin.server.gamification.event;

import java.time.LocalDateTime;

public record ReplyCreatedEvent(
        String userId,
        String postId,
        String commentId,
        String replyContent,
        String parentPostType,
        String parentPostScope,
        String parentPostAuthorId,
        int parentPostTotalReplyCount,
        boolean isFirstReplyToPost,
        long replyLatencySeconds,
        LocalDateTime postCreatedAt,
        LocalDateTime replyCreatedAt) {
}
