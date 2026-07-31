package com.stealthchat.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Controller
public class RelayController {

    private static final Logger logger = LoggerFactory.getLogger(RelayController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public RelayController(SimpMessagingTemplate messagingTemplate, StringRedisTemplate redisTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Handles incoming chat messages from a client.
     * Maps to STOMP destination: /app/chat.send
     */
    @MessageMapping("/chat.send")
    public void relayMessage(@Payload Map<String, Object> payload) {
        String recipientId = (String) payload.get("recipientId");
        String messageId = (String) payload.get("messageId");
        
        logger.info("Received message to relay. ID: {}, Target: {}", messageId, recipientId);

        // Optional: Cache the message in Redis with a 24-hour TTL in case the recipient is offline
        // The key would be a queue list per recipient.
        /* 
        String redisKey = "stealth:offline:" + recipientId;
        try {
            // Serialize and push to list
            String jsonPayload = convertToJson(payload);
            redisTemplate.opsForList().rightPush(redisKey, jsonPayload);
            // Ensure TTL is 24 hours
            redisTemplate.expire(redisKey, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            logger.error("Failed to cache message in Redis", e);
        }
        */

        // Immediately relay to the connected recipient if they are online
        // Destination: /topic/messages/{recipientId}
        messagingTemplate.convertAndSend(
                "/topic/messages/" + recipientId,
                payload
        );
    }

    /**
     * Handles incoming read/delivery receipts.
     * Maps to STOMP destination: /app/chat.receipt
     */
    @MessageMapping("/chat.receipt")
    public void relayReceipt(@Payload Map<String, Object> payload) {
        // We assume the Android client sends recipientId for the receipt target
        String recipientId = (String) payload.getOrDefault("recipientId", "THE_OTHER_USER");
        String messageId = (String) payload.get("messageId");
        
        logger.info("Relaying receipt for message ID: {} to Target: {}", messageId, recipientId);
        
        messagingTemplate.convertAndSend(
                "/topic/receipts/" + recipientId,
                payload
        );
    }

    /**
     * Handles incoming public key exchange broadcasts.
     * Maps to STOMP destination: /app/chat.keys
     */
    @MessageMapping("/chat.keys")
    public void relayKeys(@Payload Map<String, Object> payload) {
        String recipientId = (String) payload.getOrDefault("recipientId", "THE_OTHER_USER");
        String senderId = (String) payload.get("senderId");
        
        logger.info("Relaying public key from {} to {}", senderId, recipientId);
        
        messagingTemplate.convertAndSend(
                "/topic/keys/" + recipientId,
                payload
        );
    }

    /**
     * Handles typing indicators.
     * Maps to STOMP destination: /app/chat.typing
     */
    @MessageMapping("/chat.typing")
    public void relayTyping(@Payload Map<String, Object> payload) {
        String recipientId = (String) payload.getOrDefault("recipientId", "THE_OTHER_USER");
        
        messagingTemplate.convertAndSend(
                "/topic/typing/" + recipientId,
                payload
        );
    }
    
    // Quick and dirty manual serialization to avoid defining a bean or adding Jackson overhead here
    // In production, we'd use Jackson ObjectMapper.
    private String convertToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String) {
                sb.append("\"").append(entry.getValue()).append("\"");
            } else {
                sb.append(entry.getValue());
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
