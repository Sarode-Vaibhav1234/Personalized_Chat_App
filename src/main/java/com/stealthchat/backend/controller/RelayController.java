package com.stealthchat.backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Controller
public class RelayController {

    private static final Logger logger = LoggerFactory.getLogger(RelayController.class);

    private final SimpMessagingTemplate messagingTemplate;

    // In-memory queues for offline messages and keys
    private final ConcurrentHashMap<String, Queue<Map<String, Object>>> messageQueue = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Queue<Map<String, Object>>> keysQueue = new ConcurrentHashMap<>();

    private static final int MAX_QUEUE_SIZE = 100;

    @Autowired
    public RelayController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Handles incoming chat messages from a client.
     */
    @MessageMapping("/chat.send")
    public void relayMessage(@Payload Map<String, Object> payload) {
        String recipientId = (String) payload.get("recipientId");
        String messageId = (String) payload.get("messageId");
        
        logger.info("Received message to relay. ID: {}, Target: {}", messageId, recipientId);

        // Buffer the message in case the recipient is offline
        Queue<Map<String, Object>> queue = messageQueue.computeIfAbsent(recipientId, k -> new ConcurrentLinkedQueue<>());
        if (queue.size() >= MAX_QUEUE_SIZE) {
            queue.poll(); // Evict oldest
        }
        queue.add(payload);

        // Try to relay immediately in case they are online
        messagingTemplate.convertAndSend("/topic/messages/" + recipientId, payload);
    }

    /**
     * Handles incoming public key exchange broadcasts.
     */
    @MessageMapping("/chat.keys")
    public void relayKeys(@Payload Map<String, Object> payload) {
        String recipientId = (String) payload.getOrDefault("recipientId", "THE_OTHER_USER");
        String senderId = (String) payload.get("senderId");
        
        logger.info("Relaying public key from {} to {}", senderId, recipientId);
        
        // Buffer the key in case the recipient is offline
        Queue<Map<String, Object>> queue = keysQueue.computeIfAbsent(recipientId, k -> new ConcurrentLinkedQueue<>());
        if (queue.size() >= MAX_QUEUE_SIZE) {
            queue.poll(); // Evict oldest
        }
        queue.add(payload);

        messagingTemplate.convertAndSend("/topic/keys/" + recipientId, payload);
    }

    /**
     * Handles incoming read/delivery receipts.
     * We don't buffer these to save memory, they are opportunistic.
     */
    @MessageMapping("/chat.receipt")
    public void relayReceipt(@Payload Map<String, Object> payload) {
        String recipientId = (String) payload.getOrDefault("recipientId", "THE_OTHER_USER");
        messagingTemplate.convertAndSend("/topic/receipts/" + recipientId, payload);
    }

    /**
     * Handles typing indicators.
     * We don't buffer typing indicators.
     */
    @MessageMapping("/chat.typing")
    public void relayTyping(@Payload Map<String, Object> payload) {
        String recipientId = (String) payload.getOrDefault("recipientId", "THE_OTHER_USER");
        messagingTemplate.convertAndSend("/topic/typing/" + recipientId, payload);
    }

    /**
     * Listens for clients subscribing to topics.
     * If they subscribe to their message or key topics, we flush any buffered payloads to them.
     */
    @EventListener
    public void handleSubscribeEvent(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();

        if (destination == null) return;

        if (destination.startsWith("/topic/messages/")) {
            String userId = destination.substring("/topic/messages/".length());
            flushQueue(userId, messageQueue, destination);
        } else if (destination.startsWith("/topic/keys/")) {
            String userId = destination.substring("/topic/keys/".length());
            flushQueue(userId, keysQueue, destination);
        }
    }

    private void flushQueue(String userId, ConcurrentHashMap<String, Queue<Map<String, Object>>> queueMap, String destination) {
        Queue<Map<String, Object>> queue = queueMap.get(userId);
        if (queue != null && !queue.isEmpty()) {
            logger.info("Flushing {} buffered payloads to {}", queue.size(), destination);
            Map<String, Object> payload;
            while ((payload = queue.poll()) != null) {
                messagingTemplate.convertAndSend(destination, payload);
            }
        }
    }
}
