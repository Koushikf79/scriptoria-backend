package com.scriptoria.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SceneAnimationRequest {

    @Min(value = 1, message = "sceneNumber must be >= 1")
    private int sceneNumber;

    @NotBlank(message = "location must not be blank")
    private String location;

    @NotBlank(message = "timeOfDay must not be blank")
    private String timeOfDay;

    @NotBlank(message = "dominantEmotion must not be blank")
    private String dominantEmotion;

    @NotBlank(message = "description must not be blank")
    private String description;

    private String cameraMovement;

    @NotNull(message = "actionIntensity is required")
    @DecimalMin(value = "0.0", message = "actionIntensity must be between 0 and 10")
    @DecimalMax(value = "10.0", message = "actionIntensity must be between 0 and 10")
    private Double actionIntensity;
}
