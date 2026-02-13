package com.studencollabfin.server.dto;

import com.studencollabfin.server.model.Conversation;
import com.studencollabfin.server.model.User;
import java.util.Date;

/**
 * ✅ Enhanced conversation response that includes initiator (sender) details
 * Used for inbox display so app can show: name, college, department, etc.
 */
public class ConversationInviteResponse {
    private String conversationId;
    private String initiatorId;
    private String initiatorName;
    private String initiatorCollege;
    private String initiatorDepartment;
    private String status; // "PENDING" or "ACCEPTED"
    private Date createdAt;
    private Date updatedAt;

    public ConversationInviteResponse(Conversation conversation, User initiator) {
        this.conversationId = conversation.getId();
        this.initiatorId = conversation.getInitiatorId();
        this.initiatorName = initiator != null ? initiator.getFullName() : "Unknown";
        this.initiatorCollege = initiator != null ? initiator.getCollegeName() : "Unknown";
        this.initiatorDepartment = initiator != null ? initiator.getDepartment() : "Unknown";
        this.status = conversation.getStatus();
        this.createdAt = conversation.getCreatedAt();
        this.updatedAt = conversation.getUpdatedAt();
    }

    // Getters
    public String getConversationId() {
        return conversationId;
    }

    public String getInitiatorId() {
        return initiatorId;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public String getInitiatorCollege() {
        return initiatorCollege;
    }

    public String getInitiatorDepartment() {
        return initiatorDepartment;
    }

    public String getStatus() {
        return status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }
}
