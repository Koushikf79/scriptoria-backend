package com.scriptoria.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class StoryboardResponse {

    @Data
    public static class ShotVariation {
        private String shotType;           // WIDE_SHOT | CLOSE_UP | LOW_ANGLE | TOP_ANGLE | OVER_SHOULDER | DUTCH_ANGLE
        private String lightingStyle;      // DAY_NATURAL | NIGHT_NEON | GOLDEN_HOUR | SILHOUETTE | HIGH_KEY | LOW_KEY
        private String lens;               // e.g. "35mm prime", "85mm portrait", "24mm wide"
        private String cameraMovement;     // STATIC | DOLLY_IN | TRACKING | HANDHELD | CRANE | DRONE
        private String mood;               // e.g. "Ominous and claustrophobic"
        private String composition;        // e.g. "Subject in lower-left third, negative space suggests isolation"
        private String colorGrading;       // e.g. "Desaturated teal/orange split tone"
        private String detailedPrompt;     // Full cinematic prompt for director reference
    }

    private int sceneNumber;
    private String sceneDescription;
    private String location;
    private String timeOfDay;

    private List<ShotVariation> variations;  // Always 6 variations
    private String directorNote;             // AI suggestion on the scene's visual intent
}
