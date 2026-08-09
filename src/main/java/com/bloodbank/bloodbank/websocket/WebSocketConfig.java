package com.bloodbank.bloodbank.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final InventoryWebSocketHandler inventoryWebSocketHandler;
    private final JwtWebSocketInterceptor jwtWebSocketInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(inventoryWebSocketHandler, "/ws/inventory")
                .addInterceptors(jwtWebSocketInterceptor)
                .setAllowedOrigins("*");
    }
}
