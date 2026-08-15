package com.betai.api.dto;

public record DashboardTotalsResponse(
        long totalLeagues,
        long activeLeagues,
        long enabledMarkets,
        long sourceTargets,
        long activeSourceTargets,
        long teams,
        long matches,
        long scheduledMatches,
        long finishedMatches,
        long predictionSelections,
        long pendingSelections,
        long wonSelections,
        long lostSelections,
        long voidSelections,
        long accuracyRows,
        long bookmakers,
        long activeBookmakers,
        long oddsSnapshots,
        long pricedSelections,
        long positiveValueSelections,
        long modelTuningProfiles,
        long activeModelTuningProfiles,
        long automationRuns,
        long failedAutomationRuns
) {
}
