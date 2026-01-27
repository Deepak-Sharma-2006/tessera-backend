package com.studencollabfin.server.model;

public enum PostType {
    GENERAL("Post"),
    DISCUSSION("Discussion"), // Added
    COLLAB("Collab"), // Added
    ASK_HELP("Ask for Help"),
    OFFER_HELP("Offer Help"),
    POLL("Create Poll"),
    LOOKING_FOR("Looking For...");

    private final String label;

    PostType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
