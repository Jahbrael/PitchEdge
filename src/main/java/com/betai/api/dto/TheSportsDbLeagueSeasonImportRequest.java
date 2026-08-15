package com.betai.api.dto;

import com.betai.domain.league.LeagueCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TheSportsDbLeagueSeasonImportRequest(
        @NotNull LeagueCode leagueCode,
        @NotBlank @Size(max = 160) String externalLeagueId,
        @Size(max = 64) String season
) {
}
