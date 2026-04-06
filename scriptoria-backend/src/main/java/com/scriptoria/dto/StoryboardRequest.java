package com.scriptoria.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StoryboardRequest {

    @Min(value = 1, message = "Scene number must be >= 1")
    private int sceneNumber;

    @NotBlank(message = "Scene description must not be blank")
    private String sceneDescription;

    private String location;
    private String timeOfDay;
    private String dominantEmotion;
    private double actionIntensity;
}
