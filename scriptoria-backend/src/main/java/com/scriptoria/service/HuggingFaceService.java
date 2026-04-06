package com.scriptoria.service;

import com.scriptoria.dto.SceneAnimationRequest;
import com.scriptoria.dto.SceneImageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HuggingFaceService {

    @Value("${huggingface.api-key}")
    private String hfApiKey;

    @Value("${huggingface.base-url}")
    private String hfBaseUrl;

    @Value("${huggingface.image-model}")
    private String imageModel;

    @Value("${huggingface.animation-model}")
    private String animationModel;

    @Qualifier("huggingFaceRestTemplate")
    private final RestTemplate restTemplate;

    private final ThreadLocal<Boolean> warmUpRequired = ThreadLocal.withInitial(() -> false);

    public String generateSceneImage(SceneImageRequest request) {
        String prompt = buildImagePrompt(request);
        byte[] bytes = inferBinary(imageModel, prompt);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public String generateSceneAnimation(SceneAnimationRequest request) {
        warmUpRequired.set(false);
        String prompt = buildAnimationPrompt(request);

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                byte[] bytes = inferBinary(animationModel, prompt);
                return Base64.getEncoder().encodeToString(bytes);
            } catch (HttpServerErrorException.ServiceUnavailable e) {
                warmUpRequired.set(true);
                if (attempt == maxAttempts) {
                    throw new RuntimeException("Hugging Face animation model is still warming up. Try again shortly.");
                }
                log.warn("Hugging Face animation warm-up (attempt {}/{}). Retrying in 20 seconds.", attempt, maxAttempts);
                sleep20Seconds();
            }
        }
        throw new RuntimeException("Hugging Face animation generation failed.");
    }

    public String previewImagePrompt(SceneImageRequest request) {
        return buildImagePrompt(request);
    }

    public String previewAnimationPrompt(SceneAnimationRequest request) {
        return buildAnimationPrompt(request);
    }

    public String getImageModel() {
        return imageModel;
    }

    public String getAnimationModel() {
        return animationModel;
    }

    public boolean wasLastAnimationWarmUpRequired() {
        return Boolean.TRUE.equals(warmUpRequired.get());
    }

    private byte[] inferBinary(String model, String prompt) {
        if (hfApiKey == null || hfApiKey.isBlank()) {
            throw new RuntimeException("Hugging Face API key is missing. Set HUGGINGFACE_API_KEY.");
        }

        String url = hfBaseUrl + "/models/" + model;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(hfApiKey.trim());
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("inputs", prompt), headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, entity, byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new RuntimeException("Hugging Face returned empty binary response.");
            }

            MediaType mediaType = response.getHeaders().getContentType();
            if (mediaType != null && MediaType.APPLICATION_JSON.includes(mediaType)) {
                String jsonBody = new String(body, StandardCharsets.UTF_8);
                throw new RuntimeException("Hugging Face returned JSON error: " + jsonBody);
            }
            return body;
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Hugging Face request rejected (" + e.getStatusCode().value() + "): "
                    + safeBody(e.getResponseBodyAsString()), e);
        }
    }

    private String buildImagePrompt(SceneImageRequest req) {
        String genreValue = req.getGenre() != null && !req.getGenre().isBlank() ? req.getGenre() : "cinematic";
        String scriptTone = req.getScriptTone() != null && !req.getScriptTone().isBlank() ? req.getScriptTone() : "dramatic";

        return String.format(
                "%s cinematic still, %s, %s mood, film photography, %s genre, dramatic lighting, high quality, photorealistic, 35mm film, %s tone",
                req.getTimeOfDay().toLowerCase(Locale.ROOT),
                req.getLocation().toLowerCase(Locale.ROOT),
                req.getDominantEmotion().toLowerCase(Locale.ROOT),
                genreValue.toLowerCase(Locale.ROOT),
                scriptTone.toLowerCase(Locale.ROOT)
        );
    }

    private String buildAnimationPrompt(SceneAnimationRequest req) {
        String camera = req.getCameraMovement() != null && !req.getCameraMovement().isBlank()
                ? req.getCameraMovement().toLowerCase(Locale.ROOT).replace("_", " ")
                : "movement";
        return String.format(
                "%s, %s lighting, cinematic camera %s, %s emotion, film quality animation, smooth motion",
                req.getDescription(),
                req.getTimeOfDay().toLowerCase(Locale.ROOT),
                camera,
                req.getDominantEmotion().toLowerCase(Locale.ROOT)
        );
    }

    private String safeBody(String body) {
        return (body == null || body.isBlank()) ? "no details" : body;
    }

    private void sleep20Seconds() {
        try {
            Thread.sleep(20_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for Hugging Face model warm-up", e);
        }
    }
}
