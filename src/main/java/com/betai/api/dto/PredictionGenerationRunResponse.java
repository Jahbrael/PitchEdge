package com.betai.api.dto;

import com.betai.domain.prediction.PredictionGenerationRun;
import com.betai.domain.prediction.PredictionGenerationStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PredictionGenerationRunResponse(
        UUID predictionGenerationRunId,
        String leagueCode,
        String modelVersion,
        String featureSeasonLabel,
        LocalDate calculationDate,
        LocalDate fixtureDateFrom,
        LocalDate fixtureDateTo,
        String matchStatuses,
        PredictionGenerationStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        int matchesEvaluated,
        int selectionsGenerated,
        int selectionsSkipped,
        String failureReason,
        boolean cacheReused
) {
    public static PredictionGenerationRunResponse from(PredictionGenerationRun run, boolean cacheReused) {
        return new PredictionGenerationRunResponse(
                run.getId(),
                run.getLeague().getCode().name(),
                run.getModelVersion(),
                run.getFeatureSeasonLabel(),
                run.getCalculationDate(),
                run.getFixtureDateFrom(),
                run.getFixtureDateTo(),
                run.getMatchStatuses(),
                run.getGenerationStatus(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                run.getMatchesEvaluated(),
                run.getSelectionsGenerated(),
                run.getSelectionsSkipped(),
                run.getFailureReason(),
                cacheReused
        );
    }
}
