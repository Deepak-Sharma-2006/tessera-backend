package com.studencollabfin.server.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
public class Message {
    @Id
    private String id;
    private String conversationId; // Maps to podId
    private String podId; // Alias for conversationId
    private String senderId;
    private String senderName;
    private String text;
    private String content; // Alternative field name for text
    private List<String> attachmentUrls; // URLs to files/images
    private Date sentAt;
    private boolean read;
}
