package com.betai.api.dto;

import com.betai.domain.feature.FeatureGenerationRun;
import com.betai.domain.feature.FeatureGenerationStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FeatureGenerationRunResponse(
        UUID featureGenerationRunId,
        String leagueCode,
        LocalDate calculationDate,
        String seasonLabel,
        FeatureGenerationStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        int matchesSampled,
        int teamFeaturesGenerated,
        int leagueBaselinesGenerated,
        String failureReason,
        boolean cacheReused
) {
    public static FeatureGenerationRunResponse from(FeatureGenerationRun run, boolean cacheReused) {
        return new FeatureGenerationRunResponse(
                run.getId(),
                run.getLeague().getCode().name(),
                run.getCalculationDate(),
                run.getSeasonLabel(),
                run.getFeatureStatus(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs(),
                run.getMatchesSampled(),
                run.getTeamFeaturesGenerated(),
                run.getLeagueBaselinesGenerated(),
                run.getFailureReason(),
                cacheReused
        );
    }
}
