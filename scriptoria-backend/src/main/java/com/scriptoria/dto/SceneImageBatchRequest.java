package com.scriptoria.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SceneImageBatchRequest {

    @NotNull(message = "scenes is required")
    @Size(min = 1, max = 5, message = "scenes must contain between 1 and 5 items")
    private List<@Valid SceneImageRequest> scenes;
}
