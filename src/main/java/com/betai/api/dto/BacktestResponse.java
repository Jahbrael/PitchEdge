package com.betai.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BacktestResponse(
        UUID backtestRunId,
        String status,
        String modelVersion,
        LocalDate backtestDate,
        LocalDate matchDateFrom,
        LocalDate matchDateTo,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        int totalSelections,
        int totalWon,
        int totalLost,
        int totalVoid,
        int totalPriced,
        BigDecimal observedWinRate,
        BigDecimal averageProbability,
        BigDecimal brierScore,
        BigDecimal calibrationError,
        BigDecimal averageExpectedValue,
        BigDecimal realizedRoi,
        String summary,
        List<BacktestMarketSummaryResponse> marketSummaries
) {
}
