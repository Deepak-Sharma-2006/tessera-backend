package com.studencollabfin.server.service;

import com.studencollabfin.server.model.Chat;
import com.studencollabfin.server.repository.ChatRepository;
import com.studencollabfin.server.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@SuppressWarnings("null")
public class ChatService {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private UserRepository userRepository;

    public Chat startChat(String senderId, String receiverId) {
        // Verify both users exist
        userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        userRepository.findById(receiverId)
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        // Check if chat already exists
        Chat existingChat = chatRepository.findBySenderIdAndReceiverId(senderId, receiverId);
        if (existingChat != null) {
            return existingChat;
        }

        Chat chat = new Chat();
        chat.setSenderId(senderId);
        chat.setReceiverId(receiverId);
        chat.setMessages(new java.util.ArrayList<>());
        chat.setLastActivity(LocalDateTime.now());

        return chatRepository.save(chat);
    }

    public Chat sendMessage(String chatId, String senderId, String content) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        if (!chat.getSenderId().equals(senderId) && !chat.getReceiverId().equals(senderId)) {
            throw new RuntimeException("Not authorized to send message in this chat");
        }

        Chat.Message message = new Chat.Message();
        message.setSenderId(senderId);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());
        message.setType(Chat.MessageType.TEXT);

        chat.getMessages().add(message);
        chat.setLastActivity(LocalDateTime.now());

        return chatRepository.save(chat);
    }

    public Chat markMessagesAsRead(String chatId, String userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        if (!chat.getSenderId().equals(userId) && !chat.getReceiverId().equals(userId)) {
            throw new RuntimeException("Not authorized to access this chat");
        }

        for (Chat.Message message : chat.getMessages()) {
            if (!message.getSenderId().equals(userId)) {
                message.setRead(true);
            }
        }

        return chatRepository.save(chat);
    }

    public void reportChat(String chatId, String reporterId, String reason) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        if (!chat.getSenderId().equals(reporterId) && !chat.getReceiverId().equals(reporterId)) {
            throw new RuntimeException("Not authorized to report this chat");
        }

        chat.setReported(true);
        chatRepository.save(chat);
        // Additional logic to notify moderators would go here
    }

    public List<Chat> getUserChats(String userId) {
        List<Chat> sentChats = chatRepository.findBySenderId(userId);
        List<Chat> receivedChats = chatRepository.findByReceiverId(userId);
        sentChats.addAll(receivedChats);
        return sentChats;
    }
}
