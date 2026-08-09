package com.bloodbank.bloodbank.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class LiveEventPublisher {

    private final InventoryWebSocketHandler webSocketHandler;

    public void inventoryUpdated(Map<String, Object> payload) {
        webSocketHandler.broadcast("inventory.updated", payload);
    }

    public void inventoryAlert(Map<String, Object> payload) {
        webSocketHandler.broadcast("inventory.alert", payload);
    }

    public void requestCreated(Map<String, Object> payload) {
        webSocketHandler.broadcast("request.created", payload);
    }

    public void requestStatusChanged(Map<String, Object> payload) {
        webSocketHandler.broadcast("request.status_changed", payload);
    }

    public void unitExpiring(Map<String, Object> payload) {
        webSocketHandler.broadcast("unit.expiring", payload);
    }
}
