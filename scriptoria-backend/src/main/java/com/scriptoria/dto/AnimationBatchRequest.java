package com.scriptoria.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AnimationBatchRequest {

    @NotNull(message = "scenes is required")
    @Size(min = 1, max = 10, message = "scenes must contain between 1 and 10 items")
    private List<@Valid AnimationRequest> scenes;
}
