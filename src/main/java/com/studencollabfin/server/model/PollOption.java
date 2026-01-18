package com.studencollabfin.server.model;

import lombok.Data;
import java.util.List;

@Data
public class PollOption {
    private String text;
    private List<String> votes; // List of user IDs who voted for this option
}
