package com.scriptoria.controller;

import com.scriptoria.dto.StoryboardRequest;
import com.scriptoria.dto.StoryboardResponse;
import com.scriptoria.service.StoryboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/storyboard")
@RequiredArgsConstructor
@Tag(name = "Director Mode", description = "Cinematic storyboard shot generation")
public class StoryboardController {

    private final StoryboardService storyboardService;

    /**
     * POST /api/v1/storyboard/generate
     *
     * Generates 6 cinematic shot variations for a scene.
     * Called after the WS pipeline completes — user picks a scene and hits "Explore Visually".
     */
    @PostMapping("/generate")
    @Operation(summary = "Generate 6 storyboard shot variations for a scene")
    public ResponseEntity<StoryboardResponse> generate(@Valid @RequestBody StoryboardRequest request) {
        log.info("Storyboard request for scene {}", request.getSceneNumber());
        StoryboardResponse response = storyboardService.generate(request);
        return ResponseEntity.ok(response);
    }
}
