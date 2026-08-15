package com.betai.api.dto;

import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.quality.ModelQualitySnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ModelQualitySnapshotResponse(
        UUID qualitySnapshotId,
        String leagueCode,
        String marketCode,
        String marketName,
        String modelVersion,
        LocalDate qualityDate,
        int sampleSize,
        int wonCount,
        int lostCount,
        int voidCount,
        BigDecimal observedWinRate,
        BigDecimal averageRawProbability,
        BigDecimal brierScore,
        BigDecimal calibrationError,
        BigDecimal probabilityAdjustment,
        PredictionConfidenceBand confidenceBand,
        OffsetDateTime generatedAt
) {
    public static ModelQualitySnapshotResponse from(ModelQualitySnapshot snapshot) {
        return new ModelQualitySnapshotResponse(
                snapshot.getId(),
                snapshot.getLeague().getCode().name(),
                snapshot.getMarketDefinition().getCode().name(),
                snapshot.getMarketDefinition().getDisplayName(),
                snapshot.getModelVersion(),
                snapshot.getQualityDate(),
                snapshot.getSampleSize(),
                snapshot.getWonCount(),
                snapshot.getLostCount(),
                snapshot.getVoidCount(),
                snapshot.getObservedWinRate(),
                snapshot.getAverageRawProbability(),
                snapshot.getBrierScore(),
                snapshot.getCalibrationError(),
                snapshot.getProbabilityAdjustment(),
                snapshot.getConfidenceBand(),
                snapshot.getGeneratedAt()
        );
    }
}
