package com.betai.api.dto;

import com.betai.domain.league.LeagueCode;
import com.betai.domain.feature.SeasonSelectionMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record DailyFeatureGenerationRequest(
        @Size(max = 256) Set<LeagueCode> leagueCodes,
        LocalDate calculationDate,
        boolean forceRegenerate,
        @Min(1) Integer requestedSeasonCount,
        SeasonSelectionMode seasonSelectionMode,
        @Size(max = 16) Set<String> customSeasonIds
) {
}
