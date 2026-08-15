package com.betai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpcomingFixtureImportItem(
        @NotBlank @Size(max = 160) String homeTeam,
        @NotBlank @Size(max = 160) String awayTeam,
        @NotNull LocalDate matchDate,
        @NotNull LocalTime kickoffTime,
        @Size(max = 64) String roundLabel,
        @Size(max = 160) String venue,
        @Size(max = 180) String sourceFixtureKey
) {
}
