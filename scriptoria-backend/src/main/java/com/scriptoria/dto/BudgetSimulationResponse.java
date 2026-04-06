package com.scriptoria.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class BudgetSimulationResponse {

    @Data
    public static class BudgetTier {
        private String tier;            // LOW | MID | HIGH
        private String currency;        // INR | USD | KRW
        private long totalBudget;

        // Breakdown by category (values in same currency)
        private long castBudget;
        private long locationsBudget;
        private long vfxBudget;
        private long crewBudget;
        private long postProductionBudget;
        private long marketingBudget;
        private long contingency;

        private List<String> keyAssumptions;
    }

    private String market;
    private String currency;

    private BudgetTier low;
    private BudgetTier mid;
    private BudgetTier high;

    // Derived cost drivers from the script
    private List<String> costDrivers;      // e.g. "17 night scenes increase lighting cost by ~20%"
    private int totalNightScenes;
    private int totalVfxScenes;
    private int totalUniqueLocations;
    private double avgActionIntensity;

    private String marketContext;          // e.g. "Tollywood market rates applied"
}
