package com.studencollabfin.server.service;

import com.studencollabfin.server.model.Conversation;
import com.studencollabfin.server.model.Message;
import com.studencollabfin.server.repository.ConversationRepository;
import com.studencollabfin.server.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class MessagingService {
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private MessageRepository messageRepository;

    public List<Conversation> getUserConversations(String userId) {
        return conversationRepository.findByParticipantIdsContaining(userId);
    }

    public Optional<Conversation> getConversation(String id) {
        if (id == null)
            return Optional.empty();
        return conversationRepository.findById(id);
    }

    public Conversation createConversation(List<String> participantIds) {
        Conversation conv = new Conversation();
        conv.setParticipantIds(participantIds);
        conv.setCreatedAt(new Date());
        conv.setUpdatedAt(new Date());
        return conversationRepository.save(conv);
    }

    public Message sendMessage(String conversationId, String senderId, String text, List<String> attachmentUrls) {
        Message msg = new Message();
        msg.setConversationId(conversationId);
        msg.setSenderId(senderId);
        msg.setText(text);
        msg.setAttachmentUrls(attachmentUrls);
        msg.setSentAt(new Date());
        msg.setRead(false);
        if (conversationId != null) {
            Conversation conv = conversationRepository.findById(conversationId).orElseThrow();
            conv.setUpdatedAt(new Date());
            conversationRepository.save(conv);
        }
        return messageRepository.save(msg);
    }

    public List<Message> getMessages(String conversationId) {
        return messageRepository.findByConversationIdOrderBySentAtAsc(conversationId);
    }
}
