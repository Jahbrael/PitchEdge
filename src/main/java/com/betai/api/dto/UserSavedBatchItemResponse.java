package com.betai.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserSavedBatchItemResponse(
        UUID id,
        UUID predictionSelectionId,
        UUID matchId,
        String leagueCode,
        String fixture,
        OffsetDateTime kickoffAt,
        String marketCode,
        String marketName,
        String predictedValue,
        String teamOrPlayer,
        BigDecimal rawModelProbability,
        BigDecimal calibratedProbability,
        BigDecimal tunedModelProbability,
        String confidenceBand,
        BigDecimal dataQualityScore,
        Integer historicalSampleSize,
        String calibrationStatus,
        Integer modelQualitySampleSize,
        BigDecimal modelQualityCalibrationError,
        BigDecimal decimalOdds,
        BigDecimal bookmakerImpliedProbability,
        BigDecimal probabilityEdge,
        BigDecimal expectedValue,
        String valueRating,
        BigDecimal rankingScore,
        String reason,
        String modelVersion,
        OffsetDateTime generatedAt
) {
}
