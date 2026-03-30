package com.scriptoria.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scriptoria.dto.*;
import com.scriptoria.entity.AnalysisHistory;
import com.scriptoria.entity.User;
import com.scriptoria.repository.AnalysisHistoryRepository;
import com.scriptoria.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final UserRepository userRepository;
    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final ObjectMapper objectMapper;

    public UUID saveAnalysis(String userEmail,
                             String screenplay,
                             String market,
                             ScriptAnalysisResponse analysis,
                             EmotionAnalysisResponse emotion,
                             BudgetSimulationResponse budget) {
        User user = getUserByEmail(userEmail);

        AnalysisHistory history = new AnalysisHistory();
        history.setUser(user);
        history.setScreenplayTitle(extractTitle(screenplay));
        history.setGenre(analysis.getGenre());
        history.setScriptTone(analysis.getScriptTone());
        history.setTotalScenes(analysis.getTotalScenes());
        history.setMarket(market);

        history.setAnalysisJson(writeJson(analysis));
        history.setEmotionJson(writeJson(emotion));
        history.setBudgetJson(writeJson(budget));
        history.setStoryboardSnapshots("[]");

        history.setAvgActionIntensity(analysis.getAvgActionIntensity());
        history.setAvgEmotionalIntensity(analysis.getAvgEmotionalIntensity());
        history.setDominantEmotion(resolveDominantEmotion(emotion));
        history.setPeakTensionScene(emotion.getPeakTensionScene());
        history.setTotalBudgetLow(budget.getLow() != null ? budget.getLow().getTotalBudget() : null);
        history.setTotalBudgetMid(budget.getMid() != null ? budget.getMid().getTotalBudget() : null);
        history.setTotalBudgetHigh(budget.getHigh() != null ? budget.getHigh().getTotalBudget() : null);
        history.setCurrency(budget.getCurrency());
        history.setDominantTone(emotion.getDominantTone());
        history.setEmotionalJourney(emotion.getEmotionalJourney());

        return analysisHistoryRepository.save(history).getId();
    }

    public void saveStoryboardSnapshot(UUID historyId, StoryboardResponse storyboard) {
        AnalysisHistory history = analysisHistoryRepository.findById(historyId)
                .orElseThrow(() -> new IllegalArgumentException("History not found"));

        List<StoryboardResponse> snapshots = readStoryboardSnapshots(history.getStoryboardSnapshots());
        snapshots.add(storyboard);
        if (snapshots.size() > 3) {
            snapshots = new ArrayList<>(snapshots.subList(snapshots.size() - 3, snapshots.size()));
        }

        history.setStoryboardSnapshots(writeJson(snapshots));
        analysisHistoryRepository.save(history);
    }

    public HistoryPageResponse getUserHistory(String userEmail, int page, int size) {
        User user = getUserByEmail(userEmail);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AnalysisHistory> historyPage = analysisHistoryRepository.findByUser(user, pageable);

        HistoryPageResponse response = new HistoryPageResponse();
        response.setItems(historyPage.getContent().stream().map(this::toHistoryItem).toList());
        response.setTotalItems(historyPage.getTotalElements());
        response.setTotalPages(historyPage.getTotalPages());
        response.setCurrentPage(historyPage.getNumber());
        response.setPageSize(historyPage.getSize());
        return response;
    }

    public HistoryDetailResponse getHistoryDetail(String userEmail, UUID historyId) {
        AnalysisHistory history = loadOwnedHistory(userEmail, historyId);

        HistoryDetailResponse response = new HistoryDetailResponse();
        copyItemFields(history, response);
        response.setAnalysisData(readJson(history.getAnalysisJson(), ScriptAnalysisResponse.class));
        response.setEmotionData(readJson(history.getEmotionJson(), EmotionAnalysisResponse.class));
        response.setBudgetData(readJson(history.getBudgetJson(), BudgetSimulationResponse.class));
        response.setStoryboardSnapshots(readStoryboardSnapshots(history.getStoryboardSnapshots()));
        return response;
    }

    public void deleteHistory(String userEmail, UUID historyId) {
        AnalysisHistory history = loadOwnedHistory(userEmail, historyId);
        analysisHistoryRepository.delete(history);
    }

    private AnalysisHistory loadOwnedHistory(String userEmail, UUID historyId) {
        AnalysisHistory history = analysisHistoryRepository.findById(historyId)
                .orElseThrow(() -> new IllegalArgumentException("History not found"));
        String normalized = userEmail.trim().toLowerCase();
        if (!history.getUser().getEmail().equalsIgnoreCase(normalized)) {
            throw new AccessDeniedException("You do not have access to this history record");
        }
        return history;
    }

    private User getUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private HistoryItemResponse toHistoryItem(AnalysisHistory history) {
        HistoryItemResponse response = new HistoryItemResponse();
        copyItemFields(history, response);
        return response;
    }

    private void copyItemFields(AnalysisHistory history, HistoryItemResponse response) {
        response.setId(history.getId());
        response.setScreenplayTitle(history.getScreenplayTitle());
        response.setGenre(history.getGenre());
        response.setScriptTone(history.getScriptTone());
        response.setTotalScenes(history.getTotalScenes());
        response.setMarket(history.getMarket());
        response.setCreatedAt(history.getCreatedAt());
        response.setAvgActionIntensity(history.getAvgActionIntensity());
        response.setAvgEmotionalIntensity(history.getAvgEmotionalIntensity());
        response.setDominantEmotion(history.getDominantEmotion());
        response.setPeakTensionScene(history.getPeakTensionScene());
        response.setTotalBudgetLow(history.getTotalBudgetLow());
        response.setTotalBudgetMid(history.getTotalBudgetMid());
        response.setTotalBudgetHigh(history.getTotalBudgetHigh());
        response.setCurrency(history.getCurrency());
        response.setDominantTone(history.getDominantTone());
        response.setEmotionalJourney(history.getEmotionalJourney());
    }

    private String extractTitle(String screenplay) {
        if (screenplay == null || screenplay.isBlank()) {
            return "Untitled Screenplay";
        }
        return screenplay.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .map(line -> line.length() > 60 ? line.substring(0, 60) : line)
                .orElse("Untitled Screenplay");
    }

    private String resolveDominantEmotion(EmotionAnalysisResponse emotion) {
        if (emotion == null) return "UNKNOWN";
        if (emotion.getOverallProfile() != null && !emotion.getOverallProfile().isEmpty()) {
            return emotion.getOverallProfile().entrySet().stream()
                    .max(Comparator.comparingDouble(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElseGet(() -> Optional.ofNullable(emotion.getDominantTone()).orElse("UNKNOWN"));
        }
        if (emotion.getArc() != null && !emotion.getArc().isEmpty()) {
            String value = emotion.getArc().get(0).getDominantEmotion();
            if (value != null && !value.isBlank()) return value;
        }
        return Optional.ofNullable(emotion.getDominantTone()).orElse("UNKNOWN");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize history JSON: " + e.getMessage(), e);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize history JSON: " + e.getMessage(), e);
        }
    }

    private List<StoryboardResponse> readStoryboardSnapshots(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize storyboard snapshots: " + e.getMessage(), e);
        }
    }
}
