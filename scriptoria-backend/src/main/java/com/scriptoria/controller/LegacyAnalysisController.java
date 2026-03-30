package com.scriptoria.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scriptoria.dto.BudgetSimulationResponse;
import com.scriptoria.dto.SceneDto;
import com.scriptoria.dto.ScriptAnalysisResponse;
import com.scriptoria.service.BudgetSimulationService;
import com.scriptoria.service.ScriptAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analyze")
public class LegacyAnalysisController {

    private final ScriptAnalysisService scriptAnalysisService;
    private final BudgetSimulationService budgetSimulationService;
    private final ObjectMapper objectMapper;

    @PostMapping("/budget")
    public ResponseEntity<Map<String, Object>> estimateBudget(@RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null) payload = Map.of();
        try {
            String market = readMarket(payload);
            ScriptAnalysisResponse analysis = resolveAnalysis(payload);
            BudgetSimulationResponse budget = budgetSimulationService.simulate(analysis, market);
            return ResponseEntity.ok(toBudgetCompatibilityMap(budget, market));
        } catch (Exception ex) {
            String market = readMarket(payload);
            ScriptAnalysisResponse analysis;
            try {
                analysis = resolveAnalysis(payload);
            } catch (Exception ignored) {
                analysis = buildFallbackAnalysis(payload);
            }
            log.warn("Budget endpoint fallback for market {}: {}", market, ex.getMessage());
            BudgetSimulationResponse fallback = buildFallbackBudget(analysis, market);
            return ResponseEntity.ok(toBudgetCompatibilityMap(fallback, market));
        }
    }

    @PostMapping("/risk")
    public ResponseEntity<Map<String, Object>> evaluateRisk(@RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null) payload = Map.of();
        try {
            String market = readMarket(payload);
            ScriptAnalysisResponse analysis = resolveAnalysis(payload);
            BudgetSimulationResponse budget;
            try {
                budget = budgetSimulationService.simulate(analysis, market);
            } catch (Exception ex) {
                log.warn("Risk endpoint budget AI unavailable, using fallback budget for market {}: {}", market, ex.getMessage());
                budget = buildFallbackBudget(analysis, market);
            }

            int riskScore = computeRiskScore(analysis);
            String riskLevel = riskScore >= 75 ? "HIGH" : (riskScore >= 45 ? "MEDIUM" : "LOW");
            long midBudget = budget.getMid() != null ? budget.getMid().getTotalBudget() : 0L;

            List<String> riskFactors = new ArrayList<>();
            if (analysis.getVfxScenesCount() > 5) riskFactors.add("High VFX scene count may increase execution and post costs.");
            if (analysis.getNightScenesCount() > 8) riskFactors.add("Many night scenes can stretch schedule and lighting budget.");
            if ((analysis.getLocationFrequency() != null ? analysis.getLocationFrequency().size() : 0) > 10) {
                riskFactors.add("Frequent location switches may increase logistics complexity.");
            }
            if (analysis.getAvgActionIntensity() >= 6.0) riskFactors.add("Action-heavy staging increases stunt and safety overhead.");
            if (riskFactors.isEmpty()) riskFactors.add("No major production risk spikes detected from screenplay structure.");

            List<String> recommendations = List.of(
                    "Lock top 3 high-cost scenes in pre-production with detailed call sheets.",
                    "Reserve contingency for VFX, weather, and location slippage.",
                    "Prioritize schedule clustering for similar locations/time-of-day blocks."
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("market", market);
            response.put("riskScore", riskScore);
            response.put("risk_score", riskScore);
            response.put("riskLevel", riskLevel);
            response.put("risk_level", riskLevel);
            response.put("overallRisk", riskLevel);
            response.put("overall_risk", riskLevel);
            response.put("summary", "Estimated production risk is " + riskLevel + " (" + riskScore + "/100).");
            response.put("riskFactors", riskFactors);
            response.put("risk_factors", riskFactors);
            response.put("recommendations", recommendations);
            response.put("estimatedMidBudget", midBudget);
            response.put("estimated_mid_budget", midBudget);
            response.put("currency", budget.getCurrency());
            response.put("costVolatility", riskLevel);
            response.put("cost_volatility", riskLevel);
            response.put("contingencySuggestionPct", riskLevel.equals("HIGH") ? 18 : (riskLevel.equals("MEDIUM") ? 12 : 8));
            response.put("contingency_suggestion_pct", response.get("contingencySuggestionPct"));
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            String market = readMarket(payload);
            ScriptAnalysisResponse analysis;
            try {
                analysis = resolveAnalysis(payload);
            } catch (Exception ignored) {
                analysis = buildFallbackAnalysis(payload);
            }
            BudgetSimulationResponse budget = buildFallbackBudget(analysis, market);
            int riskScore = computeRiskScore(analysis);
            String riskLevel = riskScore >= 75 ? "HIGH" : (riskScore >= 45 ? "MEDIUM" : "LOW");
            log.warn("Risk endpoint hard fallback for market {}: {}", market, ex.getMessage());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("market", market);
            response.put("riskScore", riskScore);
            response.put("risk_score", riskScore);
            response.put("riskLevel", riskLevel);
            response.put("risk_level", riskLevel);
            response.put("overallRisk", riskLevel);
            response.put("overall_risk", riskLevel);
            response.put("summary", "Estimated production risk is " + riskLevel + " (" + riskScore + "/100).");
            response.put("riskFactors", List.of("Risk computed from fallback screenplay complexity signals."));
            response.put("risk_factors", response.get("riskFactors"));
            response.put("recommendations", List.of(
                    "Use contingency reserves for uncertain scenes.",
                    "Prioritize schedule clustering by location."
            ));
            long midBudget = budget.getMid() != null ? budget.getMid().getTotalBudget() : 0L;
            response.put("estimatedMidBudget", midBudget);
            response.put("estimated_mid_budget", midBudget);
            response.put("currency", budget.getCurrency());
            response.put("costVolatility", riskLevel);
            response.put("cost_volatility", riskLevel);
            response.put("contingencySuggestionPct", riskLevel.equals("HIGH") ? 18 : (riskLevel.equals("MEDIUM") ? 12 : 8));
            response.put("contingency_suggestion_pct", response.get("contingencySuggestionPct"));
            return ResponseEntity.ok(response);
        }
    }

    private ScriptAnalysisResponse resolveAnalysis(Map<String, Object> payload) {
        Object analysisObj = firstPresent(payload,
                "analysis", "analysisData", "analysis_data",
                "scriptAnalysis", "script_analysis",
                "screenplayAnalysis", "screenplay_analysis",
                "result", "data");
        if (analysisObj instanceof Map<?, ?> m) {
            return normalizeAnalysis(objectMapper.convertValue(m, ScriptAnalysisResponse.class), payload);
        }

        if (payload.get("scenes") instanceof List<?> || payload.get("scene_data") instanceof List<?>) {
            return buildFallbackAnalysis(payload);
        }

        String screenplay = readText(payload, "screenplay", "scriptText", "script", "text");
        if (screenplay != null && !screenplay.isBlank()) {
            log.info("Legacy /api/analyze call using screenplay text length={}", screenplay.length());
            return scriptAnalysisService.analyze(screenplay);
        }

        // Be permissive for legacy frontends that only pass partial metrics.
        log.warn("Legacy /api/analyze call had no screenplay; using fallback analysis from partial payload keys.");
        return buildFallbackAnalysis(payload);
    }

    private String readMarket(Map<String, Object> payload) {
        String market = readText(payload, "market");
        return (market == null || market.isBlank()) ? "GENERAL" : market.toUpperCase(Locale.ROOT);
    }

    private String readText(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }

    private Object firstPresent(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key) && payload.get(key) != null) {
                return payload.get(key);
            }
        }
        return null;
    }

    private ScriptAnalysisResponse normalizeAnalysis(ScriptAnalysisResponse analysis, Map<String, Object> payload) {
        if (analysis == null) analysis = new ScriptAnalysisResponse();
        if (analysis.getScenes() == null) analysis.setScenes(List.of());
        if (analysis.getAllCharacters() == null) analysis.setAllCharacters(List.of());
        if (analysis.getCharacterFrequency() == null) analysis.setCharacterFrequency(Map.of());
        if (analysis.getLocationFrequency() == null) analysis.setLocationFrequency(Map.of());
        if (analysis.getGenre() == null || analysis.getGenre().isBlank()) analysis.setGenre("Unknown");
        if (analysis.getScriptTone() == null || analysis.getScriptTone().isBlank()) analysis.setScriptTone("Unknown");

        if (analysis.getTotalScenes() == 0) {
            analysis.setTotalScenes(readInt(payload, "totalScenes", "total_scenes", "sceneCount", "scene_count"));
            if (analysis.getTotalScenes() == 0 && analysis.getScenes() != null) {
                analysis.setTotalScenes(analysis.getScenes().size());
            }
        }
        return analysis;
    }

    @SuppressWarnings("unchecked")
    private ScriptAnalysisResponse buildFallbackAnalysis(Map<String, Object> payload) {
        ScriptAnalysisResponse analysis = new ScriptAnalysisResponse();
        analysis.setGenre(readText(payload, "genre", "scriptGenre", "script_genre") != null
                ? readText(payload, "genre", "scriptGenre", "script_genre") : "Unknown");
        analysis.setScriptTone(readText(payload, "scriptTone", "script_tone", "tone") != null
                ? readText(payload, "scriptTone", "script_tone", "tone") : "Unknown");

        List<SceneDto> scenes = new ArrayList<>();
        Object scenesObj = firstPresent(payload, "scenes", "scene_data", "sceneData");
        if (scenesObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    Map<String, Object> m = (Map<String, Object>) raw;
                    SceneDto s = new SceneDto();
                    s.setSceneNumber(readInt(m, "sceneNumber", "scene_number", "id", "sceneId"));
                    if (s.getSceneNumber() <= 0) s.setSceneNumber(scenes.size() + 1);
                    s.setLocation(readText(m, "location", "sceneLocation", "scene_location"));
                    if (s.getLocation() == null) s.setLocation("UNKNOWN");
                    s.setTimeOfDay(readText(m, "timeOfDay", "time_of_day", "time"));
                    if (s.getTimeOfDay() == null) s.setTimeOfDay("DAY");
                    s.setInterior(readText(m, "interior", "intExt", "int_ext"));
                    if (s.getInterior() == null) s.setInterior("INT");
                    s.setDescription(readText(m, "description", "sceneDescription", "scene_description"));
                    if (s.getDescription() == null) s.setDescription("");
                    s.setActionIntensity(readDouble(m, "actionIntensity", "action_intensity", "action"));
                    s.setEmotionalIntensity(readDouble(m, "emotionalIntensity", "emotional_intensity", "emotion"));
                    s.setProductionComplexity(readDouble(m, "productionComplexity", "production_complexity", "complexity"));
                    s.setHasVfx(readBool(m, "hasVfx", "has_vfx", "vfx"));
                    s.setHasStunt(readBool(m, "hasStunt", "has_stunt", "stunt"));
                    s.setHasLargecrowd(readBool(m, "hasLargecrowd", "has_largecrowd", "has_large_crowd", "crowd"));
                    s.setDominantEmotion(readText(m, "dominantEmotion", "dominant_emotion") != null
                            ? readText(m, "dominantEmotion", "dominant_emotion") : "NEUTRAL");
                    s.setCharacters(List.of());
                    scenes.add(s);
                }
            }
        }

        analysis.setScenes(scenes);
        analysis.setTotalScenes(scenes.isEmpty()
                ? readInt(payload, "totalScenes", "total_scenes", "sceneCount", "scene_count")
                : scenes.size());
        analysis.setAvgActionIntensity(readDouble(payload, "avgActionIntensity", "avg_action_intensity"));
        analysis.setAvgEmotionalIntensity(readDouble(payload, "avgEmotionalIntensity", "avg_emotional_intensity"));
        analysis.setAvgProductionComplexity(readDouble(payload, "avgProductionComplexity", "avg_production_complexity"));
        analysis.setNightScenesCount(readInt(payload, "nightScenesCount", "night_scenes_count", "nightScenes"));
        analysis.setVfxScenesCount(readInt(payload, "vfxScenesCount", "vfx_scenes_count", "vfxScenes"));
        analysis.setExtScenesCount(readInt(payload, "extScenesCount", "ext_scenes_count", "extScenes"));
        analysis.setAllCharacters(List.of());
        analysis.setCharacterFrequency(Map.of());

        Map<String, Integer> locationFreq = new LinkedHashMap<>();
        for (SceneDto s : scenes) {
            locationFreq.merge(s.getLocation(), 1, Integer::sum);
        }
        analysis.setLocationFrequency(locationFreq);
        return normalizeAnalysis(analysis, payload);
    }

    private int readInt(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number n) return n.intValue();
            if (value instanceof String s) {
                try { return Integer.parseInt(s.trim()); } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private double readDouble(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number n) return n.doubleValue();
            if (value instanceof String s) {
                try { return Double.parseDouble(s.trim()); } catch (Exception ignored) {}
            }
        }
        return 0.0;
    }

    private boolean readBool(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Boolean b) return b;
            if (value instanceof String s) return "true".equalsIgnoreCase(s.trim());
            if (value instanceof Number n) return n.intValue() != 0;
        }
        return false;
    }

    private int computeRiskScore(ScriptAnalysisResponse analysis) {
        int locations = analysis.getLocationFrequency() != null ? analysis.getLocationFrequency().size() : 0;
        double score =
                analysis.getAvgActionIntensity() * 6.0
                        + analysis.getVfxScenesCount() * 2.5
                        + analysis.getNightScenesCount() * 1.8
                        + locations * 1.5;
        return (int) Math.max(0, Math.min(100, Math.round(score)));
    }

    private Map<String, Object> toBudgetCompatibilityMap(BudgetSimulationResponse budget, String market) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("market", market);
        response.put("currency", budget.getCurrency());
        response.put("marketContext", budget.getMarketContext());
        response.put("market_context", budget.getMarketContext());
        response.put("costDrivers", budget.getCostDrivers());
        response.put("cost_drivers", budget.getCostDrivers());
        response.put("totalNightScenes", budget.getTotalNightScenes());
        response.put("total_night_scenes", budget.getTotalNightScenes());
        response.put("totalVfxScenes", budget.getTotalVfxScenes());
        response.put("total_vfx_scenes", budget.getTotalVfxScenes());
        response.put("totalUniqueLocations", budget.getTotalUniqueLocations());
        response.put("total_unique_locations", budget.getTotalUniqueLocations());
        response.put("avgActionIntensity", budget.getAvgActionIntensity());
        response.put("avg_action_intensity", budget.getAvgActionIntensity());

        Map<String, Object> low = tierMap(budget.getLow());
        Map<String, Object> mid = tierMap(budget.getMid());
        Map<String, Object> high = tierMap(budget.getHigh());
        response.put("low", low);
        response.put("mid", mid);
        response.put("high", high);
        response.put("low_budget", low);
        response.put("mid_budget", mid);
        response.put("high_budget", high);
        response.put("tiers", Map.of("low", low, "mid", mid, "high", high));
        return response;
    }

    private Map<String, Object> tierMap(BudgetSimulationResponse.BudgetTier tier) {
        if (tier == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tier", tier.getTier());
        m.put("currency", tier.getCurrency());
        m.put("totalBudget", tier.getTotalBudget());
        m.put("total_budget", tier.getTotalBudget());
        m.put("castBudget", tier.getCastBudget());
        m.put("cast_budget", tier.getCastBudget());
        m.put("locationsBudget", tier.getLocationsBudget());
        m.put("locations_budget", tier.getLocationsBudget());
        m.put("vfxBudget", tier.getVfxBudget());
        m.put("vfx_budget", tier.getVfxBudget());
        m.put("crewBudget", tier.getCrewBudget());
        m.put("crew_budget", tier.getCrewBudget());
        m.put("postProductionBudget", tier.getPostProductionBudget());
        m.put("post_production_budget", tier.getPostProductionBudget());
        m.put("marketingBudget", tier.getMarketingBudget());
        m.put("marketing_budget", tier.getMarketingBudget());
        m.put("contingency", tier.getContingency());
        m.put("keyAssumptions", tier.getKeyAssumptions());
        m.put("key_assumptions", tier.getKeyAssumptions());
        return m;
    }

    private BudgetSimulationResponse buildFallbackBudget(ScriptAnalysisResponse analysis, String market) {
        BudgetSimulationResponse res = new BudgetSimulationResponse();
        res.setMarket(market);
        String currency = switch (market) {
            case "TOLLYWOOD", "BOLLYWOOD" -> "INR";
            case "KOREAN" -> "KRW";
            default -> "USD";
        };
        res.setCurrency(currency);
        res.setMarketContext("Fallback estimate based on screenplay complexity (AI temporarily unavailable).");
        res.setCostDrivers(List.of(
                "Scene count and location count drive schedule complexity.",
                "Night scenes increase lighting and crew overhead.",
                "VFX/action intensity increases post-production variance."
        ));
        res.setTotalNightScenes(analysis.getNightScenesCount());
        res.setTotalVfxScenes(analysis.getVfxScenesCount());
        res.setTotalUniqueLocations(analysis.getLocationFrequency() != null ? analysis.getLocationFrequency().size() : 0);
        res.setAvgActionIntensity(analysis.getAvgActionIntensity());

        long base = switch (market) {
            case "TOLLYWOOD" -> 50_000_000L;
            case "BOLLYWOOD" -> 80_000_000L;
            case "KOREAN" -> 5_000_000_000L;
            case "HOLLYWOOD" -> 8_000_000L;
            default -> 4_000_000L;
        };
        long complexityBoost = Math.round(
                analysis.getTotalScenes() * base * 0.01
                        + analysis.getVfxScenesCount() * base * 0.015
                        + analysis.getNightScenesCount() * base * 0.008
        );
        long lowTotal = Math.max(1L, base + complexityBoost / 2);
        long midTotal = Math.max(lowTotal + 1L, Math.round(lowTotal * 1.8));
        long highTotal = Math.max(midTotal + 1L, Math.round(midTotal * 2.2));

        res.setLow(makeTier("LOW", currency, lowTotal));
        res.setMid(makeTier("MID", currency, midTotal));
        res.setHigh(makeTier("HIGH", currency, highTotal));
        return res;
    }

    private BudgetSimulationResponse.BudgetTier makeTier(String name, String currency, long total) {
        BudgetSimulationResponse.BudgetTier t = new BudgetSimulationResponse.BudgetTier();
        t.setTier(name);
        t.setCurrency(currency);
        t.setTotalBudget(total);
        t.setCastBudget(Math.round(total * 0.20));
        t.setLocationsBudget(Math.round(total * 0.15));
        t.setVfxBudget(Math.round(total * 0.12));
        t.setCrewBudget(Math.round(total * 0.18));
        t.setPostProductionBudget(Math.round(total * 0.14));
        t.setMarketingBudget(Math.round(total * 0.14));
        t.setContingency(Math.round(total * 0.07));
        t.setKeyAssumptions(List.of(
                "Contingency reserved for schedule and post uncertainty.",
                "Location and crew costs scaled from scene complexity.",
                "Marketing assumed at standard theatrical campaign ratio."
        ));
        return t;
    }
}
