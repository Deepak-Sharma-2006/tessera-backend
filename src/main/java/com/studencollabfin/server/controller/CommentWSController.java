package com.studencollabfin.server.controller;

import com.studencollabfin.server.dto.CommentRequest;
import com.studencollabfin.server.model.Comment;
import com.studencollabfin.server.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CommentWSController {
    private final PostService postService;
    private final SimpMessagingTemplate messagingTemplate;

    @SuppressWarnings("null")
    @MessageMapping("/post.{postId}.comment")
    public void handleComment(@DestinationVariable String postId, @Payload CommentRequest payload,
            SimpMessageHeaderAccessor headerAccessor) {
        Object sessionUserIdObj = headerAccessor != null && headerAccessor.getSessionAttributes() != null
                ? headerAccessor.getSessionAttributes().get("userId")
                : null;
        String userId = sessionUserIdObj instanceof String ? (String) sessionUserIdObj : null;

        if ((userId == null || userId.isBlank()) && headerAccessor != null && headerAccessor.getUser() != null) {
            userId = headerAccessor.getUser().getName();
        }

        if ((userId == null || userId.isBlank()) && headerAccessor != null) {
            userId = headerAccessor.getFirstNativeHeader("userId");
        }

        log.info("[Comment-WS] Resolution: UserID={}, SessionFound={}, PrincipalFound={}",
                userId,
                sessionUserIdObj != null,
                headerAccessor != null && headerAccessor.getUser() != null);

        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("CRITICAL: WebSocket Auth Context Missing.");
        }

        // Save comment to comments collection
        Comment saved = postService.addCommentToPost(postId, payload, userId);

        // Broadcast the saved comment
        java.util.Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("comment", saved);
        envelope.put("parentId", payload.getParentId());
        messagingTemplate.convertAndSend(String.format("/topic/post.%s.comments", postId), (Object) envelope);
    }
}
