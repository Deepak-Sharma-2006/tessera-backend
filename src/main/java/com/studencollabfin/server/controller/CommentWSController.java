package com.studencollabfin.server.controller;

import com.studencollabfin.server.dto.CommentRequest;
import com.studencollabfin.server.model.SocialPost;
import com.studencollabfin.server.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class CommentWSController {
    private final PostService postService;
    private final SimpMessagingTemplate messagingTemplate;

    @SuppressWarnings("null")
    @MessageMapping("/post.{postId}.comment")
    public void handleComment(@DestinationVariable String postId, CommentRequest payload) {
        // Ensure the post is retrieved and updated in a persisted manner
        SocialPost.Comment saved = postService.addCommentToPost(postId, payload);

        // Only broadcast after save
        java.util.Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("comment", saved);
        envelope.put("parentId", payload.getParentId());
        messagingTemplate.convertAndSend(String.format("/topic/post.%s.comments", postId), (Object) envelope);
    }
}
