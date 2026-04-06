package com.scriptoria.dto;

import lombok.Data;

@Data
public class SceneAnimationResponse {
    private int sceneNumber;
    private String videoBase64;
    private String gifBase64;
    private String prompt;
    private int durationSeconds;
    private String model;
    private boolean warmUpRequired;
}
