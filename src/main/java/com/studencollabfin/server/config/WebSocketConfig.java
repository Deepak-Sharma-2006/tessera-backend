package com.studencollabfin.server.config;

import com.studencollabfin.server.model.User;
import com.studencollabfin.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
        private final JwtUtil jwtUtil;
        private final UserRepository userRepository;

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
        public void configureClientInboundChannel(@org.springframework.lang.NonNull ChannelRegistration registration) {
                registration.interceptors(new ChannelInterceptor() {
                        @Override
                        public Message<?> preSend(@org.springframework.lang.NonNull Message<?> message,
                                        @org.springframework.lang.NonNull MessageChannel channel) {
                                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                                if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
                                        return message;
                                }

                                String authorization = accessor.getFirstNativeHeader("Authorization");
                                if (authorization == null || authorization.isBlank()) {
                                        authorization = accessor.getFirstNativeHeader("authorization");
                                }

                                if (authorization == null || !authorization.startsWith("Bearer ")) {
                                        return message;
                                }

                                String token = authorization.substring(7).trim();
                                if (token.isEmpty()) {
                                        return message;
                                }

                                try {
                                        String email = jwtUtil.getUsernameFromToken(token);
                                        User user = userRepository.findByEmail(email).orElse(null);
                                        if (user == null || user.getId() == null || user.getId().isBlank()) {
                                                return message;
                                        }

                                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                                        user.getId(),
                                                        null,
                                                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
                                        accessor.setUser(authentication);
                                        String userId = user.getId();
                                        log.info("BREADCRUMB WS: WebSocket Connected & Authenticated for UserID: {}",
                                                        userId);
                                } catch (Exception e) {
                                        log.error("WebSocket CONNECT authentication failed", e);
                                }

                                return message;
                        }
                });
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
