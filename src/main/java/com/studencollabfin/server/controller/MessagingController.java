package com.studencollabfin.server.controller;

import com.studencollabfin.server.model.Conversation;
import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.service.MessagingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class MessagingController {
    @Autowired
    private MessagingService messagingService;

    @GetMapping("/conversations/{userId}")
    public List<Conversation> getUserConversations(@PathVariable String userId) {
        return messagingService.getUserConversations(userId);
    }

    @PostMapping("/conversations")
    public Conversation createConversation(@RequestBody Map<String, List<String>> body) {
        return messagingService.createConversation(body.get("participantIds"));
    }

    @GetMapping("/conversation/{conversationId}")
    public Conversation getConversation(@PathVariable String conversationId) {
        return messagingService.getConversation(conversationId).orElse(null);
    }

    @GetMapping("/conversation/{conversationId}/messages")
    public List<Message> getMessages(@PathVariable String conversationId) {
        return messagingService.getMessages(conversationId);
    }

    @PostMapping("/conversation/{conversationId}/send")
    public Message sendMessage(@PathVariable String conversationId, @RequestBody Map<String, Object> body) {
        String senderId = (String) body.get("senderId");
        String text = (String) body.get("text");
        @SuppressWarnings("unchecked")
        List<String> attachmentUrls = (List<String>) body.getOrDefault("attachmentUrls", null);
        return messagingService.sendMessage(conversationId, senderId, text, attachmentUrls);
    }
}
