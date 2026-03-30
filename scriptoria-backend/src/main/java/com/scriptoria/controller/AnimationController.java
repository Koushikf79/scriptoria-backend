package com.scriptoria.controller;

import com.scriptoria.dto.AnimationBatchRequest;
import com.scriptoria.dto.AnimationBatchResponse;
import com.scriptoria.dto.AnimationRequest;
import com.scriptoria.dto.AnimationResponse;
import com.scriptoria.service.AnimationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/animation")
@RequiredArgsConstructor
@Tag(name = "Animation", description = "CSS animation generation per scene")
public class AnimationController {

    private final AnimationService animationService;

    @PostMapping("/generate")
    @Operation(summary = "Generate CSS animation for a scene")
    public ResponseEntity<AnimationResponse> generate(@Valid @RequestBody AnimationRequest request) {
        log.info("Animation request for scene {}", request.getSceneNumber());
        AnimationResponse response = animationService.generate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate-batch")
    @Operation(summary = "Generate CSS animations for up to 10 scenes")
    public ResponseEntity<AnimationBatchResponse> generateBatch(@Valid @RequestBody AnimationBatchRequest request) {
        log.info("Batch animation request with {} scenes", request.getScenes().size());
        AnimationBatchResponse response = animationService.generateBatch(request);
        return ResponseEntity.ok(response);
    }
}
