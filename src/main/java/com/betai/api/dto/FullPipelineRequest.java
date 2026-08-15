package com.betai.api.dto;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.feature.SeasonSelectionMode;
import com.betai.domain.match.MatchStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record FullPipelineRequest(
        @Size(max = 256) Set<LeagueCode> leagueCodes,
        LocalDate pipelineDate,
        LocalDate fixtureDateFrom,
        LocalDate fixtureDateTo,
        LocalDate settlementMatchDateFrom,
        LocalDate settlementMatchDateTo,
        LocalDate backtestMatchDateFrom,
        LocalDate backtestMatchDateTo,
        @Size(max = 6) Set<MatchStatus> predictionMatchStatuses,
        String modelVersion,
        Boolean runFixtureDiscovery,
        Boolean fixtureDiscoveryAutoRegisterFootballDataSources,
        Boolean fixtureDiscoveryGeneratePendingSlate,
        String fixtureDiscoveryTargetSeasonLabel,
        Boolean runRefresh,
        Boolean runExtraction,
        Boolean runOddsExtraction,
        Boolean runFeatures,
        Boolean runHistoricalPredictions,
        Boolean runPredictions,
        Boolean runSettlement,
        Boolean runModelQuality,
        Boolean runBacktest,
        @Min(0) Integer backtestMinimumSampleSize,
        Boolean forceRefresh,
        Boolean forceReprocess,
        Boolean forceRegenerateFeatures,
        Boolean forceRegeneratePredictions,
        Boolean forceResettle,
        @Min(1) Integer requestedSeasonCount,
        SeasonSelectionMode seasonSelectionMode,
        @Size(max = 16) Set<String> customSeasonIds
) {
}
