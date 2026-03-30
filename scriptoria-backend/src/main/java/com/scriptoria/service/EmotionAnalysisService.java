package com.scriptoria.service;

import com.scriptoria.dto.EmotionAnalysisResponse;
import com.scriptoria.dto.EmotionAnalysisResponse.EmotionPoint;
import com.scriptoria.dto.SceneDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionAnalysisService {

    private final AiService aiService;

    private static final String SYSTEM_PROMPT = """
            You are a film emotion analyst specializing in screenplay emotional arcs.
            Given a list of scenes, score these emotions per scene from 0.0 to 10.0:
            tension, joy, grief, fear, anger, hope, love

            Per scene also provide:
            - dominantEmotion: the highest scoring emotion (uppercase)
            - emotionNote: one short sentence explaining the emotional driver

            Top-level:
            - dominantTone: overall emotional tone (1 sentence)
            - peakTensionScene: scene number with highest tension score
            - peakJoyScene: scene number with highest joy score
            - emotionalJourney: one sentence arc summary

            Return ONLY valid JSON:
            {
              "dominantTone": "...",
              "peakTensionScene": 12,
              "peakJoyScene": 5,
              "emotionalJourney": "...",
              "arc": [
                {
                  "sceneNumber": 1, "location": "...",
                  "tension": 3.5, "joy": 7.0, "grief": 1.0,
                  "fear": 2.0, "anger": 1.5, "hope": 6.0, "love": 4.0,
                  "dominantEmotion": "JOY", "emotionNote": "..."
                }
              ]
            }
            """;

    @SuppressWarnings("unchecked")
    public EmotionAnalysisResponse analyze(List<SceneDto> scenes) {
        log.info("Starting emotion analysis for {} scenes", scenes.size());

        StringBuilder sb = new StringBuilder();
        for (SceneDto s : scenes) {
            sb.append("Scene ").append(s.getSceneNumber())
              .append(" [").append(s.getLocation()).append(", ").append(s.getTimeOfDay()).append("]: ")
              .append(s.getDescription())
              .append(" | Characters: ").append(String.join(", ", s.getCharacters()))
              .append("\n");
        }

        List<String> chunks = aiService.chunkText(sb.toString());
        List<EmotionPoint> allPoints = new ArrayList<>();
        String dominantTone = "";
        int peakTension = 1, peakJoy = 1;
        String emotionalJourney = "";

        for (int i = 0; i < chunks.size(); i++) {
            String userPrompt = "Analyze emotions for these scenes (chunk " + (i+1) + "/" + chunks.size() + "):\n\n" + chunks.get(i);
            try {
                Map<String, Object> raw = aiService.chatJson(SYSTEM_PROMPT, userPrompt, Map.class);

                if (raw.get("dominantTone")     instanceof String v)  dominantTone    = v;
                if (raw.get("emotionalJourney") instanceof String v)  emotionalJourney = v;
                if (raw.get("peakTensionScene") instanceof Number n)  peakTension     = n.intValue();
                if (raw.get("peakJoyScene")     instanceof Number n)  peakJoy         = n.intValue();

                if (raw.get("arc") instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> ep) {
                            allPoints.add(mapToPoint((Map<String, Object>) ep));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Emotion chunk {} failed: {}", i, e.getMessage());
            }
        }

        return buildResponse(allPoints, dominantTone, peakTension, peakJoy, emotionalJourney);
    }

    @SuppressWarnings("unchecked")
    private EmotionPoint mapToPoint(Map<String, Object> m) {
        EmotionPoint ep = new EmotionPoint();
        ep.setSceneNumber(m.get("sceneNumber") instanceof Number n ? n.intValue()
                : (m.get("scene_number") instanceof Number n2 ? n2.intValue() : 0));
        ep.setLocation(str(m, "location", ""));
        ep.setTension(dbl(m, "tension")); ep.setJoy(dbl(m, "joy"));
        ep.setGrief(dbl(m, "grief"));     ep.setFear(dbl(m, "fear"));
        ep.setAnger(dbl(m, "anger"));     ep.setHope(dbl(m, "hope"));
        ep.setLove(dbl(m, "love"));
        ep.setDominantEmotion(str(m, "dominantEmotion",
                str(m, "dominant_emotion", "NEUTRAL")));
        ep.setEmotionNote(str(m, "emotionNote", str(m, "emotion_note", "")));
        return ep;
    }

    private EmotionAnalysisResponse buildResponse(List<EmotionPoint> arc, String tone,
                                                   int peakTension, int peakJoy, String journey) {
        EmotionAnalysisResponse res = new EmotionAnalysisResponse();
        res.setArc(arc);
        res.setDominantTone(tone);
        res.setPeakTensionScene(peakTension);
        res.setPeakJoyScene(peakJoy);
        res.setEmotionalJourney(journey);

        if (!arc.isEmpty()) {
            Map<String, Double> profile = new LinkedHashMap<>();
            profile.put("tension", arc.stream().mapToDouble(EmotionPoint::getTension).average().orElse(0));
            profile.put("joy",     arc.stream().mapToDouble(EmotionPoint::getJoy).average().orElse(0));
            profile.put("grief",   arc.stream().mapToDouble(EmotionPoint::getGrief).average().orElse(0));
            profile.put("fear",    arc.stream().mapToDouble(EmotionPoint::getFear).average().orElse(0));
            profile.put("anger",   arc.stream().mapToDouble(EmotionPoint::getAnger).average().orElse(0));
            profile.put("hope",    arc.stream().mapToDouble(EmotionPoint::getHope).average().orElse(0));
            profile.put("love",    arc.stream().mapToDouble(EmotionPoint::getLove).average().orElse(0));
            res.setOverallProfile(profile);
        } else {
            // Keep a non-empty structure for frontend charts that assume first point exists.
            EmotionPoint fallback = new EmotionPoint();
            fallback.setSceneNumber(1);
            fallback.setLocation("UNKNOWN");
            fallback.setDominantEmotion("NEUTRAL");
            fallback.setEmotionNote("No emotion arc could be inferred.");
            fallback.setTension(0.0);
            fallback.setJoy(0.0);
            fallback.setGrief(0.0);
            fallback.setFear(0.0);
            fallback.setAnger(0.0);
            fallback.setHope(0.0);
            fallback.setLove(0.0);
            res.setArc(List.of(fallback));
            res.setOverallProfile(Map.of(
                    "tension", 0.0,
                    "joy", 0.0,
                    "grief", 0.0,
                    "fear", 0.0,
                    "anger", 0.0,
                    "hope", 0.0,
                    "love", 0.0
            ));
            if (res.getDominantTone() == null || res.getDominantTone().isBlank()) {
                res.setDominantTone("Neutral");
            }
        }
        return res;
    }

    private String str(Map<String,Object> m, String k, String def) { return m.get(k) instanceof String v ? v : def; }
    private double dbl(Map<String,Object> m, String k)             { return m.get(k) instanceof Number n ? n.doubleValue() : 0.0; }
}
