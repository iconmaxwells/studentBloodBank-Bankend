package com.bloodbank.bloodbank.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class InventoryWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket connected: {}", session.getId());
        send(session, Map.of("event", "connected", "message", "Subscribed to live inventory monitoring"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket disconnected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        send(session, Map.of("event", "ack", "message", "Event stream active"));
    }

    public void broadcast(String event, Map<String, Object> payload) {
        Map<String, Object> message = Map.of(
                "event", event,
                "timestamp", java.time.Instant.now().toString(),
                "data", payload
        );
        sessions.forEach(session -> send(session, message));
    }

    private void send(WebSocketSession session, Map<String, Object> message) {
        if (!session.isOpen()) {
            sessions.remove(session);
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (Exception e) {
            log.warn("Failed to send WebSocket message: {}", e.getMessage());
            sessions.remove(session);
        }
    }
}
