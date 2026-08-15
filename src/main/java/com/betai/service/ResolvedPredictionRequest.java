package com.betai.service;

import com.betai.api.dto.SelectionStrategy;
import com.betai.api.dto.ValueMode;

import java.math.BigDecimal;
import java.util.List;

record ResolvedPredictionRequest(
        String sport,
        SelectionStrategy strategy,
        int minimumSelections,
        int maximumSelections,
        int numberOfBatches,
        List<PredictionStrategySettings> strategySettings,
        boolean legacyValueModeActive,
        ValueMode legacyValueMode,
        boolean allowMultipleSelectionsFromSameMatch,
        int maximumSelectionsPerMatch,
        Integer maximumSelectionsPerTeam,
        Integer maximumSelectionsPerLeague,
        boolean requireMultipleLeagues,
        int minimumDistinctLeagues,
        boolean requireDifferentMarketGroups,
        boolean avoidCorrelatedSelections,
        boolean allowRepeatSelectionsAcrossBatches,
        BigDecimal minimumBatchDifferencePercentage,
        BigDecimal correlationTolerance
) {

    PredictionBatchBuildRequest toBatchBuildRequest(int qualifiedSelectionsFound) {
        return new PredictionBatchBuildRequest(
                strategy,
                numberOfBatches,
                minimumSelections,
                maximumSelections,
                qualifiedSelectionsFound,
                allowMultipleSelectionsFromSameMatch,
                maximumSelectionsPerMatch,
                maximumSelectionsPerTeam,
                maximumSelectionsPerLeague,
                requireMultipleLeagues,
                minimumDistinctLeagues,
                requireDifferentMarketGroups,
                avoidCorrelatedSelections,
                allowRepeatSelectionsAcrossBatches,
                minimumBatchDifferencePercentage,
                correlationTolerance
        );
    }
}
