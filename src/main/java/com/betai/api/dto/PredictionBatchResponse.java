package com.betai.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record PredictionBatchResponse(
        int batchNumber,
        SelectionStrategy strategy,
        int requestedMinimumSelections,
        int requestedMaximumSelections,
        int qualifiedSelectionsFound,
        int returnedSelections,
        BigDecimal averageModelProbability,
        BigDecimal estimatedIndependentBatchProbability,
        RiskBand riskLevel,
        int distinctLeagueCount,
        int distinctMarketGroupCount,
        List<String> correlationWarnings,
        PredictionResponseStatus status,
        String warningMessage,
        int selectionCount,
        BatchRiskMetricsResponse risk,
        List<PredictionSelectionResponse> selections
) {
}
