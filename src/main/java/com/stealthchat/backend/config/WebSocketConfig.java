package com.stealthchat.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple memory-based message broker to carry the messages back to the client
        config.enableSimpleBroker("/user", "/topic");
        
        // Prefix for messages BOUND for the server (from the client)
        config.setApplicationDestinationPrefixes("/app");
        
        // This makes sure messages sent to a specific user using SimpMessagingTemplate 
        // will route to /user/{username}/queue/...
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // The endpoint clients will use to connect to our WebSocket server
        // No SockJS here because we use a raw OkHttp WebSocket client on Android
        registry.addEndpoint("/ws-stealth").setAllowedOriginPatterns("*");
    }
}
