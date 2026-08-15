package com.betai.api.dto;

import com.betai.domain.league.LeagueCode;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record FixtureDiscoveryRequest(
        @Size(max = 256) Set<LeagueCode> leagueCodes,
        @Pattern(regexp = "\\d{4}/\\d{4}", message = "targetSeasonLabel must use yyyy/yyyy format")
        @Size(max = 32) String targetSeasonLabel,
        LocalDate discoveryDate,
        LocalDate fixtureDateFrom,
        LocalDate fixtureDateTo,
        Boolean forceRefresh,
        Boolean forceReprocess,
        Boolean autoRegisterFootballDataSources,
        Boolean generatePendingSlate,
        @Size(max = 80) String modelVersion,
        Boolean forceRegeneratePredictions
) {
}
