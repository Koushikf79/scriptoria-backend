package com.scriptoria.dto;

import lombok.Data;

@Data
public class SceneImageResponse {
    private int sceneNumber;
    private String imageBase64;
    private String prompt;
    private String model;
}
