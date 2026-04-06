package com.scriptoria.dto;

import lombok.Data;

import java.util.List;

@Data
public class SceneImageBatchResponse {
    private List<SceneImageResponse> images;
}
