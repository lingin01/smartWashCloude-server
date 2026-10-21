package com.company.project.controller;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/ws")
public class WebSocketController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);
    private static final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket connected: {}", session.getId());
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session.getId());
        log.info("WebSocket disconnected: {}", session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        log.debug("WebSocket message from {}: {}", session.getId(), message);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket error: session={}, error={}", session.getId(), error.getMessage());
        sessions.remove(session.getId());
    }

    public void broadcast(String message) {
        sessions.values().forEach(session -> {
            try {
                if (session.isOpen()) {
                    session.getBasicRemote().sendText(message);
                }
            } catch (IOException e) {
                log.error("WebSocket send error: {}", e.getMessage());
            }
        });
    }

    public void sendToSession(String sessionId, String message) {
        Session session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.error("WebSocket send error: {}", e.getMessage());
            }
        }
    }

    public int getOnlineCount() {
        return sessions.size();
    }
}
