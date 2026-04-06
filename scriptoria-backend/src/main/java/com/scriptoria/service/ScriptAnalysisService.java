package com.scriptoria.service;

import com.scriptoria.dto.SceneDto;
import com.scriptoria.dto.ScriptAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptAnalysisService {

    private final AiService aiService;

    private static final String SYSTEM_PROMPT = """
            You are an expert screenplay analyst with deep knowledge of film production.
            Parse the screenplay and extract structured scene data.

            For each scene heading (INT./EXT. ...) extract:
            - sceneNumber (sequential integer starting at 1)
            - location (place name, e.g. "COFFEE SHOP")
            - timeOfDay (DAY | NIGHT | DAWN | DUSK)
            - interior (INT | EXT | INT/EXT)
            - characters (array of character name strings who appear or speak)
            - description (1-2 sentence action summary)
            - actionIntensity (0.0-10.0)
            - emotionalIntensity (0.0-10.0)
            - productionComplexity (0.0-10.0)
            - dominantEmotion (TENSION|JOY|GRIEF|FEAR|ANGER|HOPE|LOVE|NEUTRAL)
            - hasVfx (boolean)
            - hasStunt (boolean)
            - hasLargecrowd (boolean)

            Top-level fields:
            - genre (e.g. "Crime Thriller")
            - scriptTone (brief tone description)

            Return ONLY this JSON structure, nothing else:
            {
              "genre": "...",
              "scriptTone": "...",
              "scenes": [ { ...all scene fields... } ]
            }
            """;

    @SuppressWarnings("unchecked")
    public ScriptAnalysisResponse analyze(String screenplay) {
        log.info("Starting script analysis, length={}", screenplay.length());

        List<String> chunks = aiService.chunkText(screenplay);
        List<SceneDto> allScenes = new ArrayList<>();
        String genre = "Unknown";
        String scriptTone = "Unknown";
        int sceneOffset = 0;

        for (int i = 0; i < chunks.size(); i++) {
            String userPrompt = "Analyze screenplay chunk " + (i + 1) + " of " + chunks.size() + ":\n\n" + chunks.get(i);
            try {
                Map<String, Object> raw = aiService.chatJson(SYSTEM_PROMPT, userPrompt, Map.class);

                if (raw.get("genre") instanceof String g)     genre = g;
                if (raw.get("scriptTone") instanceof String t) scriptTone = t;

                if (raw.get("scenes") instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> sm) {
                            allScenes.add(mapToScene((Map<String, Object>) sm, sceneOffset++));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to analyze chunk {}: {}", i, e.getMessage());
            }
        }

        return buildResponse(allScenes, genre, scriptTone);
    }

    @SuppressWarnings("unchecked")
    private SceneDto mapToScene(Map<String, Object> sm, int offset) {
        SceneDto s = new SceneDto();
        s.setSceneNumber(sm.get("sceneNumber") instanceof Number n ? n.intValue() : offset + 1);
        s.setLocation(str(sm, "location", "UNKNOWN"));
        s.setTimeOfDay(str(sm, "timeOfDay", "DAY"));
        s.setInterior(str(sm, "interior", "INT"));
        s.setDescription(str(sm, "description", ""));
        s.setDominantEmotion(str(sm, "dominantEmotion", "NEUTRAL"));
        s.setCharacters(sm.get("characters") instanceof List<?> l
                ? l.stream().map(Object::toString).toList() : List.of());
        s.setActionIntensity(dbl(sm, "actionIntensity"));
        s.setEmotionalIntensity(dbl(sm, "emotionalIntensity"));
        s.setProductionComplexity(dbl(sm, "productionComplexity"));
        s.setHasVfx(bool(sm, "hasVfx"));
        s.setHasStunt(bool(sm, "hasStunt"));
        s.setHasLargecrowd(bool(sm, "hasLargecrowd"));
        return s;
    }

    private ScriptAnalysisResponse buildResponse(List<SceneDto> scenes, String genre, String tone) {
        ScriptAnalysisResponse res = new ScriptAnalysisResponse();
        res.setScenes(scenes);
        res.setTotalScenes(scenes.size());
        res.setGenre(genre);
        res.setScriptTone(tone);

        Set<String> allChars = new LinkedHashSet<>();
        Map<String, Integer> charFreq = new HashMap<>();
        Map<String, Integer> locFreq  = new HashMap<>();

        for (SceneDto sc : scenes) {
            if (sc.getCharacters() != null) {
                sc.getCharacters().forEach(c -> { allChars.add(c); charFreq.merge(c, 1, Integer::sum); });
            }
            locFreq.merge(sc.getLocation(), 1, Integer::sum);
        }

        res.setAllCharacters(new ArrayList<>(allChars));
        res.setCharacterFrequency(charFreq);
        res.setLocationFrequency(locFreq);
        res.setNightScenesCount((int) scenes.stream().filter(s -> "NIGHT".equals(s.getTimeOfDay())).count());
        res.setExtScenesCount((int)   scenes.stream().filter(s -> "EXT".equals(s.getInterior())).count());
        res.setVfxScenesCount((int)   scenes.stream().filter(SceneDto::isHasVfx).count());

        if (!scenes.isEmpty()) {
            res.setAvgActionIntensity(    scenes.stream().mapToDouble(SceneDto::getActionIntensity).average().orElse(0));
            res.setAvgEmotionalIntensity( scenes.stream().mapToDouble(SceneDto::getEmotionalIntensity).average().orElse(0));
            res.setAvgProductionComplexity(scenes.stream().mapToDouble(SceneDto::getProductionComplexity).average().orElse(0));
        }
        return res;
    }

    private String  str(Map<String,Object> m, String k, String def) { return m.get(k) instanceof String v ? v : def; }
    private double  dbl(Map<String,Object> m, String k)             { return m.get(k) instanceof Number n ? n.doubleValue() : 0.0; }
    private boolean bool(Map<String,Object> m, String k)            { return Boolean.TRUE.equals(m.get(k)); }
}
