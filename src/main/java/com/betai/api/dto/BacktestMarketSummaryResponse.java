package com.betai.api.dto;

import com.betai.domain.backtest.BacktestMarketSummary;

import java.math.BigDecimal;
import java.util.UUID;

public record BacktestMarketSummaryResponse(
        UUID summaryId,
        String leagueCode,
        String marketCode,
        String marketName,
        int sampleSize,
        int wonCount,
        int lostCount,
        int voidCount,
        int pricedCount,
        BigDecimal observedWinRate,
        BigDecimal averageProbability,
        BigDecimal brierScore,
        BigDecimal calibrationError,
        BigDecimal averageExpectedValue,
        BigDecimal realizedRoi,
        BigDecimal recommendedProbabilityAdjustment,
        String tuningRecommendation
) {
    public static BacktestMarketSummaryResponse from(BacktestMarketSummary summary) {
        return new BacktestMarketSummaryResponse(
                summary.getId(),
                summary.getLeague().getCode().name(),
                summary.getMarketDefinition().getCode().name(),
                summary.getMarketDefinition().getDisplayName(),
                summary.getSampleSize(),
                summary.getWonCount(),
                summary.getLostCount(),
                summary.getVoidCount(),
                summary.getPricedCount(),
                summary.getObservedWinRate(),
                summary.getAverageProbability(),
                summary.getBrierScore(),
                summary.getCalibrationError(),
                summary.getAverageExpectedValue(),
                summary.getRealizedRoi(),
                summary.getRecommendedProbabilityAdjustment(),
                summary.getTuningRecommendation().name()
        );
    }
}
