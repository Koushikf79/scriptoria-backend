package com.scriptoria.controller;

import com.scriptoria.dto.SceneAnimationRequest;
import com.scriptoria.dto.SceneAnimationResponse;
import com.scriptoria.dto.SceneImageBatchRequest;
import com.scriptoria.dto.SceneImageBatchResponse;
import com.scriptoria.dto.SceneImageRequest;
import com.scriptoria.dto.SceneImageResponse;
import com.scriptoria.service.HuggingFaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/v1/visual")
@RequiredArgsConstructor
@Tag(name = "Visual", description = "Scene image and animation generation")
public class VisualController {

    private final HuggingFaceService huggingFaceService;

    @PostMapping("/scene-image")
    @Operation(summary = "Generate one image for a scene")
    public ResponseEntity<SceneImageResponse> generateSceneImage(@Valid @RequestBody SceneImageRequest request) {
        log.info("Scene image request for scene {}", request.getSceneNumber());

        SceneImageResponse response = new SceneImageResponse();
        response.setSceneNumber(request.getSceneNumber());
        response.setPrompt(huggingFaceService.previewImagePrompt(request));
        response.setModel(huggingFaceService.getImageModel());
        response.setImageBase64(huggingFaceService.generateSceneImage(request));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/scene-animation")
    @Operation(summary = "Generate one short animation for a scene")
    public WebAsyncTask<ResponseEntity<SceneAnimationResponse>> generateSceneAnimation(
            @Valid @RequestBody SceneAnimationRequest request
    ) {
        log.info("Scene animation request for scene {}", request.getSceneNumber());

        return new WebAsyncTask<>(120_000L, () -> {
            SceneAnimationResponse response = new SceneAnimationResponse();
            response.setSceneNumber(request.getSceneNumber());
            response.setPrompt(huggingFaceService.previewAnimationPrompt(request));
            response.setModel(huggingFaceService.getAnimationModel());
            response.setDurationSeconds(3);
            response.setVideoBase64(huggingFaceService.generateSceneAnimation(request));
            response.setGifBase64(null);
            response.setWarmUpRequired(huggingFaceService.wasLastAnimationWarmUpRequired());
            return ResponseEntity.ok(response);
        });
    }

    @PostMapping("/scene-image-batch")
    @Operation(summary = "Generate up to 5 scene images in parallel")
    public ResponseEntity<SceneImageBatchResponse> generateSceneImageBatch(
            @Valid @RequestBody SceneImageBatchRequest request
    ) {
        List<CompletableFuture<SceneImageResponse>> futures = request.getScenes().stream()
                .map(scene -> CompletableFuture.supplyAsync(() -> toImageResponse(scene)))
                .toList();

        SceneImageBatchResponse response = new SceneImageBatchResponse();
        response.setImages(futures.stream().map(CompletableFuture::join).toList());
        return ResponseEntity.ok(response);
    }

    private SceneImageResponse toImageResponse(SceneImageRequest request) {
        SceneImageResponse response = new SceneImageResponse();
        response.setSceneNumber(request.getSceneNumber());
        response.setPrompt(huggingFaceService.previewImagePrompt(request));
        response.setModel(huggingFaceService.getImageModel());
        response.setImageBase64(huggingFaceService.generateSceneImage(request));
        return response;
    }
}
