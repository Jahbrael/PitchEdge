package com.betai.service;

import com.betai.api.dto.SelectionStrategy;

import java.math.BigDecimal;

record PredictionBatchBuildRequest(
        SelectionStrategy strategy,
        int numberOfBatches,
        int minimumSelections,
        int maximumSelections,
        int qualifiedSelectionsFound,
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
}
