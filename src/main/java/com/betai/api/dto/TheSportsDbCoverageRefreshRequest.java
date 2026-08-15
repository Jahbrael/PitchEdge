package com.betai.api.dto;

import com.betai.domain.league.LeagueCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TheSportsDbCoverageRefreshRequest(
        @NotNull LeagueCode leagueCode,
        @Size(max = 64) String season
) {
}
