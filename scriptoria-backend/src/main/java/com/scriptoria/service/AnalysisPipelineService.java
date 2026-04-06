package com.scriptoria.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scriptoria.dto.BudgetSimulationResponse;
import com.scriptoria.dto.EmotionAnalysisResponse;
import com.scriptoria.dto.EmotionAnalysisResponse.EmotionPoint;
import com.scriptoria.dto.ScreenplayRequest;
import com.scriptoria.dto.ScriptAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisPipelineService {

    private final ScriptAnalysisService scriptAnalysisService;
    private final EmotionAnalysisService emotionAnalysisService;
    private final BudgetSimulationService budgetSimulationService;
    private final HistoryService historyService;
    private final ObjectMapper objectMapper;

    public void runPipeline(WebSocketSession session, ScreenplayRequest request) {
        try {
            sendProgress(session, "SCRIPT_ANALYSIS", 10, "Parsing screenplay scenes...");
            ScriptAnalysisResponse analysis = scriptAnalysisService.analyze(request.getScreenplay());
            sendPayload(session, "ANALYSIS", analysis);

            sendProgress(session, "EMOTION_ANALYSIS", 40, "Mapping emotional arcs...");
            EmotionAnalysisResponse emotions = emotionAnalysisService.analyze(analysis.getScenes());
            sendPayload(session, "EMOTION", emotions);

            sendProgress(session, "BUDGET_SIMULATION", 70, "Calculating budget tiers...");
            String market = request.getMarket() != null ? request.getMarket() : "GENERAL";
            BudgetSimulationResponse budget = budgetSimulationService.simulate(analysis, market);
            sendPayload(session, "BUDGET", budget);

            Object userEmailAttr = session.getAttributes().get("userEmail");
            if (userEmailAttr instanceof String userEmail && !userEmail.isBlank()) {
                historyService.saveAnalysis(userEmail, request.getScreenplay(), market, analysis, emotions, budget);
            } else {
                log.debug("No authenticated user on websocket session {}, skipping history save", session.getId());
            }

            sendEvent(session, Map.of("type", "COMPLETE"));
        } catch (Exception e) {
            log.error("Pipeline failed: {}", e.getMessage(), e);
            sendError(session, e.getMessage());
        }
    }

    private void sendProgress(WebSocketSession session, String stage, int pct, String message) {
        sendEvent(session, Map.of(
                "type", "PROGRESS",
                "stage", stage,
                "percentage", pct,
                "message", message
        ));
    }

    private void sendPayload(WebSocketSession session, String type, Object data) {
        try {
            Object responseData = data;
            if ("EMOTION".equals(type) && data instanceof EmotionAnalysisResponse emotionData) {
                responseData = toEmotionCompatibilityMap(emotionData);
            }
            Map<String, Object> envelope = Map.of("type", type, "data", responseData);
            send(session, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.error("Failed to serialize {} payload: {}", type, e.getMessage());
        }
    }

    private Map<String, Object> toEmotionCompatibilityMap(EmotionAnalysisResponse emotion) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("arc", emotion.getArc());
        out.put("emotional_arc", toSnakeArc(emotion.getArc()));
        out.put("overallProfile", emotion.getOverallProfile());
        out.put("overall_profile", emotion.getOverallProfile());
        out.put("dominantTone", emotion.getDominantTone());
        out.put("dominant_tone", emotion.getDominantTone());
        out.put("peakTensionScene", emotion.getPeakTensionScene());
        out.put("peak_tension_scene", emotion.getPeakTensionScene());
        out.put("peakJoyScene", emotion.getPeakJoyScene());
        out.put("peak_joy_scene", emotion.getPeakJoyScene());
        out.put("emotionalJourney", emotion.getEmotionalJourney());
        out.put("emotional_journey", emotion.getEmotionalJourney());
        return out;
    }

    private List<Map<String, Object>> toSnakeArc(List<EmotionPoint> arc) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (arc == null) return list;
        for (EmotionPoint p : arc) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("scene_number", p.getSceneNumber());
            m.put("location", p.getLocation());
            m.put("tension", p.getTension());
            m.put("joy", p.getJoy());
            m.put("grief", p.getGrief());
            m.put("fear", p.getFear());
            m.put("anger", p.getAnger());
            m.put("hope", p.getHope());
            m.put("love", p.getLove());
            m.put("dominant_emotion", p.getDominantEmotion());
            m.put("emotion_note", p.getEmotionNote());
            list.add(m);
        }
        return list;
    }

    private void sendError(WebSocketSession session, String message) {
        sendEvent(session, Map.of("type", "ERROR", "message", message != null ? message : "Unknown error"));
    }

    private void sendEvent(WebSocketSession session, Map<String, ?> payload) {
        try {
            send(session, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Failed to send websocket event: {}", e.getMessage());
        }
    }

    private synchronized void send(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.error("Websocket send failed: {}", e.getMessage());
        }
    }
}
