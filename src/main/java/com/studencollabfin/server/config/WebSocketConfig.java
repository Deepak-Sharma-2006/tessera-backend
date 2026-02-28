package com.studencollabfin.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private static final String[] ALLOWED_ORIGINS = List.of(
            "http://localhost:5173",
            "http://localhost:5174",
            "http://localhost:3000",
            "https://tezzera.netlify.app",
            "https://*.netlify.app").toArray(new String[0]);

    @Override
    public void configureMessageBroker(@org.springframework.lang.NonNull MessageBrokerRegistry config) {
        // Enable simple broker for /topic and /queue destinations
        // User-specific messages will be sent to /user/{userId}/queue/* or
        // /user/{userId}/topic/*
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        // Set the prefix for user-specific destinations
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(@org.springframework.lang.NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(ALLOWED_ORIGINS)
                .withSockJS();

        registry.addEndpoint("/ws-studcollab")
                .setAllowedOriginPatterns(ALLOWED_ORIGINS)
                .withSockJS();

        registry.addEndpoint("/ws-studcollab-mobile")
                .setAllowedOriginPatterns(ALLOWED_ORIGINS);
    }
}
