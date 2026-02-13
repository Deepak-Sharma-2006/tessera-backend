package com.studencollabfin.server.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "collabpoddata")
public class PodMessage {
    @Id
    private String id;

    @Indexed
    private String podId;

    private String senderId;
    private String senderName;
    private String content;

    // Reply and attachment fields
    private String replyToId;
    private String replyToName;
    private String replyToContent;
    private String attachmentUrl;
    private String attachmentType; // IMAGE, FILE, NONE

    @Indexed(expireAfterSeconds = 259200) // 3 days TTL
    private LocalDateTime timestamp;

    public PodMessage() {
        this.timestamp = LocalDateTime.now();
        this.attachmentType = "NONE";
    }

    public PodMessage(String podId, String senderId, String senderName, String content) {
        this.podId = podId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.timestamp = LocalDateTime.now();
        this.attachmentType = "NONE";
    }
}
