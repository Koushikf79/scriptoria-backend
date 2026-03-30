package com.scriptoria.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Core AI service — calls OpenRouter /v1/chat/completions.
 *
 * Quality-first configuration:
 *  - reasoning_effort: high  (best reasoning quality)
 *  - temperature: 0.2 for structured JSON features (analysis, emotion, budget)
 *  - temperature: 0.85 for creative features (storyboard)
 *  - max_completion_tokens: 8192
 *  - Retry on transient failures
 *  - Larger chunk size (10000 chars) for better scene context on long scripts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final RestTemplate openRouterRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openrouter.api-key}")
    private String apiKey;

    @Value("${openrouter.base-url:https://openrouter.ai/api/v1}")
    private String baseUrl;

    @Value("${openrouter.model:openai/gpt-oss-120b}")
    private String model;

    @Value("${openrouter.max-completion-tokens:8192}")
    private int maxCompletionTokens;

    @Value("${openrouter.top-p:0.95}")
    private double topP;

    @Value("${openrouter.reasoning-effort:high}")
    private String reasoningEffort;

    // Per-feature temperatures
    @Value("${openrouter.temperature-analysis:0.2}")
    private double temperatureAnalysis;

    @Value("${openrouter.temperature-storyboard:0.85}")
    private double temperatureStoryboard;

    @Value("${openrouter.max-retries:2}")
    private int maxRetries;

    @Value("${openrouter.chunk-size-chars:10000}")
    private int defaultChunkSize;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Analytical chat — low temperature for consistent structured output.
     * Use for: script analysis, emotion analysis, budget simulation.
     */
    public String chatAnalytical(String systemPrompt, String userPrompt) {
        return chatWithRetry(systemPrompt, userPrompt, temperatureAnalysis);
    }

    /**
     * Creative chat — higher temperature for varied, imaginative output.
     * Use for: storyboard / director mode.
     */
    public String chatCreative(String systemPrompt, String userPrompt) {
        return chatWithRetry(systemPrompt, userPrompt, temperatureStoryboard);
    }

    /**
     * Analytical JSON call — parses response into target class.
     */
    public <T> T chatJson(String systemPrompt, String userPrompt, Class<T> responseType) {
        return parseJson(chatAnalytical(buildJsonSystem(systemPrompt), userPrompt), responseType);
    }

    /**
     * Creative JSON call — for storyboard generation.
     */
    public <T> T chatJsonCreative(String systemPrompt, String userPrompt, Class<T> responseType) {
        return parseJson(chatCreative(buildJsonSystem(systemPrompt), userPrompt), responseType);
    }

    /**
     * Chunks large screenplay text into segments, preserving scene boundaries.
     * Uses configured chunk size (10000 chars for quality mode).
     */
    public List<String> chunkText(String text) {
        return chunkText(text, defaultChunkSize);
    }

    public List<String> chunkText(String text, int maxChunkChars) {
        if (text.length() <= maxChunkChars) return List.of(text);

        List<String> sceneBlocks = splitBySceneHeading(text);
        if (sceneBlocks.isEmpty()) {
            return chunkByLines(text, maxChunkChars);
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : sceneBlocks) {
            if (block.length() > maxChunkChars) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString());
                    current = new StringBuilder();
                }
                chunks.addAll(chunkByLines(block, maxChunkChars));
                continue;
            }

            if (current.length() + block.length() > maxChunkChars && !current.isEmpty()) {
                chunks.add(current.toString());
                current = new StringBuilder();
            }
            current.append(block);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        log.info("Screenplay chunked into {} parts (chunkSize={})", chunks.size(), maxChunkChars);
        return chunks;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String chatWithRetry(String systemPrompt, String userPrompt, double temperature) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                if (attempt > 1) {
                    log.warn("Retry attempt {}/{} after failure", attempt, maxRetries + 1);
                    Thread.sleep(1500L * (attempt - 1)); // backoff: 1.5s, 3s
                }
                log.debug("OpenRouter call: model={} temp={} attempt={} prompt_len={}",
                        model, temperature, attempt, userPrompt.length());

                Map<String, Object> body = buildBody(systemPrompt, userPrompt, temperature);
                Map<String, Object> response = doPost(body);
                return extractContent(response);

            } catch (HttpClientErrorException e) {
                // Don't retry auth or rate limit errors
                if (e.getStatusCode().value() == 401) throw new RuntimeException("Invalid OpenRouter API key.");
                if (e.getStatusCode().value() == 402) throw new RuntimeException("OpenRouter credits exhausted. Top up at openrouter.ai");
                if (e.getStatusCode().value() == 429) throw new RuntimeException("Rate limit hit. Try again in a moment.");
                if (e.getStatusCode().value() >= 400 && e.getStatusCode().value() < 500) {
                    String body = e.getResponseBodyAsString();
                    throw new RuntimeException("OpenRouter request rejected (" + e.getStatusCode().value() + "): "
                            + (body != null && !body.isBlank() ? body : "no details"));
                }
                lastException = e;
                log.warn("HTTP {} on attempt {}: {}", e.getStatusCode(), attempt, e.getResponseBodyAsString());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Request interrupted", ie);
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {} failed: {}", attempt, e.getMessage());
            }
        }

        throw new RuntimeException("AI call failed after " + (maxRetries + 1) + " attempts: "
                + (lastException != null ? lastException.getMessage() : "unknown"), lastException);
    }

    private Map<String, Object> buildBody(String systemPrompt, String userPrompt, double temperature) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model",                 model);
        body.put("messages",              List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user",   "content", userPrompt)
        ));
        body.put("temperature",           temperature);
        body.put("max_completion_tokens", maxCompletionTokens);
        body.put("top_p",                 topP);
        body.put("stream",                false);
        body.put("reasoning_effort",      reasoningEffort);
        body.put("stop",                  null);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doPost(Map<String, Object> body) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("OpenRouter API key is missing. Set OPENROUTER_API_KEY.");
        }
        String normalizedApiKey = apiKey.trim();
        if (normalizedApiKey.equals("PASTE_YOUR_OPENROUTER_KEY_HERE")) {
            throw new RuntimeException("OpenRouter API key is placeholder text. Set OPENROUTER_API_KEY.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(normalizedApiKey);
        headers.set("HTTP-Referer", "https://scriptoria.app");
        headers.set("X-Title",      "Scriptoria");

        ResponseEntity<Map> response = openRouterRestTemplate.postForEntity(
                baseUrl + "/chat/completions",
                new HttpEntity<>(body, headers),
                Map.class
        );
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        try {
            if (response == null) {
                throw new RuntimeException("Empty response from OpenRouter");
            }
            if (response.get("error") instanceof Map<?, ?> errorMap) {
                throw new RuntimeException("OpenRouter error: " + errorMap);
            }
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            Object contentObj = message.get("content");
            String content = extractTextContent(contentObj);
            log.debug("Response received: {} chars", content.length());
            return content;
        } catch (Exception e) {
            log.error("Unexpected OpenRouter response: {}", response);
            throw new RuntimeException("Could not parse OpenRouter response: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTextContent(Object contentObj) {
        if (contentObj == null) return "";
        if (contentObj instanceof String s) return s;
        if (contentObj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object partObj : list) {
                if (partObj instanceof Map<?, ?> part) {
                    Object text = ((Map<String, Object>) part).get("text");
                    if (text instanceof String t) sb.append(t);
                }
            }
            return sb.toString();
        }
        return contentObj.toString();
    }

    private <T> T parseJson(String raw, Class<T> type) {
        String cleaned = raw.trim()
                .replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)^```\\s*",     "")
                .replaceAll("(?s)\\s*```$",     "")
                .trim();
        try {
            return objectMapper.readValue(cleaned, type);
        } catch (Exception e) {
            log.error("JSON parse failed. Raw:\n{}", cleaned.length() > 500 ? cleaned.substring(0, 500) + "..." : cleaned);
            throw new RuntimeException("AI returned malformed JSON: " + e.getMessage(), e);
        }
    }

    private String buildJsonSystem(String systemPrompt) {
        return systemPrompt
                + "\n\nCRITICAL: Respond with ONLY valid JSON. No markdown, no backticks, "
                + "no explanation. Start with { end with }.";
    }

    private List<String> splitBySceneHeading(String text) {
        String[] lines = text.split("\\R", -1);
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            boolean isSceneHeading = line.trim().matches("^(INT\\.|EXT\\.|INT/EXT\\.).*");
            if (isSceneHeading && !current.isEmpty()) {
                blocks.add(current.toString());
                current = new StringBuilder();
            }
            current.append(line).append("\n");
        }

        if (!current.isEmpty()) {
            blocks.add(current.toString());
        }
        return blocks;
    }

    private List<String> chunkByLines(String text, int maxChunkChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\\R", -1)) {
            String lineWithNl = line + "\n";
            if (current.length() + lineWithNl.length() > maxChunkChars && !current.isEmpty()) {
                chunks.add(current.toString());
                current = new StringBuilder();
            }
            current.append(lineWithNl);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
