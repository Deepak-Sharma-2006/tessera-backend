package com.studencollabfin.server.dto;

import lombok.Data;

@Data
public class CommentRequest {
    private String content;
    private String parentId; // optional: id of parent comment
    private String authorName; // name of the commenter
}
