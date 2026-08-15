package com.betai.api.dto;

import com.betai.domain.prediction.PredictionConfidenceBand;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LeagueMarketReadinessResponse(
        String leagueCode,
        String leagueName,
        String marketCode,
        String marketName,
        String modelVersion,
        LocalDate asOfDate,
        int minimumSampleSizeRequired,
        long settledSelectionsFound,
        long pricedSelectionsFound,
        boolean hasResultsSource,
        boolean hasFixtureSource,
        boolean hasOddsReferenceSource,
        boolean hasQualitySnapshot,
        LocalDate qualityDate,
        Integer qualitySampleSize,
        PredictionConfidenceBand confidenceBand,
        BigDecimal brierScore,
        BigDecimal calibrationError,
        boolean calibrationReady,
        boolean hasActiveGlobalTuningProfile,
        LocalDate tuningProfileDate,
        Integer tuningSampleSize,
        boolean optimizedProbabilityReady,
        boolean valueStrategyDataReady,
        ModelReadinessStatus status,
        List<String> missingSteps
) {
}
