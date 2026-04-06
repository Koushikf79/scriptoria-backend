package com.scriptoria.dto;

import lombok.Data;
import java.util.List;

@Data
public class SceneDto {

    private int sceneNumber;
    private String location;
    private String timeOfDay;            // DAY | NIGHT | DAWN | DUSK | CONTINUOUS
    private String interior;             // INT | EXT | INT/EXT
    private List<String> characters;
    private String description;          // Brief action summary

    // Intensity scores 0-10
    private double actionIntensity;
    private double emotionalIntensity;
    private double productionComplexity;

    // Derived
    private String dominantEmotion;      // e.g. TENSION, JOY, GRIEF
    private boolean hasVfx;
    private boolean hasStunt;
    private boolean hasLargecrowd;
}
