package com.betai.api.dto;

import com.betai.domain.league.LeagueCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record ModelQualityGenerationRequest(
        @Size(max = 256) Set<LeagueCode> leagueCodes,
        String modelVersion,
        LocalDate qualityDate,
        @Min(1) Integer minimumSampleSize
) {
}
