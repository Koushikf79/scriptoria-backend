package com.scriptoria.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class HistoryDetailResponse extends HistoryItemResponse {
    private ScriptAnalysisResponse analysisData;
    private EmotionAnalysisResponse emotionData;
    private BudgetSimulationResponse budgetData;
    private List<StoryboardResponse> storyboardSnapshots;
}
