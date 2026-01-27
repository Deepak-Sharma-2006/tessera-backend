package com.studencollabfin.server.model;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class PollOption {
    private String id = UUID.randomUUID().toString();
    private String text;
    private List<String> votes; // List of user IDs who voted for this option
}
