package com.scriptoria.controller;

import com.scriptoria.dto.HistoryDetailResponse;
import com.scriptoria.dto.HistoryPageResponse;
import com.scriptoria.dto.StoryboardResponse;
import com.scriptoria.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @GetMapping
    public ResponseEntity<HistoryPageResponse> getUserHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        HistoryPageResponse response = historyService.getUserHistory(authentication.getName(), page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<HistoryDetailResponse> getHistoryDetail(
            Authentication authentication,
            @PathVariable UUID id
    ) {
        HistoryDetailResponse response = historyService.getHistoryDetail(authentication.getName(), id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHistory(Authentication authentication, @PathVariable UUID id) {
        historyService.deleteHistory(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/storyboard")
    public ResponseEntity<Void> saveStoryboardSnapshot(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody StoryboardResponse storyboard
    ) {
        historyService.getHistoryDetail(authentication.getName(), id);
        historyService.saveStoryboardSnapshot(id, storyboard);
        return ResponseEntity.ok().build();
    }
}
