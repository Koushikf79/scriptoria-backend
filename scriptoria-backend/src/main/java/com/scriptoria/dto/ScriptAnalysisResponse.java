package com.scriptoria.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ScriptAnalysisResponse {

    private int totalScenes;
    private List<String> allCharacters;
    private double avgActionIntensity;
    private double avgEmotionalIntensity;
    private double avgProductionComplexity;

    private int nightScenesCount;
    private int extScenesCount;
    private int vfxScenesCount;

    private List<SceneDto> scenes;
    private Map<String, Integer> locationFrequency;   // location -> scene count
    private Map<String, Integer> characterFrequency;  // character -> appearance count

    private String scriptTone;        // e.g. "Dark thriller with comedic undertones"
    private String genre;
}
