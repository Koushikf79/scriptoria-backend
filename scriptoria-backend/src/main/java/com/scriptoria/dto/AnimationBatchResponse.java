package com.scriptoria.dto;

import lombok.Data;

import java.util.List;

@Data
public class AnimationBatchResponse {
    private List<AnimationResponse> animations;
}
