package com.betai.service;

import com.betai.api.dto.RankingMode;
import com.betai.api.dto.SelectionStrategy;
import com.betai.domain.prediction.PredictionConfidenceBand;

import java.math.BigDecimal;

record PredictionStrategySettings(
        SelectionStrategy strategy,
        RankingMode rankingMode,
        BigDecimal minimumModelProbability,
        BigDecimal maximumModelProbability,
        BigDecimal minimumDecimalOdds,
        BigDecimal maximumDecimalOdds,
        PredictionConfidenceBand minimumConfidence,
        BigDecimal minimumDataQuality,
        int minimumHistoricalSample,
        BigDecimal minimumExpectedValue,
        BigDecimal minimumProbabilityEdge,
        boolean requireCalibration,
        boolean allowUnratedPredictions,
        boolean usesOddsForQualification,
        int maximumSelectionsPerMatch,
        BigDecimal allocationPercentage,
        java.util.Map<com.betai.domain.market.MarketCode, com.betai.api.dto.ProbabilityRange> marketProbabilityRanges
) {
}
