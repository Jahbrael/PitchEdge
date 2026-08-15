package com.betai.api.dto;

import com.betai.domain.settlement.ModelAccuracyDaily;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ModelAccuracyResponse(
        UUID accuracyId,
        String leagueCode,
        String marketCode,
        String marketName,
        String modelVersion,
        LocalDate accuracyDate,
        int settledSelections,
        int wonCount,
        int lostCount,
        int voidCount,
        BigDecimal winRate,
        BigDecimal averageProbability,
        BigDecimal brierScore,
        BigDecimal calibrationError
) {
    public static ModelAccuracyResponse from(ModelAccuracyDaily accuracy) {
        return new ModelAccuracyResponse(
                accuracy.getId(),
                accuracy.getLeague().getCode().name(),
                accuracy.getMarketDefinition().getCode().name(),
                accuracy.getMarketDefinition().getDisplayName(),
                accuracy.getModelVersion(),
                accuracy.getAccuracyDate(),
                accuracy.getSettledSelections(),
                accuracy.getWonCount(),
                accuracy.getLostCount(),
                accuracy.getVoidCount(),
                accuracy.getWinRate(),
                accuracy.getAverageProbability(),
                accuracy.getBrierScore(),
                accuracy.getCalibrationError()
        );
    }
}
