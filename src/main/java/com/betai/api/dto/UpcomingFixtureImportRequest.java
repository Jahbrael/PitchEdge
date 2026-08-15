package com.betai.api.dto;

import com.betai.domain.league.LeagueCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpcomingFixtureImportRequest(
        @NotNull LeagueCode leagueCode,
        @NotNull @Size(max = 32) String seasonLabel,
        @NotEmpty @Size(max = 100) List<@Valid UpcomingFixtureImportItem> fixtures
) {
}
