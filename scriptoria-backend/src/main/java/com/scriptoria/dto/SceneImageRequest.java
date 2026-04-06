package com.scriptoria.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SceneImageRequest {

    @Min(value = 1, message = "sceneNumber must be >= 1")
    private int sceneNumber;

    @NotBlank(message = "location must not be blank")
    private String location;

    @NotBlank(message = "timeOfDay must not be blank")
    private String timeOfDay;

    @NotBlank(message = "dominantEmotion must not be blank")
    private String dominantEmotion;

    private String scriptTone;
    private String genre;
}
