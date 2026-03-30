package com.scriptoria.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class HistoryItemResponse {
    private UUID id;
    private String screenplayTitle;
    private String genre;
    private String scriptTone;
    private int totalScenes;
    private String market;
    private LocalDateTime createdAt;
    private Double avgActionIntensity;
    private Double avgEmotionalIntensity;
    private String dominantEmotion;
    private Integer peakTensionScene;
    private Long totalBudgetLow;
    private Long totalBudgetMid;
    private Long totalBudgetHigh;
    private String currency;
    private String dominantTone;
    private String emotionalJourney;
}
