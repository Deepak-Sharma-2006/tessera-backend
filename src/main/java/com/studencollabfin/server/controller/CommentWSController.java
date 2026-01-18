package com.studencollabfin.server.controller;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.studencollabfin.server.dto.CommentRequest;
import com.studencollabfin.server.model.PodMessage;
import com.studencollabfin.server.model.SocialPost;
import com.studencollabfin.server.repository.PodMessageRepository;
import com.studencollabfin.server.service.PodMessageService;
import com.studencollabfin.server.service.PostService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CommentWSController {
    private final PostService postService;
    private final SimpMessagingTemplate messagingTemplate;
    private final PodMessageRepository podMessageRepository;
    private final PodMessageService podMessageService;

    @MessageMapping("/post.{postId}.comment")
    public void handleComment(@DestinationVariable String postId, CommentRequest payload) {
        // Ensure the post is retrieved and updated in a persisted manner
        SocialPost.Comment saved = postService.addCommentToPost(postId, payload);

        // Only broadcast after save
        java.util.Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("comment", saved);
        envelope.put("parentId", payload.getParentId());
        messagingTemplate.convertAndSend(String.format("/topic/post.%s.comments", postId), envelope);
    }

    @MessageMapping("/pod.{podId}.chat")
    public void handlePodChat(@DestinationVariable String podId, CommentRequest payload) {
        // Create and persist message to PodMessage collection (NOT to
        // CollabPod.messages)
        try {
            // Use senderId and senderName from payload, fall back to authorName if needed
            String senderId = payload.getSenderId();
            String senderName = payload.getSenderName() != null ? payload.getSenderName() : payload.getAuthorName();

            PodMessage msg = new PodMessage(podId, senderId, senderName, payload.getContent());

            // Set reply and attachment fields
            msg.setReplyToId(payload.getReplyToId());
            msg.setReplyToName(payload.getReplyToName());
            msg.setReplyToContent(payload.getReplyToContent());
            msg.setAttachmentUrl(payload.getAttachmentUrl());
            msg.setAttachmentType(payload.getAttachmentType() != null ? payload.getAttachmentType() : "NONE");

            // Save to PodMessage collection with TTL
            PodMessage saved = podMessageService.saveMessage(msg);

            // Broadcast the saved message
            java.util.Map<String, Object> envelope = new java.util.HashMap<>();
            envelope.put("message", saved);
            messagingTemplate.convertAndSend(String.format("/topic/pod.%s.chat", podId), envelope);
        } catch (Exception ex) {
            System.err.println("Failed to persist/broadcast pod message: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
