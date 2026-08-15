package com.betai.api.dto;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.feature.SeasonSelectionMode;
import com.betai.domain.match.MatchStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record PredictionGenerationRequest(
        @Size(max = 256) Set<LeagueCode> leagueCodes,
        LocalDate calculationDate,
        String featureSeasonLabel,
        LocalDate fixtureDateFrom,
        LocalDate fixtureDateTo,
        @Size(max = 6) Set<MatchStatus> matchStatuses,
        String modelVersion,
        String calibrationModelVersion,
        boolean forceRegenerate,
        @Min(1) Integer requestedSeasonCount,
        SeasonSelectionMode seasonSelectionMode,
        @Size(max = 16) Set<String> customSeasonIds
) {
}
