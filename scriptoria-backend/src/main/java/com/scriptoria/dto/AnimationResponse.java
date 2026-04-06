package com.scriptoria.dto;

import lombok.Data;

@Data
public class AnimationResponse {

    @Data
    public static class ColorGrade {
        private String shadows;
        private String midtones;
        private String highlights;
        private double saturation;
    }

    private int sceneNumber;
    private String animationName;
    private String cssKeyframes;
    private String containerStyles;
    private String textStyles;
    private String overlayEffect;
    private String particleEffect;
    private String animationDuration;
    private String animationTimingFunction;
    private String animationIterationCount;
    private boolean lightFlicker;
    private double vignetteIntensity;
    private ColorGrade colorGrade;
    private String sceneLabel;
    private String moodTag;
    private String directorHint;
}
