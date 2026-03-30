package com.scriptoria.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "Service health and info")
public class HealthController {

    @Value("${openrouter.model:openai/gpt-oss-120b}")
    private String model;

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Scriptoria",
                "model", model,
                "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/info")
    @Operation(summary = "API endpoint summary")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
                "websocket", Map.of(
                        "url", "ws://<host>/ws/analyze",
                        "description", "Full analysis pipeline (script → emotion → budget). Send screenplay JSON, receive streamed events.",
                        "inputFormat", Map.of("screenplay", "string", "market", "TOLLYWOOD|BOLLYWOOD|HOLLYWOOD|KOREAN|GENERAL")
                ),
                "rest", Map.of(
                        "POST /api/v1/storyboard/generate", "Generate 6 cinematic shot variations for a scene (Director Mode)",
                        "GET /api/v1/health", "Service health check",
                        "GET /swagger-ui.html", "Interactive API documentation"
                )
        ));
    }
}
