package com.studencollabfin.server.dto;

import lombok.Data;

@Data
public class CommentRequest {
    private String content;
    private String parentId; // optional: id of parent comment
    private String authorName; // name of the commenter (for backward compatibility)
    private String senderId; // ID of the sender
    private String senderName; // Name of the sender

    // Reply and attachment fields
    private String replyToId; // ID of the message being replied to
    private String replyToName; // Name of the person being replied to
    private String replyToContent; // Snippet of the text being replied to
    private String attachmentUrl; // URL of the uploaded file/image
    private String attachmentType; // NONE, IMAGE, FILE
}
