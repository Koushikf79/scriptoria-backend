package com.scriptoria.service;

import com.scriptoria.dto.AnimationBatchRequest;
import com.scriptoria.dto.AnimationBatchResponse;
import com.scriptoria.dto.AnimationRequest;
import com.scriptoria.dto.AnimationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnimationService {

    private final AiService aiService;

    private static final String SYSTEM_PROMPT = """
            You are a cinematographer and CSS animation expert.
            Given a scene, generate CSS keyframe animation properties that simulate
            the described camera movement and atmosphere in a browser card.

            Camera movement mappings (use as base, enhance creatively):
            - DOLLY_IN    -> scale(1) to scale(1.15-1.25), slow ease-in
            - DOLLY_OUT   -> scale(1.2) to scale(1.0), slow ease-out
            - TRACKING    -> translateX(-20px) to translateX(20px), linear
            - PAN         -> translateX(-30px) to translateX(30px), ease-in-out
            - CRANE       -> translateY(20px) to translateY(-20px), slow ease
            - DRONE       -> scale(1.3) translateY(-30px) to scale(1.0) translateY(0), ease-out
            - HANDHELD    -> random subtle shake, translateX(-2px to 2px) translateY(-2px to 2px)
            - STATIC      -> subtle breathing, scale(1.0) to scale(1.02), very slow
            - STEADICAM   -> smooth combined translate plus slight scale

            Time of day color grades:
            - DAY     -> warm whites, cyan sky tones, high saturation
            - NIGHT   -> deep blues/blacks, low saturation, lifted shadows
            - DAWN    -> purple-to-orange gradient, soft diffused
            - DUSK    -> golden-orange, long shadows, warm highlights
            - GOLDEN_HOUR -> amber, lens flare hints, high contrast

            Emotion atmosphere effects:
            - TENSION  -> vignette heavy, desaturated, cool tones, slow dolly
            - JOY      -> bright, warm, bouncy scale animation
            - GRIEF    -> desaturated, cool blue, very slow fade pulse
            - FEAR     -> high contrast, flicker, harsh shadows
            - ANGER    -> red tints, fast aggressive movement
            - HOPE     -> soft warm glow, gentle upward crane
            - LOVE     -> soft focus vignette, warm golden tones

            Particle effects based on location/time:
            - NIGHT exterior -> "rain" or "stars"
            - DAY exterior   -> "dust" or "leaves"
            - Interior fire  -> "embers"
            - Otherwise      -> "none"

            Output rules:
            - Return ONLY valid JSON matching this structure:
              {
                "sceneNumber": 1,
                "animationName": "camera_emotion_time_scene",
                "cssKeyframes": "@keyframes ...",
                "containerStyles": "...",
                "textStyles": "...",
                "overlayEffect": "...",
                "particleEffect": "rain|stars|dust|leaves|embers|none",
                "animationDuration": "2.0s-6.0s",
                "animationTimingFunction": "...",
                "animationIterationCount": "...",
                "lightFlicker": false,
                "vignetteIntensity": 0.3-1.0,
                "colorGrade": { "shadows":"#", "midtones":"#", "highlights":"#", "saturation":0.0-1.0 },
                "sceneLabel": "...",
                "moodTag": "...",
                "directorHint": "..."
              }
            - animationName must be unique: cameraMovement_emotion_timeOfDay_sceneNumber in lowercase with underscores.
            - All CSS values must be valid and directly injectable into React style props.
            """;

    public AnimationResponse generate(AnimationRequest request) {
        log.info("Generating animation for scene {}", request.getSceneNumber());

        String userPrompt = """
                Generate cinematic CSS animation JSON for this scene:
                sceneNumber: %d
                location: %s
                timeOfDay: %s
                dominantEmotion: %s
                actionIntensity: %.1f
                cameraMovement: %s
                description: %s
                characters: %s
                hasVfx: %s
                """.formatted(
                request.getSceneNumber(),
                request.getLocation(),
                request.getTimeOfDay(),
                request.getDominantEmotion(),
                request.getActionIntensity(),
                request.getCameraMovement(),
                request.getDescription(),
                request.getCharacters(),
                request.getHasVfx()
        );

        AnimationResponse response = aiService.chatJsonCreative(SYSTEM_PROMPT, userPrompt, AnimationResponse.class);
        return normalizeResponse(request, response);
    }

    public AnimationBatchResponse generateBatch(AnimationBatchRequest request) {
        if (request.getScenes() == null || request.getScenes().isEmpty()) {
            throw new IllegalArgumentException("scenes must contain between 1 and 10 items");
        }
        if (request.getScenes().size() > 10) {
            throw new IllegalArgumentException("Max 10 scenes per batch request");
        }

        List<CompletableFuture<AnimationResponse>> futures = request.getScenes().stream()
                .map(scene -> CompletableFuture.supplyAsync(() -> generate(scene)))
                .toList();

        List<AnimationResponse> animations = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        AnimationBatchResponse response = new AnimationBatchResponse();
        response.setAnimations(animations);
        return response;
    }

    private AnimationResponse normalizeResponse(AnimationRequest request, AnimationResponse response) {
        AnimationResponse res = Optional.ofNullable(response).orElseGet(AnimationResponse::new);
        res.setSceneNumber(request.getSceneNumber());

        if (isBlank(res.getAnimationName())) {
            res.setAnimationName(buildAnimationName(request));
        }

        if (isBlank(res.getCssKeyframes())) {
            res.setCssKeyframes(defaultKeyframes(res.getAnimationName(), request.getCameraMovement()));
        }
        if (isBlank(res.getContainerStyles())) {
            res.setContainerStyles(defaultContainerStyles(request.getTimeOfDay()));
        }
        if (isBlank(res.getTextStyles())) {
            res.setTextStyles("color: #d8dbe8; font-family: 'Courier New', monospace;");
        }
        if (isBlank(res.getOverlayEffect())) {
            res.setOverlayEffect("radial-gradient(ellipse at center, rgba(0,0,0,0.12) 0%, transparent 70%)");
        }
        if (isBlank(res.getParticleEffect())) {
            res.setParticleEffect(defaultParticleEffect(request.getLocation(), request.getTimeOfDay()));
        }

        res.setAnimationDuration(computeDuration(request.getActionIntensity()));
        if (isBlank(res.getAnimationTimingFunction())) {
            res.setAnimationTimingFunction("cubic-bezier(0.25, 0.46, 0.45, 0.94)");
        }
        if (isBlank(res.getAnimationIterationCount())) {
            res.setAnimationIterationCount("infinite");
        }

        res.setVignetteIntensity(emotionVignette(request.getDominantEmotion()));

        if (res.getColorGrade() == null) {
            res.setColorGrade(defaultColorGrade(request.getTimeOfDay()));
        } else {
            AnimationResponse.ColorGrade cg = res.getColorGrade();
            if (isBlank(cg.getShadows()) || isBlank(cg.getMidtones()) || isBlank(cg.getHighlights())) {
                AnimationResponse.ColorGrade fallback = defaultColorGrade(request.getTimeOfDay());
                if (isBlank(cg.getShadows())) cg.setShadows(fallback.getShadows());
                if (isBlank(cg.getMidtones())) cg.setMidtones(fallback.getMidtones());
                if (isBlank(cg.getHighlights())) cg.setHighlights(fallback.getHighlights());
            }
            cg.setSaturation(clamp(cg.getSaturation(), 0.0, 1.0));
        }

        if (isBlank(res.getSceneLabel())) {
            res.setSceneLabel("EXT. " + request.getLocation() + " - " + request.getTimeOfDay());
        }
        if (isBlank(res.getMoodTag())) {
            res.setMoodTag(request.getDominantEmotion());
        }
        if (isBlank(res.getDirectorHint())) {
            res.setDirectorHint("Match motion speed to emotional rhythm and keep atmospheric overlays subtle.");
        }

        return res;
    }

    private String computeDuration(double actionIntensity) {
        double clampedIntensity = clamp(actionIntensity, 0.0, 10.0);
        double seconds = 6.0 - (clampedIntensity * 0.4);
        return String.format(Locale.ROOT, "%.1fs", clamp(seconds, 2.0, 6.0));
    }

    private double emotionVignette(String dominantEmotion) {
        if (dominantEmotion == null) {
            return 0.6;
        }
        return switch (dominantEmotion.toUpperCase(Locale.ROOT)) {
            case "JOY" -> 0.35;
            case "HOPE" -> 0.45;
            case "LOVE" -> 0.50;
            case "GRIEF" -> 0.78;
            case "TENSION" -> 0.85;
            case "ANGER" -> 0.90;
            case "FEAR" -> 0.95;
            default -> 0.60;
        };
    }

    private AnimationResponse.ColorGrade defaultColorGrade(String timeOfDay) {
        AnimationResponse.ColorGrade cg = new AnimationResponse.ColorGrade();
        String value = timeOfDay == null ? "" : timeOfDay.toUpperCase(Locale.ROOT);
        switch (value) {
            case "DAY" -> {
                cg.setShadows("#5d7996");
                cg.setMidtones("#8ac6df");
                cg.setHighlights("#fff4d6");
                cg.setSaturation(0.9);
            }
            case "DAWN" -> {
                cg.setShadows("#4a4b7d");
                cg.setMidtones("#cf7f63");
                cg.setHighlights("#ffd8a8");
                cg.setSaturation(0.75);
            }
            case "DUSK", "GOLDEN_HOUR" -> {
                cg.setShadows("#5b4630");
                cg.setMidtones("#b46b3f");
                cg.setHighlights("#ffd18c");
                cg.setSaturation(0.82);
            }
            case "NIGHT" -> {
                cg.setShadows("#0a0e1a");
                cg.setMidtones("#1e3a5f");
                cg.setHighlights("#c8a96e");
                cg.setSaturation(0.6);
            }
            default -> {
                cg.setShadows("#1a1f2b");
                cg.setMidtones("#3d4b63");
                cg.setHighlights("#c7cad4");
                cg.setSaturation(0.7);
            }
        }
        return cg;
    }

    private String defaultParticleEffect(String location, String timeOfDay) {
        String loc = Optional.ofNullable(location).orElse("").toUpperCase(Locale.ROOT);
        String tod = Optional.ofNullable(timeOfDay).orElse("").toUpperCase(Locale.ROOT);
        boolean exterior = loc.contains("EXT") || loc.contains("ROOFTOP") || loc.contains("STREET")
                || loc.contains("FOREST") || loc.contains("BEACH") || loc.contains("OUTDOOR");
        if (exterior && "NIGHT".equals(tod)) return "rain";
        if (exterior && "DAY".equals(tod)) return "dust";
        if (loc.contains("FIRE")) return "embers";
        return "none";
    }

    private String defaultContainerStyles(String timeOfDay) {
        String tod = Optional.ofNullable(timeOfDay).orElse("NIGHT").toUpperCase(Locale.ROOT);
        return switch (tod) {
            case "DAY" -> "background: linear-gradient(135deg, #dbeeff 0%, #b9d9f5 50%, #f6eddc 100%); border: 1px solid #9cbfdf;";
            case "DAWN" -> "background: linear-gradient(135deg, #675c90 0%, #d68466 55%, #ffd6a8 100%); border: 1px solid #a67774;";
            case "DUSK", "GOLDEN_HOUR" -> "background: linear-gradient(135deg, #5b3f2a 0%, #b5663f 60%, #ffcc8b 100%); border: 1px solid #8d5b3f;";
            default -> "background: linear-gradient(135deg, #0a0a0f 0%, #1a1a2e 50%, #16213e 100%); border: 1px solid #2a2a4a;";
        };
    }

    private String defaultKeyframes(String animationName, String cameraMovement) {
        String key = Optional.ofNullable(animationName).filter(v -> !v.isBlank()).orElse("scene_motion");
        String movement = Optional.ofNullable(cameraMovement).orElse("STATIC").toUpperCase(Locale.ROOT);

        return switch (movement) {
            case "DOLLY_IN" -> "@keyframes " + key + " { 0% { transform: scale(1) translateY(0); opacity: 0.75; } 100% { transform: scale(1.18) translateY(-12px); opacity: 1; } }";
            case "DOLLY_OUT" -> "@keyframes " + key + " { 0% { transform: scale(1.2) translateY(-8px); opacity: 1; } 100% { transform: scale(1) translateY(0); opacity: 0.82; } }";
            case "TRACKING", "PAN" -> "@keyframes " + key + " { 0% { transform: translateX(-24px); } 50% { transform: translateX(0); } 100% { transform: translateX(24px); } }";
            case "CRANE" -> "@keyframes " + key + " { 0% { transform: translateY(20px) scale(1.03); } 100% { transform: translateY(-20px) scale(1); } }";
            case "DRONE" -> "@keyframes " + key + " { 0% { transform: scale(1.3) translateY(-30px); opacity: 0.85; } 100% { transform: scale(1) translateY(0); opacity: 1; } }";
            case "HANDHELD" -> "@keyframes " + key + " { 0% { transform: translate(0, 0); } 25% { transform: translate(2px, -1px); } 50% { transform: translate(-2px, 2px); } 75% { transform: translate(1px, -2px); } 100% { transform: translate(0, 0); } }";
            case "STEADICAM" -> "@keyframes " + key + " { 0% { transform: translateX(-10px) translateY(4px) scale(1.02); } 100% { transform: translateX(10px) translateY(-4px) scale(1.06); } }";
            default -> "@keyframes " + key + " { 0% { transform: scale(1); opacity: 0.9; } 100% { transform: scale(1.02); opacity: 1; } }";
        };
    }

    private String buildAnimationName(AnimationRequest request) {
        return sanitizeToken(request.getCameraMovement()) + "_"
                + sanitizeToken(request.getDominantEmotion()) + "_"
                + sanitizeToken(request.getTimeOfDay()) + "_"
                + request.getSceneNumber();
    }

    private String sanitizeToken(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
