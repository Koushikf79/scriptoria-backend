package com.scriptoria.service;

import com.scriptoria.dto.StoryboardRequest;
import com.scriptoria.dto.StoryboardResponse;
import com.scriptoria.dto.StoryboardResponse.ShotVariation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryboardService {

    private final AiService aiService;

    private static final String SYSTEM_PROMPT = """
            You are a world-class cinematographer and director.
            Given a scene, generate exactly 6 cinematic shot variations.

            The 6 must cover (in order):
            1. WIDE_SHOT     — DAY_NATURAL lighting
            2. CLOSE_UP      — DAY_NATURAL lighting
            3. LOW_ANGLE     — GOLDEN_HOUR lighting
            4. TOP_ANGLE     — overhead / bird's eye
            5. WIDE_SHOT     — NIGHT_NEON or MOONLIGHT lighting
            6. CLOSE_UP      — LOW_KEY or CANDLELIGHT lighting

            For each variation:
            - shotType, lightingStyle, lens (e.g. "35mm prime"), cameraMovement
            - mood (1 sentence), composition (1-2 sentences)
            - colorGrading (brief), detailedPrompt (3-5 sentences for the DP)

            Also top-level:
            - directorNote: your best shot recommendation and why (2-3 sentences)

            Return ONLY valid JSON:
            {
              "directorNote": "...",
              "variations": [ { "shotType":"...","lightingStyle":"...","lens":"...","cameraMovement":"...","mood":"...","composition":"...","colorGrading":"...","detailedPrompt":"..." }, ...6 items... ]
            }
            """;

    @SuppressWarnings("unchecked")
    public StoryboardResponse generate(StoryboardRequest req) {
        log.info("Storyboard for scene {}", req.getSceneNumber());

        String userPrompt = """
                Scene %d | Location: %s | Time: %s | Emotion: %s | Action intensity: %.1f/10

                %s
                """.formatted(
                req.getSceneNumber(),
                Optional.ofNullable(req.getLocation()).orElse("UNSPECIFIED"),
                Optional.ofNullable(req.getTimeOfDay()).orElse("DAY"),
                Optional.ofNullable(req.getDominantEmotion()).orElse("NEUTRAL"),
                req.getActionIntensity(),
                req.getSceneDescription()
        );

        StoryboardResponse res = new StoryboardResponse();
        res.setSceneNumber(req.getSceneNumber());
        res.setSceneDescription(req.getSceneDescription());
        res.setLocation(req.getLocation());
        res.setTimeOfDay(req.getTimeOfDay());
        List<ShotVariation> variations;

        try {
            Map<String, Object> raw = aiService.chatJsonCreative(SYSTEM_PROMPT, userPrompt, Map.class);
            res.setDirectorNote(str(raw, "directorNote", ""));

            variations = new ArrayList<>();
            if (raw.get("variations") instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> vm) {
                        Map<String, Object> v = (Map<String, Object>) vm;
                        ShotVariation sv = new ShotVariation();
                        sv.setShotType(str(v, "shotType", "WIDE_SHOT"));
                        sv.setLightingStyle(str(v, "lightingStyle", "DAY_NATURAL"));
                        sv.setLens(str(v, "lens", "35mm prime"));
                        sv.setCameraMovement(str(v, "cameraMovement", "STATIC"));
                        sv.setMood(str(v, "mood", ""));
                        sv.setComposition(str(v, "composition", ""));
                        sv.setColorGrading(str(v, "colorGrading", ""));
                        sv.setDetailedPrompt(str(v, "detailedPrompt", ""));
                        variations.add(sv);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Storyboard AI unavailable, returning built-in fallback for scene {}: {}", req.getSceneNumber(), ex.getMessage());
            res.setDirectorNote("AI service is temporarily unavailable. Showing a production-ready fallback shot plan.");
            variations = buildFallbackVariations(req);
        }

        if (variations.size() > 6) {
            variations = new ArrayList<>(variations.subList(0, 6));
        }
        while (variations.size() < 6) {
            ShotVariation filler = new ShotVariation();
            filler.setShotType("WIDE_SHOT");
            filler.setLightingStyle("DAY_NATURAL");
            filler.setLens("35mm prime");
            filler.setCameraMovement("STATIC");
            filler.setMood("Cinematic baseline variation");
            filler.setComposition("Centered subject with clean visual balance.");
            filler.setColorGrading("Neutral filmic contrast");
            filler.setDetailedPrompt("Compose a clean cinematic frame matching the scene context.");
            variations.add(filler);
        }
        res.setVariations(variations);
        return res;
    }

    private List<ShotVariation> buildFallbackVariations(StoryboardRequest req) {
        String scene = Optional.ofNullable(req.getSceneDescription()).orElse("Scene setup");
        String location = Optional.ofNullable(req.getLocation()).orElse("set location");
        String emotion = Optional.ofNullable(req.getDominantEmotion()).orElse("neutral");
        String time = Optional.ofNullable(req.getTimeOfDay()).orElse("DAY");

        List<ShotVariation> list = new ArrayList<>();
        list.add(makeVariation("WIDE_SHOT", "DAY_NATURAL", "24mm", "SLOW_PUSH_IN",
                "Establishing spatial geography",
                "Frame the full environment and character blocking to orient the audience.",
                "Natural contrast, soft highlights",
                "Open on a wide master at " + location + " during " + time + ". Stage actors with layered depth and a slow push in to set tone. Keep movement minimal and readable. Scene context: " + scene));
        list.add(makeVariation("CLOSE_UP", "DAY_NATURAL", "85mm", "STATIC",
                "Internal emotional focus",
                "Isolate the protagonist's face with shallow depth for emotional clarity.",
                "Warm skin tones, subtle halation",
                "Cut to a close-up reaction emphasizing " + emotion + ". Hold eye-line and micro-expressions. Keep background soft and uncluttered to prioritize performance."));
        list.add(makeVariation("LOW_ANGLE", "GOLDEN_HOUR", "35mm", "TILT_UP",
                "Character empowerment",
                "Use a low perspective to increase scale and dramatic weight.",
                "Amber highlights, lifted blacks",
                "Capture a low-angle hero composition at golden-hour style lighting. Start on foreground texture, tilt up to the subject, and lock on a decisive beat."));
        list.add(makeVariation("TOP_ANGLE", "SOFT_OVERHEAD", "28mm", "CRANE_DOWN",
                "Strategic overview",
                "Bird's-eye framing to reveal relationships and spatial tension.",
                "Neutral cinematic grade",
                "Design an overhead shot revealing all key positions and movement vectors. Use gentle crane motion for visual continuity and staging clarity."));
        list.add(makeVariation("WIDE_SHOT", "NIGHT_NEON", "32mm", "LATERAL_TRACK",
                "Atmospheric escalation",
                "Create contrast and depth with motivated practical light sources.",
                "Cool shadows, neon accents",
                "Reimagine the same blocking in a night-neon treatment. Track laterally to reveal environmental details and maintain dynamic silhouette separation."));
        list.add(makeVariation("CLOSE_UP", "LOW_KEY", "50mm", "HANDHELD_SUBTLE",
                "Intimate dramatic beat",
                "Tight framing with controlled handheld texture for urgency.",
                "Deep contrast, selective color",
                "Finish on a low-key close-up of the emotional pivot line. Keep handheld movement restrained and rhythmically synced to dialogue pauses."));
        return list;
    }

    private ShotVariation makeVariation(String shotType, String lightingStyle, String lens, String cameraMovement,
                                        String mood, String composition, String colorGrading, String detailedPrompt) {
        ShotVariation sv = new ShotVariation();
        sv.setShotType(shotType);
        sv.setLightingStyle(lightingStyle);
        sv.setLens(lens);
        sv.setCameraMovement(cameraMovement);
        sv.setMood(mood);
        sv.setComposition(composition);
        sv.setColorGrading(colorGrading);
        sv.setDetailedPrompt(detailedPrompt);
        return sv;
    }

    private String str(Map<String,Object> m, String k, String def) { return m.get(k) instanceof String v ? v : def; }
}
