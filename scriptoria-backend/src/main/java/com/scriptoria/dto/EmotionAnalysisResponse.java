package com.scriptoria.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class EmotionAnalysisResponse {

    @Data
    public static class EmotionPoint {
        private int sceneNumber;
        private String location;
        private double tension;
        private double joy;
        private double grief;
        private double fear;
        private double anger;
        private double hope;
        private double love;
        private String dominantEmotion;
        private String emotionNote;      // Brief reason: e.g. "Protagonist confronts betrayal"
    }

    private List<EmotionPoint> arc;

    // Aggregate emotional profile
    private Map<String, Double> overallProfile;   // emotion -> avg score
    private String dominantTone;                   // e.g. "Predominantly tense with hopeful resolution"
    private int peakTensionScene;
    private int peakJoyScene;
    private String emotionalJourney;               // One-sentence narrative arc summary
}
