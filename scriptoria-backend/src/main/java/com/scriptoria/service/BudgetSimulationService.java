package com.scriptoria.service;

import com.scriptoria.dto.BudgetSimulationResponse;
import com.scriptoria.dto.BudgetSimulationResponse.BudgetTier;
import com.scriptoria.dto.ScriptAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetSimulationService {

    private final AiService aiService;

    private static final String SYSTEM_PROMPT = """
            You are a senior film production accountant with expertise across cinema markets.
            Given a screenplay analysis, produce a three-tier budget simulation (LOW, MID, HIGH).

            Market rate guidance:
            - TOLLYWOOD (Telugu): LOW=2-8 Cr INR, MID=10-40 Cr INR, HIGH=50-200 Cr INR
            - BOLLYWOOD: LOW=3-15 Cr INR, MID=20-80 Cr INR, HIGH=100-500 Cr INR
            - HOLLYWOOD: LOW=1-5M USD, MID=10-40M USD, HIGH=80-250M USD
            - KOREAN: LOW=1-3B KRW, MID=5-15B KRW, HIGH=20-60B KRW
            - GENERAL: LOW=500K-2M USD, MID=5-20M USD, HIGH=40-120M USD

            For each tier (low, mid, high) provide:
            - tier, currency, totalBudget
            - castBudget, locationsBudget, vfxBudget, crewBudget, postProductionBudget, marketingBudget, contingency
            - keyAssumptions: array of 3 strings

            Also provide:
            - costDrivers: array of 4-5 strings about THIS script's specific cost factors
            - marketContext: one sentence

            Return ONLY valid JSON:
            {
              "marketContext": "...",
              "costDrivers": ["...", "..."],
              "low":  { "tier": "LOW",  "currency": "INR", "totalBudget": 50000000, ... },
              "mid":  { "tier": "MID",  ... },
              "high": { "tier": "HIGH", ... }
            }
            """;

    @SuppressWarnings("unchecked")
    public BudgetSimulationResponse simulate(ScriptAnalysisResponse analysis, String market) {
        log.info("Budget simulation market={} scenes={}", market, analysis.getTotalScenes());

        String userPrompt = buildSummary(analysis, market);
        Map<String, Object> raw = aiService.chatJson(SYSTEM_PROMPT, userPrompt, Map.class);

        String currency = switch (market.toUpperCase()) {
            case "TOLLYWOOD", "BOLLYWOOD" -> "INR";
            case "KOREAN" -> "KRW";
            default -> "USD";
        };

        BudgetSimulationResponse res = new BudgetSimulationResponse();
        res.setMarket(market);
        res.setCurrency(currency);
        res.setMarketContext(str(raw, "marketContext", ""));
        res.setTotalNightScenes(analysis.getNightScenesCount());
        res.setTotalVfxScenes(analysis.getVfxScenesCount());
        res.setTotalUniqueLocations(analysis.getLocationFrequency() != null ? analysis.getLocationFrequency().size() : 0);
        res.setAvgActionIntensity(analysis.getAvgActionIntensity());
        res.setCostDrivers(raw.get("costDrivers") instanceof List<?> l
                ? l.stream().map(Object::toString).toList() : List.of());
        res.setLow(mapTier(raw, "low",   currency));
        res.setMid(mapTier(raw, "mid",   currency));
        res.setHigh(mapTier(raw, "high", currency));
        return res;
    }

    private String buildSummary(ScriptAnalysisResponse a, String market) {
        String topLocs = Optional.ofNullable(a.getLocationFrequency())
                .map(m -> m.entrySet().stream()
                        .sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
                        .limit(5).map(e -> e.getKey() + "(" + e.getValue() + ")")
                        .reduce((x, y) -> x + ", " + y).orElse("N/A"))
                .orElse("N/A");

        String chars = Optional.ofNullable(a.getAllCharacters())
                .map(l -> String.join(", ", l.stream().limit(8).toList()))
                .orElse("N/A");

        return """
                TARGET MARKET: %s
                Total scenes: %d | Genre: %s | Tone: %s
                Night scenes: %d | Exterior: %d | VFX scenes: %d
                Unique locations: %d
                Avg action intensity: %.1f/10
                Avg emotional intensity: %.1f/10
                Avg production complexity: %.1f/10
                Main characters: %s
                Top locations: %s
                """.formatted(
                market, a.getTotalScenes(), a.getGenre(), a.getScriptTone(),
                a.getNightScenesCount(), a.getExtScenesCount(), a.getVfxScenesCount(),
                a.getLocationFrequency() != null ? a.getLocationFrequency().size() : 0,
                a.getAvgActionIntensity(), a.getAvgEmotionalIntensity(), a.getAvgProductionComplexity(),
                chars, topLocs
        );
    }

    @SuppressWarnings("unchecked")
    private BudgetTier mapTier(Map<String, Object> raw, String key, String currency) {
        Object val = raw.get(key);
        Map<String, Object> m = val instanceof Map<?,?> ? (Map<String, Object>) val : Map.of();

        BudgetTier t = new BudgetTier();
        t.setTier(key.toUpperCase());
        t.setCurrency(str(m, "currency", currency));
        t.setTotalBudget(lng(m, "totalBudget"));
        t.setCastBudget(lng(m, "castBudget"));
        t.setLocationsBudget(lng(m, "locationsBudget"));
        t.setVfxBudget(lng(m, "vfxBudget"));
        t.setCrewBudget(lng(m, "crewBudget"));
        t.setPostProductionBudget(lng(m, "postProductionBudget"));
        t.setMarketingBudget(lng(m, "marketingBudget"));
        t.setContingency(lng(m, "contingency"));
        t.setKeyAssumptions(m.get("keyAssumptions") instanceof List<?> l
                ? l.stream().map(Object::toString).toList() : List.of());
        return t;
    }

    private String str(Map<String,Object> m, String k, String def) { return m.get(k) instanceof String v ? v : def; }
    private long   lng(Map<String,Object> m, String k)             { return m.get(k) instanceof Number n ? n.longValue() : 0L; }
}
