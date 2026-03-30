package com.scriptoria.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scriptoria.dto.ScreenplayRequest;
import com.scriptoria.security.AppUserDetailsService;
import com.scriptoria.service.AnalysisPipelineService;
import com.scriptoria.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisWebSocketHandler extends TextWebSocketHandler {

    private final AnalysisPipelineService pipelineService;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractToken(session.getUri());
        if (token == null || token.isBlank()) {
            sendErrorAndClose(session, "Missing token");
            return;
        }

        try {
            String email = jwtService.extractEmail(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            if (!jwtService.isTokenValid(token, userDetails)) {
                sendErrorAndClose(session, "Invalid token");
                return;
            }
            session.getAttributes().put("userEmail", email);
            log.info("WS client authenticated: session={} email={}", session.getId(), email);
        } catch (Exception ex) {
            log.warn("WS auth failed for session {}: {}", session.getId(), ex.getMessage());
            sendErrorAndClose(session, "Invalid token");
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.info("WS message received from {}, length={}", session.getId(), message.getPayloadLength());

        executor.submit(() -> {
            try {
                ScreenplayRequest request = objectMapper.readValue(message.getPayload(), ScreenplayRequest.class);
                if (request.getScreenplay() == null || request.getScreenplay().isBlank()) {
                    sendError(session, "Screenplay text must not be empty");
                    return;
                }
                pipelineService.runPipeline(session, request);
            } catch (Exception e) {
                log.error("WS handler error: {}", e.getMessage(), e);
                sendError(session, "Failed to parse request: " + e.getMessage());
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WS client disconnected: {} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WS transport error on session {}: {}", session.getId(), exception.getMessage());
    }

    private String extractToken(URI uri) {
        if (uri == null) return null;
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("token");
    }

    private void sendErrorAndClose(WebSocketSession session, String msg) {
        sendError(session, msg);
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } catch (Exception e) {
            log.error("Failed to close WS session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String msg) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("type", "ERROR", "message", msg));
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.error("Failed to send WS error: {}", e.getMessage());
        }
    }
}
