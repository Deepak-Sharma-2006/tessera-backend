package com.studencollabfin.server.websocket;

import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.service.MessagingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class MessagingWebSocketController {
    @Autowired
    private MessagingService messagingService;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @SuppressWarnings("null")
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload Message message) {
        // Save message to DB
        Message saved = messagingService.sendMessage(
                message.getConversationId(),
                message.getSenderId(),
                message.getText(),
                message.getAttachmentUrls());
        // Send to topic for conversation
        messagingTemplate.convertAndSend("/topic/conversation." + message.getConversationId(), (Object) saved);
    }
}
