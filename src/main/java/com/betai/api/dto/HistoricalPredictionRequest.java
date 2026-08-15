package com.betai.api.dto;

import com.betai.domain.feature.SeasonSelectionMode;
import com.betai.domain.league.LeagueCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record HistoricalPredictionRequest(
        @Size(max = 256) Set<LeagueCode> leagueCodes,
        @NotNull LocalDate calculationDate,
        String featureSeasonLabel,
        @NotNull LocalDate matchDateFrom,
        @NotNull LocalDate matchDateTo,
        String baseModelVersion,
        String historicalModelVersion,
        Boolean forceRegenerateFeatures,
        Boolean forceRegeneratePredictions,
        Boolean settleAfterGeneration,
        @Min(1) Integer requestedSeasonCount,
        SeasonSelectionMode seasonSelectionMode,
        @Size(max = 16) Set<String> customSeasonIds
) {
}
