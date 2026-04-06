package com.scriptoria.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiService {

    private final RestTemplate geminiRestTemplate;
    private final ObjectMapper objectMapper;

    public AiService(@Qualifier("geminiRestTemplate") RestTemplate geminiRestTemplate, ObjectMapper objectMapper) {
        this.geminiRestTemplate = geminiRestTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    @Value("${gemini.max-output-tokens:8192}")
    private int maxOutputTokens;

    @Value("${gemini.temperature-analysis:0.2}")
    private double temperatureAnalysis;

    @Value("${gemini.temperature-creative:0.85}")
    private double temperatureCreative;

    @Value("${gemini.chunk-size-chars:10000}")
    private int chunkSizeChars;

    @Value("${gemini.max-retries:2}")
    private int maxRetries;

    public String chatAnalytical(String systemPrompt, String userPrompt) {
        return chatWithRetry(systemPrompt, userPrompt, temperatureAnalysis);
    }

    public String chatCreative(String systemPrompt, String userPrompt) {
        return chatWithRetry(systemPrompt, userPrompt, temperatureCreative);
    }

    public <T> T chatJson(String systemPrompt, String userPrompt, Class<T> responseType) {
        return parseJson(chatAnalytical(buildJsonSystem(systemPrompt), userPrompt), responseType);
    }

    public <T> T chatJsonCreative(String systemPrompt, String userPrompt, Class<T> responseType) {
        return parseJson(chatCreative(buildJsonSystem(systemPrompt), userPrompt), responseType);
    }

    public List<String> chunkText(String text) {
        return chunkText(text, chunkSizeChars);
    }

    public List<String> chunkText(String text, int maxChunkChars) {
        if (text.length() <= maxChunkChars) {
            return List.of(text);
        }

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

    private String chatWithRetry(String systemPrompt, String userPrompt, double temperature) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                if (attempt > 1) {
                    log.warn("Retry attempt {}/{} after failure", attempt, maxRetries + 1);
                    Thread.sleep(1500L * (attempt - 1));
                }
                return callGemini(systemPrompt, userPrompt, temperature);
            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                if (status == 401 || status == 403) {
                    throw new RuntimeException("Invalid Gemini API key or insufficient permission.");
                }
                if (status == 429) {
                    throw new RuntimeException("Gemini rate limit reached. Please wait and retry.");
                }
                if (status >= 400 && status < 500) {
                    throw new RuntimeException("Gemini request rejected (" + status + "): " + safeBody(e.getResponseBodyAsString()));
                }
                lastException = e;
                log.warn("Gemini client error on attempt {}: {}", attempt, e.getMessage());
            } catch (HttpServerErrorException e) {
                lastException = e;
                log.warn("Gemini server error on attempt {}: {}", attempt, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Gemini request interrupted", e);
            } catch (Exception e) {
                lastException = e;
                log.warn("Gemini attempt {} failed: {}", attempt, e.getMessage());
            }
        }

        throw new RuntimeException(
                "Gemini call failed after " + (maxRetries + 1) + " attempts: "
                        + (lastException != null ? lastException.getMessage() : "unknown"),
                lastException
        );
    }

    private Map<String, Object> buildGeminiBody(String systemPrompt, String userPrompt, double temperature) {
        Map<String, Object> textPart = Map.of(
                "text", "INSTRUCTIONS:\n" + systemPrompt + "\n\nINPUT:\n" + userPrompt
        );
        Map<String, Object> content = Map.of("parts", List.of(textPart));
        Map<String, Object> generationConfig = Map.of(
                "temperature", temperature,
                "maxOutputTokens", maxOutputTokens,
                "topP", 0.95
        );
        return Map.of(
                "contents", List.of(content),
                "generationConfig", generationConfig
        );
    }

    @SuppressWarnings("unchecked")
    private String callGemini(String systemPrompt, String userPrompt, double temperature) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new RuntimeException("Gemini API key is missing. Set GEMINI_API_KEY.");
        }
        if ("PASTE_YOUR_GEMINI_KEY_HERE".equals(geminiApiKey.trim())) {
            throw new RuntimeException("Gemini API key is placeholder text. Set GEMINI_API_KEY.");
        }

        String url = geminiBaseUrl + "/models/" + geminiModel + ":generateContent?key=" + geminiApiKey.trim();
        Map<String, Object> body = buildGeminiBody(systemPrompt, userPrompt, temperature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = geminiRestTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
        return stripMarkdownFences(extractContent(response.getBody()));
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        try {
            if (response == null) {
                throw new RuntimeException("Empty response from Gemini");
            }

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new RuntimeException("No candidates returned by Gemini");
            }

            Map<String, Object> firstCandidate = candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
            if (content == null) {
                throw new RuntimeException("Gemini content missing");
            }
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) {
                throw new RuntimeException("Gemini parts missing");
            }

            String text = String.valueOf(parts.get(0).getOrDefault("text", ""));
            if (text.isBlank()) {
                throw new RuntimeException("Gemini returned empty text");
            }
            return text;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected Gemini response: {}", response);
            throw new RuntimeException("Could not parse Gemini response: " + e.getMessage(), e);
        }
    }

    private String stripMarkdownFences(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)^```\\s*", "")
                .replaceAll("(?s)\\s*```$", "")
                .trim();
    }

    private String safeBody(String body) {
        return (body == null || body.isBlank()) ? "no details" : body;
    }

    private <T> T parseJson(String raw, Class<T> type) {
        String cleaned = stripMarkdownFences(raw);
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
